package com.kanbanboard.mobile.feature.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kanbanboard.mobile.core.data.NotificationRepository
import com.kanbanboard.mobile.core.data.SessionRepository
import com.kanbanboard.mobile.core.data.TeamRepository
import com.kanbanboard.mobile.core.data.settings.AppSettings
import com.kanbanboard.mobile.core.data.settings.BottomBarStyle
import com.kanbanboard.mobile.core.data.settings.SettingsRepository
import com.kanbanboard.mobile.core.data.settings.ThemeMode
import com.kanbanboard.mobile.core.files.FileStore
import com.kanbanboard.mobile.core.model.AppError
import com.kanbanboard.mobile.core.model.AppNotification
import com.kanbanboard.mobile.core.model.NewAccount
import com.kanbanboard.mobile.core.model.SchedulePrivacy
import com.kanbanboard.mobile.core.model.Session
import com.kanbanboard.mobile.core.model.StudyGroup
import com.kanbanboard.mobile.core.model.TeamMember
import com.kanbanboard.mobile.core.model.TeamRole
import com.kanbanboard.mobile.core.model.UserId
import com.kanbanboard.mobile.core.policy.TaskPolicy
import java.io.File
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val session: Session? = null,
    val teamName: String = "",
    /** Все аккаунты команды (заблокированные тоже — их видит капитан). */
    val members: List<TeamMember> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val notifications: List<AppNotification> = emptyList(),
) {
    val isCaptain: Boolean get() = session?.let { TaskPolicy.canCreateAccounts(it) } == true
    val unread: Int get() = notifications.count { !it.read }
    val activeMembers: List<TeamMember> get() = members.filter { it.user.isActive }
}

/**
 * Состояние профиля и всех его разделов: настройки аккаунта, настройки приложения,
 * уведомления и участники. Каждый экран создаёт свой экземпляр — данные общие через репозитории.
 */
class ProfileViewModel(
    private val sessions: SessionRepository,
    private val team: TeamRepository,
    private val settings: SettingsRepository,
    private val notifications: NotificationRepository,
    private val files: FileStore,
) : ViewModel() {
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    val state: StateFlow<ProfileUiState> = combine(
        sessions.session,
        team.team,
        team.members,
        settings.settings,
        notifications.notifications,
    ) { session, info, members, prefs, items ->
        ProfileUiState(session, info.name, members, prefs, items)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    private fun report(result: Result<*>, success: String? = null) {
        result
            .onSuccess { success?.let { _messages.trySend(it) } }
            .onFailure { _messages.trySend(it.message ?: "Не удалось сохранить") }
    }

    // ---------- Настройки аккаунта ----------

    fun setGroup(group: StudyGroup) = viewModelScope.launch { report(team.updateMyStudyGroup(group)) }

    fun setPrivacy(privacy: SchedulePrivacy) = viewModelScope.launch { report(team.updateMySchedulePrivacy(privacy)) }

    /** Фото профиля копируется в хранилище приложения; старое удаляется. */
    fun setPhoto(uri: Uri) = viewModelScope.launch {
        val previous = state.value.session?.user?.photo
        try {
            val photoLimit = if (com.kanbanboard.mobile.BuildConfig.BACKEND_BASE_URL.isBlank()) 15L else 5L
            val picked = files.import(uri, maxBytes = photoLimit * 1024 * 1024)
            val path = files.file(picked.fileRef)?.path ?: return@launch
            team.updateMyPhoto(path)
                .onSuccess { previous?.let(::deleteLocal) }
                .onFailure { _messages.trySend(it.message ?: "Не удалось сохранить фото") }
        } catch (e: AppError) {
            _messages.trySend(e.message ?: "Не удалось загрузить фото")
        }
    }

    fun removePhoto() = viewModelScope.launch {
        val previous = state.value.session?.user?.photo ?: return@launch
        team.updateMyPhoto(null).onSuccess { deleteLocal(previous) }
    }

    private fun deleteLocal(path: String) {
        File(path).parentFile?.takeIf { it.name.length == 36 }?.deleteRecursively()
    }

    // ---------- Настройки приложения ----------

    fun setTheme(theme: ThemeMode) = viewModelScope.launch { settings.setTheme(theme) }
    fun setBottomBar(style: BottomBarStyle) = viewModelScope.launch { settings.setBottomBar(style) }
    fun setTabAnimation(enabled: Boolean) = viewModelScope.launch { settings.setTabAnimation(enabled) }

    fun setPush(tasks: Boolean, events: Boolean) = viewModelScope.launch { settings.setPush(tasks, events) }

    /** Обои хранятся только на устройстве. */
    fun setWallpaper(uri: Uri) = viewModelScope.launch {
        val previous = state.value.settings.wallpaper
        try {
            val picked = files.import(uri, maxBytes = 20L * 1024 * 1024)
            val path = files.file(picked.fileRef)?.path ?: return@launch
            settings.setWallpaper(path)
            previous?.let(::deleteLocal)
        } catch (e: AppError) {
            _messages.trySend(e.message ?: "Не удалось загрузить изображение")
        }
    }

    fun removeWallpaper() = viewModelScope.launch {
        val previous = state.value.settings.wallpaper ?: return@launch
        settings.setWallpaper(null)
        deleteLocal(previous)
    }

    fun setSaveFolder(uri: Uri?) = viewModelScope.launch { settings.setSaveFolder(uri?.toString()) }

    // ---------- Уведомления ----------

    fun markNotificationsRead() = viewModelScope.launch { notifications.markAllRead() }

    // ---------- Участники (капитан) ----------

    fun createAccount(account: NewAccount, onError: (String) -> Unit, onDone: () -> Unit) {
        viewModelScope.launch {
            team.createAccount(account)
                .onSuccess {
                    onDone()
                    _messages.trySend("Аккаунт «${it.user.login}» создан. Передайте временный пароль лично")
                }
                .onFailure { onError(it.message ?: "Не удалось создать аккаунт") }
        }
    }

    fun setMemberActive(userId: UserId, active: Boolean) = viewModelScope.launch {
        report(team.setMemberActive(userId, active), if (active) "Доступ восстановлен" else "Доступ заблокирован")
    }

    /** Новый временный пароль возвращается в [onPassword], чтобы показать его капитану один раз. */
    fun resetPassword(userId: UserId, onPassword: (String) -> Unit) = viewModelScope.launch {
        team.resetPassword(userId)
            .onSuccess(onPassword)
            .onFailure { _messages.trySend(it.message ?: "Не удалось сбросить пароль") }
    }

    /** Смена своего пароля; [onError] — текст ошибки для диалога. */
    fun changePassword(current: String, new: String, onError: (String) -> Unit, onDone: () -> Unit) {
        viewModelScope.launch {
            team.changePassword(current, new)
                .onSuccess { onDone(); _messages.trySend("Пароль изменён") }
                .onFailure { onError(it.message ?: "Не удалось сменить пароль") }
        }
    }

    fun setMemberRole(userId: UserId, role: TeamRole) = viewModelScope.launch {
        report(team.setMemberRole(userId, role), if (role == TeamRole.CAPTAIN) "Назначен капитаном" else "Роль капитана снята")
    }

    fun deleteAccount(userId: UserId, onDone: () -> Unit) = viewModelScope.launch {
        team.deleteAccount(userId)
            .onSuccess { onDone(); _messages.trySend("Аккаунт удалён") }
            .onFailure { _messages.trySend(it.message ?: "Не удалось удалить аккаунт") }
    }

    fun logout() = viewModelScope.launch { sessions.logout() }
}
