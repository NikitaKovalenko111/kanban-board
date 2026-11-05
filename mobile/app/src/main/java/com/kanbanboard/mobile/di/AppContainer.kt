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
import com.kanbanboard.mobile.core.files.FileStore
import com.kanbanboard.mobile.core.notifications.ReminderScheduler
import com.kanbanboard.mobile.core.notifications.ReminderSync
import com.kanbanboard.mobile.core.notifications.TaskPushSync
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Ручной DI: один контейнер на процесс. Здесь решается, какие реализации получают экраны.
 * Сейчас задачи, проекты, команда и события — демо-сервер, сохраняемый на устройстве; пары — парсер ИСУ.
 * Когда появится REST API, демо-репозитории заменяются сетевыми только в этом файле.
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

    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(app)
    val sessionRepository: SessionRepository = demoSessions
    val teamRepository: TeamRepository = DemoTeamRepository(backend, demoSessions, fileStore)
    val projectRepository: ProjectRepository = DemoProjectRepository(backend, demoSessions, fileStore)
    val boardRepository: BoardRepository = DemoBoardRepository(backend, demoSessions, fileStore)
    val scheduleRepository: ScheduleRepository = DemoScheduleRepository(backend, demoSessions, IsuLessonsSource(app, clock = clock, example = InitialData.exampleLessons))
    val notificationRepository: NotificationRepository = DemoNotificationRepository(backend, demoSessions)

    private val reminderSync = ReminderSync(
        ReminderScheduler(app), sessionRepository, scheduleRepository, teamRepository, settingsRepository,
    )
    private val taskPush = TaskPushSync(app, notificationRepository, settingsRepository)

    /** Запускается один раз из Application: напоминания и системные уведомления о задачах. */
    fun start() {
        demoStore.autosave(backend, appScope)
        reminderSync.start(appScope)
        taskPush.start(appScope)
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer не передан") }
