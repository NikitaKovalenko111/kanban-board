package com.kanbanboard.mobile.di

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.kanbanboard.mobile.core.data.BoardRepository
import com.kanbanboard.mobile.core.data.NotificationRepository
import com.kanbanboard.mobile.core.data.ProjectRepository
import com.kanbanboard.mobile.core.data.ScheduleRepository
import com.kanbanboard.mobile.core.data.SessionRepository
import com.kanbanboard.mobile.core.data.TeamRepository
import com.kanbanboard.mobile.core.data.demo.DemoBackend
import com.kanbanboard.mobile.core.data.demo.DemoBoardRepository
import com.kanbanboard.mobile.core.data.demo.DemoNotificationRepository
import com.kanbanboard.mobile.core.data.demo.DemoProjectRepository
import com.kanbanboard.mobile.core.data.demo.DemoScheduleRepository
import com.kanbanboard.mobile.core.data.demo.DemoSessionRepository
import com.kanbanboard.mobile.core.data.demo.DemoStore
import com.kanbanboard.mobile.core.data.demo.DemoTeamRepository
import com.kanbanboard.mobile.core.data.schedule.IsuLessonsSource
import com.kanbanboard.mobile.core.data.settings.DataStoreSettingsRepository
import com.kanbanboard.mobile.core.data.settings.SettingsRepository
import com.kanbanboard.mobile.BuildConfig
import com.kanbanboard.mobile.core.data.network.AuthApi
import com.kanbanboard.mobile.core.data.network.BackendHttp
import com.kanbanboard.mobile.core.data.network.NetworkRefresh
import com.kanbanboard.mobile.core.data.network.WorkspaceApi
import com.kanbanboard.mobile.core.data.network.LiveSessionRepository
import com.kanbanboard.mobile.core.data.network.LiveProjectRepository
import com.kanbanboard.mobile.core.data.network.LiveBoardRepository
import com.kanbanboard.mobile.core.data.network.LiveTeamRepository
import com.kanbanboard.mobile.core.data.network.LiveScheduleRepository
import com.kanbanboard.mobile.core.data.network.LiveNotificationRepository
import com.kanbanboard.mobile.core.files.FileStore
import com.kanbanboard.mobile.core.notifications.ReminderScheduler
import com.kanbanboard.mobile.core.notifications.ReminderSync
import com.kanbanboard.mobile.core.notifications.TaskPushSync
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.IOException
import android.net.Uri

/**
 * Ручной DI: один контейнер на процесс. Здесь решается, какие реализации получают экраны.
 * При заданном backendBaseUrl все репозитории получают данные через REST API и JWT.
 * Без адреса работают автономные демонстрационные сборки.
 */
class AppContainer(context: Context) {
    private val app = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clock = Clock.systemDefaultZone()

    val fileStore = FileStore(app)

    // Демо-сервер сохраняется на устройстве: созданные данные и вход переживают перезапуск.
    private val demoStore = DemoStore(app.filesDir)
    // Стартовые данные зависят от версии сборки: clean — DemoSeed, mock — MockData (src/<flavor>/.../InitialData.kt).
    private val backend = DemoBackend(clock = clock, initial = demoStore.loadState() ?: InitialData.state(clock, fileStore))
    private val demoSessions = DemoSessionRepository(backend, appScope, demoStore.loadSession(), demoStore::saveSession)
    private val networkRefresh = NetworkRefresh()
    private val authApi = BuildConfig.BACKEND_BASE_URL.takeIf { it.isNotBlank() }?.let(::AuthApi)
    private val backendHttp = authApi?.let { BackendHttp(BuildConfig.BACKEND_BASE_URL, it) }
    private val workspaceApi = backendHttp?.let(::WorkspaceApi)
    private val liveSessions = if (authApi != null && workspaceApi != null)
        LiveSessionRepository(authApi, workspaceApi, fileStore, networkRefresh)
    else null

    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(app)
    val sessionRepository: SessionRepository = liveSessions ?: demoSessions
    val teamRepository: TeamRepository = if (workspaceApi != null && liveSessions != null)
        LiveTeamRepository(workspaceApi, networkRefresh, liveSessions)
    else DemoTeamRepository(backend, demoSessions, fileStore)
    val projectRepository: ProjectRepository = workspaceApi?.let { LiveProjectRepository(it, networkRefresh, fileStore) }
        ?: DemoProjectRepository(backend, demoSessions, fileStore)
    val boardRepository: BoardRepository = workspaceApi?.let { LiveBoardRepository(it, networkRefresh, fileStore) }
        ?: DemoBoardRepository(backend, demoSessions, fileStore)
    val scheduleRepository: ScheduleRepository = workspaceApi?.let { LiveScheduleRepository(it, networkRefresh) }
        ?: DemoScheduleRepository(backend, demoSessions, IsuLessonsSource(app, clock = clock, example = InitialData.exampleLessons))
    val notificationRepository: NotificationRepository = if (workspaceApi != null && liveSessions != null)
        LiveNotificationRepository(workspaceApi, networkRefresh, liveSessions)
    else DemoNotificationRepository(backend, demoSessions)

    private val reminderSync = ReminderSync(
        ReminderScheduler(app), sessionRepository, scheduleRepository, teamRepository, settingsRepository,
    )
    private val taskPush = TaskPushSync(app, notificationRepository, settingsRepository)

    /** Возвращает локальную копию серверного файла для просмотра и экспорта. */
    suspend fun resolveFile(fileRef: String, name: String): String? {
        fileStore.file(fileRef)?.let { return fileRef }
        val api = workspaceApi ?: return null
        val path = Uri.parse(fileRef).path.orEmpty()
        val match = Regex("^/api/v1/(?:files|users)/([0-9a-fA-F-]{36})(?:/photo)?$").matchEntire(path)
            ?: throw IOException("Некорректная ссылка на файл")
        val bytes = if (path.contains("/users/")) api.downloadUserPhoto(match.groupValues[1])
            else api.downloadFile(match.groupValues[1])
        return fileStore.cacheRemote(name, bytes)
    }

    /** Запускается один раз из Application: напоминания и системные уведомления о задачах. */
    fun start() {
        if (workspaceApi == null) demoStore.autosave(backend, appScope)
        reminderSync.start(appScope)
        taskPush.start(appScope)
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer не передан") }
