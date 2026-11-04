package com.kanbanboard.mobile.core.data.demo

import com.kanbanboard.mobile.core.data.BoardRepository
import com.kanbanboard.mobile.core.data.NotificationRepository
import com.kanbanboard.mobile.core.data.ProjectRepository
import com.kanbanboard.mobile.core.data.ScheduleRepository
import com.kanbanboard.mobile.core.data.SessionRepository
import com.kanbanboard.mobile.core.data.TeamRepository
import com.kanbanboard.mobile.core.data.schedule.LessonsSource
import com.kanbanboard.mobile.core.files.FileStore
import com.kanbanboard.mobile.core.model.AppError
import com.kanbanboard.mobile.core.model.AppNotification
import com.kanbanboard.mobile.core.model.AttachmentKind
import com.kanbanboard.mobile.core.model.BoardColumn
import com.kanbanboard.mobile.core.model.ColumnDraft
import com.kanbanboard.mobile.core.model.ColumnId
import com.kanbanboard.mobile.core.model.ColumnKind
import com.kanbanboard.mobile.core.model.EventDraft
import com.kanbanboard.mobile.core.model.EventId
import com.kanbanboard.mobile.core.model.EventType
import com.kanbanboard.mobile.core.model.EventTypeDraft
import com.kanbanboard.mobile.core.model.EventTypeId
import com.kanbanboard.mobile.core.model.LessonsSnapshot
import com.kanbanboard.mobile.core.model.NewAccount
import com.kanbanboard.mobile.core.model.NotificationKind
import com.kanbanboard.mobile.core.model.PickedFile
import com.kanbanboard.mobile.core.model.Project
import com.kanbanboard.mobile.core.model.ProjectDraft
import com.kanbanboard.mobile.core.model.ProjectId
import com.kanbanboard.mobile.core.model.ScheduleEvent
import com.kanbanboard.mobile.core.model.SchedulePrivacy
import com.kanbanboard.mobile.core.model.Session
import com.kanbanboard.mobile.core.model.SourceKind
import com.kanbanboard.mobile.core.model.StudyGroup
import com.kanbanboard.mobile.core.model.Task
import com.kanbanboard.mobile.core.model.TaskDraft
import com.kanbanboard.mobile.core.model.TaskId
import com.kanbanboard.mobile.core.model.Team
import com.kanbanboard.mobile.core.model.TeamMember
import com.kanbanboard.mobile.core.model.TeamRole
import com.kanbanboard.mobile.core.model.UserId
import com.kanbanboard.mobile.core.model.appResult
import com.kanbanboard.mobile.core.time.Formats
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/*
 * Реализации репозиториев поверх DemoBackend. Каждая — тонкая обёртка: берёт текущую сессию
 * и вызывает «сервер». При подключении REST API эти классы заменяются сетевыми.
 */

/**
 * Сессия. [restoredUserId] — кто был в системе при прошлом запуске; [persist] сохраняет вход,
 * чтобы не логиниться при каждом открытии приложения.
 */
class DemoSessionRepository(
    private val backend: DemoBackend,
    scope: CoroutineScope,
    restoredUserId: UserId? = null,
    private val persist: (UserId?) -> Unit = {},
) : SessionRepository {
    private val loggedIn = MutableStateFlow(restoredUserId)

    // Сессия пересчитывается из данных: смена группы, фото или блокировка аккаунта сразу видны приложению.
    override val session: StateFlow<Session?> = combine(loggedIn, backend.state) { id, state ->
        id?.let(state::sessionFor)
    }.stateIn(scope, SharingStarted.Eagerly, null)

    override suspend fun login(login: String, password: String): Result<Session> = appResult {
        backend.login(login, password).also {
            loggedIn.value = it.user.id
            persist(it.user.id)
        }
    }

    override suspend fun logout() {
        loggedIn.value = null
        persist(null)
    }

    fun requireSession(): Session = session.value ?: throw AppError.Unauthorized()
}

