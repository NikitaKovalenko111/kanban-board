package com.kanbanboard.mobile.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kanbanboard.mobile.core.model.ProjectId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class ThemeMode(val title: String) {
    SYSTEM("Системная"),
    LIGHT("Светлая"),
    DARK("Тёмная"),
    AMOLED("AMOLED"),
}

/** Вид нижней панели навигации (Профиль → Настройки приложения). */
enum class BottomBarStyle(val title: String) {
    STATIC("Статичная"),
    FLOATING("Плавающая"),
}

/**
 * Настройки этого устройства. Всё, что относится к аккаунту (группа, фото, приватность),
 * хранится на сервере в профиле пользователя, а не здесь.
 */
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val bottomBar: BottomBarStyle = BottomBarStyle.STATIC,
    /** Сдвиг экрана влево/вправо при переключении вкладок. */
    val tabAnimation: Boolean = true,
    /** Путь к своей картинке-обоям в памяти приложения; null — обычный фон. */
    val wallpaper: String? = null,
    /** Показывать системные уведомления (push) о задачах. */
    val pushTasks: Boolean = true,
    /** Показывать системные уведомления-напоминания о событиях и парах. */
    val pushEvents: Boolean = true,
    /** Папка для сохранения файлов, выбранная через SAF (content://… tree URI). */
    val saveFolder: String? = null,
    val selectedProjectId: ProjectId? = null,
)

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setTheme(theme: ThemeMode)
    suspend fun setBottomBar(style: BottomBarStyle)
    suspend fun setTabAnimation(enabled: Boolean)
    suspend fun setWallpaper(path: String?)
    suspend fun setPush(tasks: Boolean, events: Boolean)
    suspend fun setSaveFolder(uri: String?)
    suspend fun setSelectedProject(projectId: ProjectId)
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreSettingsRepository(context: Context) : SettingsRepository {
    private val store = context.applicationContext.settingsDataStore

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val bottomBar = stringPreferencesKey("bottom_bar")
        val tabAnimation = booleanPreferencesKey("tab_animation")
        val wallpaper = stringPreferencesKey("wallpaper")
        val pushTasks = booleanPreferencesKey("push_tasks")
        val pushEvents = booleanPreferencesKey("push_events")
        val saveFolder = stringPreferencesKey("save_folder")
        val project = stringPreferencesKey("selected_project")
    }

    override val settings: Flow<AppSettings> = store.data
        .map { prefs ->
            AppSettings(
                theme = ThemeMode.entries.firstOrNull { it.name == prefs[Keys.theme] } ?: ThemeMode.SYSTEM,
                bottomBar = BottomBarStyle.entries.firstOrNull { it.name == prefs[Keys.bottomBar] } ?: BottomBarStyle.STATIC,
                tabAnimation = prefs[Keys.tabAnimation] ?: true,
                wallpaper = prefs[Keys.wallpaper],
                pushTasks = prefs[Keys.pushTasks] ?: true,
                pushEvents = prefs[Keys.pushEvents] ?: true,
                saveFolder = prefs[Keys.saveFolder],
                selectedProjectId = prefs[Keys.project]?.let(::ProjectId),
            )
        }
        .distinctUntilChanged()

    override suspend fun setTheme(theme: ThemeMode) {
        store.edit { it[Keys.theme] = theme.name }
    }

    override suspend fun setBottomBar(style: BottomBarStyle) {
        store.edit { it[Keys.bottomBar] = style.name }
    }

    override suspend fun setTabAnimation(enabled: Boolean) {
        store.edit { it[Keys.tabAnimation] = enabled }
    }

    override suspend fun setWallpaper(path: String?) {
        store.edit { if (path == null) it.remove(Keys.wallpaper) else it[Keys.wallpaper] = path }
    }

    override suspend fun setPush(tasks: Boolean, events: Boolean) {
        store.edit {
            it[Keys.pushTasks] = tasks
            it[Keys.pushEvents] = events
        }
    }

    override suspend fun setSaveFolder(uri: String?) {
        store.edit { if (uri == null) it.remove(Keys.saveFolder) else it[Keys.saveFolder] = uri }
    }

    override suspend fun setSelectedProject(projectId: ProjectId) {
        store.edit { it[Keys.project] = projectId.value }
    }
}