class DemoTeamRepository(
    private val backend: DemoBackend,
    private val sessions: DemoSessionRepository,
    private val files: FileStore? = null,
) : TeamRepository {
    override val team: Flow<Team> = backend.state.map { it.team }.distinctUntilChanged()

    /** Все аккаунты команды, включая заблокированные: капитан должен видеть, кого можно восстановить. */
    override val members: Flow<List<TeamMember>> = backend.state
        .map { state -> state.accounts.map { it.member }.sortedWith(compareBy<TeamMember>({ it.role }, { it.user.fullName })) }
        .distinctUntilChanged()

    override suspend fun updateMyStudyGroup(group: StudyGroup): Result<Unit> =
        appResult { backend.updateStudyGroup(sessions.requireSession(), group) }

    override suspend fun updateMySchedulePrivacy(privacy: SchedulePrivacy): Result<Unit> =
        appResult { backend.updateSchedulePrivacy(sessions.requireSession(), privacy) }

    override suspend fun updateMyPhoto(photo: String?): Result<Unit> =
        appResult { backend.updatePhoto(sessions.requireSession(), photo) }

    override suspend fun createAccount(account: NewAccount): Result<TeamMember> =
        appResult { backend.createAccount(sessions.requireSession(), account) }

    override suspend fun setMemberActive(userId: UserId, active: Boolean): Result<Unit> =
        appResult { backend.setMemberActive(sessions.requireSession(), userId, active) }

    override suspend fun resetPassword(userId: UserId): Result<String> =
        appResult { backend.resetPassword(sessions.requireSession(), userId) }

    override suspend fun changePassword(current: String, new: String): Result<Unit> =
        appResult { backend.changePassword(sessions.requireSession(), current, new) }

    override suspend fun setMemberRole(userId: UserId, role: TeamRole): Result<Unit> =
        appResult { backend.setMemberRole(sessions.requireSession(), userId, role) }

    override suspend fun deleteAccount(userId: UserId): Result<Unit> = appResult {
        val photo = backend.state.value.user(userId)?.photo
        backend.deleteAccount(sessions.requireSession(), userId)
        photo?.let { files?.deleteByPath(it) }
    }
}

class DemoProjectRepository(
    private val backend: DemoBackend,
    private val sessions: DemoSessionRepository,
    private val files: FileStore? = null,
) : ProjectRepository {
    override val projects: Flow<List<Project>> = backend.state.map { it.projects }.distinctUntilChanged()

    override fun project(id: ProjectId): Flow<Project?> =
        backend.state.map { state -> state.projects.firstOrNull { it.id == id } }.distinctUntilChanged()

    override suspend fun createProject(draft: ProjectDraft): Result<Project> =
        appResult { backend.createProject(sessions.requireSession(), draft) }

    override suspend fun updateProject(id: ProjectId, draft: ProjectDraft): Result<Unit> =
        appResult { backend.updateProject(sessions.requireSession(), id, draft) }

    override suspend fun addLink(id: ProjectId, title: String, url: String): Result<Unit> = appResult {
        val actor = sessions.requireSession()
        backend.addSource(actor, id, backend.newSource(actor, SourceKind.LINK, title.ifBlank { url }, url, null, null))
    }

    override suspend fun addFile(id: ProjectId, file: PickedFile): Result<Unit> = appResult {
        val actor = sessions.requireSession()
        backend.addSource(actor, id, backend.newSource(actor, SourceKind.FILE, file.name, null, file.sizeBytes, file.fileRef))
    }

    override suspend fun removeSource(id: ProjectId, sourceId: String): Result<Unit> = appResult {
        backend.removeSource(sessions.requireSession(), id, sourceId)?.let { files?.delete(it) }
    }

    override suspend fun deleteProject(id: ProjectId): Result<Unit> = appResult {
        backend.deleteProject(sessions.requireSession(), id).forEach { files?.delete(it) }
    }
}

class DemoBoardRepository(
    private val backend: DemoBackend,
    private val sessions: DemoSessionRepository,
    private val files: FileStore? = null,
) : BoardRepository {
    override fun columns(projectId: ProjectId): Flow<List<BoardColumn>> =
        backend.state.map { it.columnsOf(projectId) }.distinctUntilChanged()

    override fun tasks(projectId: ProjectId): Flow<List<Task>> =
        backend.state.map { state -> state.tasks.filter { it.projectId == projectId } }.distinctUntilChanged()

    override fun task(taskId: TaskId): Flow<Task?> =
        backend.state.map { state -> state.tasks.firstOrNull { it.id == taskId } }.distinctUntilChanged()

    override fun completedTasks(): Flow<List<Task>> = backend.state.map { state ->
        val doneColumns = state.columns.filter { it.kind == ColumnKind.DONE }.map { it.id }.toSet()
        state.tasks.filter { it.columnId in doneColumns }
    }.distinctUntilChanged()

    override fun assignedTasks(userId: UserId): Flow<List<Task>> = backend.state.map { state ->
        val doneColumns = state.columns.filter { it.kind == ColumnKind.DONE }.map { it.id }.toSet()
        state.tasks.filter { it.assigneeId == userId && it.columnId !in doneColumns }
    }.distinctUntilChanged()

    private fun actor() = sessions.requireSession()

    override suspend fun createTask(projectId: ProjectId, draft: TaskDraft): Result<Task> =
        appResult { backend.createTask(actor(), projectId, draft) }

    override suspend fun updateTask(taskId: TaskId, draft: TaskDraft): Result<Task> =
        appResult { backend.updateTask(actor(), taskId, draft) }

    override suspend fun deleteTask(taskId: TaskId): Result<Unit> = appResult {
        backend.deleteTask(actor(), taskId).forEach { files?.delete(it) }
    }

    override suspend fun takeTask(taskId: TaskId): Result<Unit> =
        appResult { backend.takeTask(actor(), taskId) }

    override suspend fun moveTask(taskId: TaskId, columnId: ColumnId, comment: String?): Result<Unit> =
        appResult { backend.moveTask(actor(), taskId, columnId, comment) }

    override suspend fun addComment(taskId: TaskId, text: String): Result<Unit> =
        appResult { backend.addComment(actor(), taskId, text) }

    override suspend fun addAttachment(taskId: TaskId, file: PickedFile, kind: AttachmentKind): Result<Unit> =
        appResult { backend.addAttachment(actor(), taskId, file, kind) }

    override suspend fun removeAttachment(taskId: TaskId, attachmentId: String): Result<Unit> = appResult {
        backend.removeAttachment(actor(), taskId, attachmentId)?.let { files?.delete(it) }
    }

    override suspend fun assignToSelf(taskId: TaskId): Result<Unit> =
        appResult { backend.assignToSelf(actor(), taskId) }

    override suspend fun saveColumns(projectId: ProjectId, columns: List<ColumnDraft>): Result<Unit> =
        appResult { backend.saveColumns(actor(), projectId, columns) }
}

/** События, типы и напоминания — в демо-сервере; пары — из [LessonsSource] (парсер ИСУ с кэшем). */
class DemoScheduleRepository(
    private val backend: DemoBackend,
    private val sessions: DemoSessionRepository,
    private val lessonsSource: LessonsSource,
) : ScheduleRepository {

    override fun lessons(group: StudyGroup, from: LocalDate, to: LocalDate): Flow<LessonsSnapshot> =
        lessonsSource.lessons(group, from, to)

    override fun events(personId: UserId, groups: Set<StudyGroup>, from: Instant, to: Instant): Flow<List<ScheduleEvent>> =
        combine(backend.state, sessions.session) { state, session ->
            val viewer = session?.user?.id ?: return@combine emptyList<ScheduleEvent>()
            state.eventsFor(viewer, personId, groups).filter { event ->
                val end = event.endsAt ?: event.startsAt
                !end.isBefore(from) && event.startsAt.isBefore(to)
            }.sortedBy { it.startsAt }
        }.distinctUntilChanged()

    override fun event(id: EventId): Flow<ScheduleEvent?> =
        combine(backend.state, sessions.session) { state, session ->
            val viewer = session?.user?.id ?: return@combine null
            state.events.firstOrNull { it.id == id }?.let { state.presentFor(viewer, it) }
        }.distinctUntilChanged()

    override val eventTypes: Flow<List<EventType>> = backend.state.map { it.eventTypes }.distinctUntilChanged()

    override val myReminders: Flow<Map<String, Set<Int>>> =
        combine(backend.state, sessions.session) { state, session ->
            session?.let { state.remindersOf(it.user.id) } ?: emptyMap()
        }.distinctUntilChanged()

    override suspend fun createEvent(draft: EventDraft): Result<ScheduleEvent> =
        appResult { backend.createEvent(sessions.requireSession(), draft) }

    override suspend fun updateEvent(id: EventId, draft: EventDraft): Result<Unit> =
        appResult { backend.updateEvent(sessions.requireSession(), id, draft) }

    override suspend fun deleteEvent(id: EventId): Result<Unit> =
        appResult { backend.deleteEvent(sessions.requireSession(), id) }

    override suspend fun saveEventType(id: EventTypeId?, draft: EventTypeDraft): Result<EventType> =
        appResult { backend.saveEventType(sessions.requireSession(), id, draft) }

    override suspend fun deleteEventType(id: EventTypeId): Result<Unit> =
        appResult { backend.deleteEventType(sessions.requireSession(), id) }

    override suspend fun setMyReminders(itemKey: String, minutes: Set<Int>): Result<Unit> =
        appResult { backend.setReminders(sessions.requireSession(), itemKey, minutes) }
}

/**
 * Уведомления: о задачах — хранятся на «сервере»; «скоро событие» — вычисляются для событий
 * ближайших суток из моего расписания (командные, моей группы, мои личные).
 */
class DemoNotificationRepository(
    private val backend: DemoBackend,
    private val sessions: DemoSessionRepository,
) : NotificationRepository {

    private fun upcoming(state: DemoState, session: Session): List<AppNotification> {
        val now = backend.clock.instant()
        val horizon = now.plus(Duration.ofHours(UPCOMING_HOURS))
        val seen = state.seenEventNotifications[session.user.id].orEmpty()
        return state.eventsFor(session.user.id, session.user.id, setOf(session.user.studyGroup))
            .filter { it.startsAt.isAfter(now) && it.startsAt.isBefore(horizon) }
            .map { event ->
                AppNotification(
                    id = eventKey(event.id),
                    kind = NotificationKind.EVENT,
                    title = event.title,
                    text = "Начало ${Formats.dateTime(event.startsAt)}" +
                        event.location.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                    at = event.startsAt,
                    read = eventKey(event.id) in seen,
                    eventId = event.id.value,
                )
            }
    }

    override val notifications: Flow<List<AppNotification>> =
        combine(backend.state, sessions.session) { state, session ->
            if (session == null) return@combine emptyList<AppNotification>()
            (state.notifications[session.user.id].orEmpty() + upcoming(state, session))
                .sortedWith(compareBy<AppNotification> { it.read }.thenByDescending { it.at })
        }.distinctUntilChanged()

    override suspend fun markAllRead() {
        val session = sessions.session.value ?: return
        val eventKeys = upcoming(backend.state.value, session).map { it.id }.toSet()
        backend.markNotificationsRead(session, eventKeys)
    }

    private fun eventKey(id: EventId) = "event:${id.value}"

    private companion object {
        const val UPCOMING_HOURS = 24L
    }
}
