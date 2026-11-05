package com.kanbanboard.mobile.core.data.network

import com.kanbanboard.mobile.core.data.ProjectRepository
import com.kanbanboard.mobile.core.data.BoardRepository
import com.kanbanboard.mobile.core.data.SessionRepository
import com.kanbanboard.mobile.core.data.TeamRepository
import com.kanbanboard.mobile.core.data.ScheduleRepository
import com.kanbanboard.mobile.core.data.NotificationRepository
import com.kanbanboard.mobile.core.model.Project
import com.kanbanboard.mobile.core.model.ProjectDraft
import com.kanbanboard.mobile.core.model.ProjectId
import com.kanbanboard.mobile.core.model.Session
import com.kanbanboard.mobile.core.model.TeamRole
import com.kanbanboard.mobile.core.model.PickedFile
import com.kanbanboard.mobile.core.model.AttachmentKind
import com.kanbanboard.mobile.core.model.BoardColumn
import com.kanbanboard.mobile.core.model.ColumnDraft
import com.kanbanboard.mobile.core.model.ColumnId
import com.kanbanboard.mobile.core.model.Task
import com.kanbanboard.mobile.core.model.TaskDraft
import com.kanbanboard.mobile.core.model.TaskId
import com.kanbanboard.mobile.core.model.UserId
import com.kanbanboard.mobile.core.model.NewAccount
import com.kanbanboard.mobile.core.model.SchedulePrivacy
import com.kanbanboard.mobile.core.model.StudyGroup
import com.kanbanboard.mobile.core.model.Team
import com.kanbanboard.mobile.core.model.TeamMember
import com.kanbanboard.mobile.core.model.EventDraft
import com.kanbanboard.mobile.core.model.EventId
import com.kanbanboard.mobile.core.model.EventType
import com.kanbanboard.mobile.core.model.EventTypeDraft
import com.kanbanboard.mobile.core.model.EventTypeId
import com.kanbanboard.mobile.core.model.LessonsSnapshot
import com.kanbanboard.mobile.core.model.ScheduleEvent
import com.kanbanboard.mobile.core.model.AppNotification
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import com.kanbanboard.mobile.core.files.FileStore
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transformLatest

/** Общий сигнал перечитать серверные данные после мутации. */
internal class NetworkRefresh {
    private val generation = MutableStateFlow(0L)
    fun <T> data(load: suspend () -> T): Flow<T> = generation.flatMapLatest {
        flow {
            while (true) {
                try { emit(load()); break }
                catch (_: IOException) { delay(5_000) }
            }
        }
    }
    fun <T> polling(intervalMillis: Long, load: suspend () -> T): Flow<T> =
        merge(generation.mapLatest { Unit }, flow {
            while (true) { delay(intervalMillis); emit(Unit) }
        }).transformLatest { try { emit(load()) } catch (_: IOException) { /* Retry on next tick. */ } }
    fun changed() { generation.value++ }
}

internal suspend fun <T> networkResult(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancel: CancellationException) {
    throw cancel
} catch (error: Exception) {
    Result.failure(error)
}

internal class LiveSessionRepository(
    private val auth: AuthApi,
    private val api: WorkspaceApi,
    private val files: FileStore,
    private val refresh: NetworkRefresh,
) : SessionRepository {
    private val current = MutableStateFlow<Session?>(null)
    private val photos = mutableMapOf<String, Pair<String, String>>()
    override val session: StateFlow<Session?> = current.asStateFlow()

    override suspend fun login(login: String, password: String): Result<Session> = networkResult {
        val response = auth.login(login, password)
        Session(userWithPhoto(response.user), TeamRole.valueOf(response.user.role)).also {
            current.value = it
            refresh.changed()
        }
    }

    override suspend fun logout() {
        try { auth.logout() } catch (_: IOException) { /* Локальный выход важнее доступности сервера. */ } finally {
            current.value = null
            refresh.changed()
        }
    }

    internal suspend fun updateUser(dto: UserDto) {
        current.value = Session(userWithPhoto(dto), TeamRole.valueOf(dto.role))
        refresh.changed()
    }

    private suspend fun userWithPhoto(dto: UserDto): com.kanbanboard.mobile.core.model.User {
        val user = NetworkModelMapper.user(dto)
        val url = dto.imageUrl ?: return user.copy(photo = null)
        val cached = photos[dto.id]?.takeIf { it.first == url && java.io.File(it.second).isFile }
        if (cached != null) return user.copy(photo = cached.second)
        return try {
            val bytes = api.downloadUserPhoto(dto.id)
            val ref = files.cacheRemote("avatar-${dto.id}.jpg", bytes)
            val path = files.file(ref)?.path ?: return user
            photos[dto.id] = url to path
            user.copy(photo = path)
        } catch (_: IOException) {
            user.copy(photo = null)
        }
    }
}

internal class LiveProjectRepository(
    private val api: WorkspaceApi,
    private val refresh: NetworkRefresh,
    private val files: FileStore,
) : ProjectRepository {
    override val projects: Flow<List<Project>> = refresh.data {
        api.projects().map(NetworkModelMapper::project)
    }
    override fun project(id: ProjectId): Flow<Project?> = refresh.data {
        try { NetworkModelMapper.project(api.project(id.value)) }
        catch (e: IOException) { if (e.message?.contains("404") == true) null else throw e }
    }

    override suspend fun createProject(draft: ProjectDraft): Result<Project> = networkResult {
        NetworkModelMapper.project(api.createProject(draft.toRequest())).also { refresh.changed() }
    }

    override suspend fun updateProject(id: ProjectId, draft: ProjectDraft): Result<Unit> = networkResult {
        api.updateProject(id.value, draft.toRequest())
        refresh.changed()
    }

    override suspend fun addLink(id: ProjectId, title: String, url: String): Result<Unit> = networkResult {
        api.addProjectLink(id.value, title, url)
        refresh.changed()
    }

    override suspend fun addFile(id: ProjectId, file: PickedFile): Result<Unit> = networkResult {
        val local = files.file(file.fileRef) ?: throw IOException("Файл не найден на устройстве")
        api.addProjectFile(id.value, local, file.name, FileStore.mimeType(file.name))
        refresh.changed()
    }

    override suspend fun removeSource(id: ProjectId, sourceId: String): Result<Unit> = networkResult {
        api.removeProjectSource(id.value, sourceId)
        refresh.changed()
    }

    override suspend fun deleteProject(id: ProjectId): Result<Unit> = networkResult {
        api.deleteProject(id.value)
        refresh.changed()
    }

    private fun ProjectDraft.toRequest() = ProjectRequestDto(
        name, description, members.map { (userId, role) -> ProjectMemberDto(userId.value, role) },
    )
}

internal class LiveBoardRepository(
    private val api: WorkspaceApi,
    private val refresh: NetworkRefresh,
    private val files: FileStore,
) : BoardRepository {
    override fun columns(projectId: ProjectId): Flow<List<BoardColumn>> = refresh.data {
        api.columns(projectId.value).map(NetworkModelMapper::column)
    }
    override fun tasks(projectId: ProjectId): Flow<List<Task>> = refresh.data {
        api.tasks(projectId.value).map(NetworkModelMapper::task)
    }
    override fun task(taskId: TaskId): Flow<Task?> = refresh.data {
        try { NetworkModelMapper.task(api.task(taskId.value)) }
        catch (e: IOException) { if (e.message?.contains("404") == true) null else throw e }
    }
    override fun assignedTasks(userId: UserId): Flow<List<Task>> = refresh.data {
        api.assignedTasks(userId.value).map(NetworkModelMapper::task)
    }
    override fun completedTasks(): Flow<List<Task>> = refresh.data {
        api.completedTasks().map(NetworkModelMapper::task)
    }

    override suspend fun createTask(projectId: ProjectId, draft: TaskDraft): Result<Task> = networkResult {
        NetworkModelMapper.task(api.createTask(projectId.value, draft.toCreate())).also { refresh.changed() }
    }
    override suspend fun updateTask(taskId: TaskId, draft: TaskDraft): Result<Task> = networkResult {
        NetworkModelMapper.task(api.updateTask(taskId.value, draft.toPatch())).also { refresh.changed() }
    }
    override suspend fun deleteTask(taskId: TaskId): Result<Unit> = networkResult {
        api.deleteTask(taskId.value); refresh.changed()
    }
    override suspend fun takeTask(taskId: TaskId): Result<Unit> = networkResult {
        api.takeTask(taskId.value); refresh.changed()
    }
    override suspend fun moveTask(taskId: TaskId, columnId: ColumnId, comment: String?): Result<Unit> = networkResult {
        api.moveTask(taskId.value, columnId.value, comment); refresh.changed()
    }
    override suspend fun addComment(taskId: TaskId, text: String): Result<Unit> = networkResult {
        api.addComment(taskId.value, text); refresh.changed()
    }
    override suspend fun addAttachment(taskId: TaskId, file: PickedFile, kind: AttachmentKind): Result<Unit> = networkResult {
        val local = files.file(file.fileRef) ?: throw IOException("Файл не найден на устройстве")
        api.addAttachment(taskId.value, local, file.name, FileStore.mimeType(file.name), kind.name)
        refresh.changed()
    }
    override suspend fun removeAttachment(taskId: TaskId, attachmentId: String): Result<Unit> = networkResult {
        api.removeAttachment(taskId.value, attachmentId); refresh.changed()
    }
    override suspend fun assignToSelf(taskId: TaskId): Result<Unit> = networkResult {
        api.assignToSelf(taskId.value); refresh.changed()
    }
    override suspend fun saveColumns(projectId: ProjectId, columns: List<ColumnDraft>): Result<Unit> = networkResult {
        api.saveColumns(projectId.value, columns.map { ColumnInputDto(it.id?.value, it.title, NetworkModelMapper.hexColor(it.color)) })
        refresh.changed()
    }
    private fun TaskDraft.toCreate() = TaskRequestDto(
        title, description, priority.name, assigneeId?.value, dueAt?.toString(), columnId?.value,
    )
    private fun TaskDraft.toPatch() = TaskPatchDto(
        title, description, priority.name, assigneeId?.value, dueAt?.toString(),
    )
}

internal class LiveTeamRepository(
    private val api: WorkspaceApi,
    private val refresh: NetworkRefresh,
    private val sessions: LiveSessionRepository,
) : TeamRepository {
    override val team: Flow<Team> = refresh.data { api.team().let { Team(it.name, it.description) } }
    override val members: Flow<List<TeamMember>> = refresh.data {
        api.users().map(NetworkModelMapper::member)
    }
    override suspend fun updateMyStudyGroup(group: StudyGroup): Result<Unit> = networkResult {
        sessions.updateUser(api.updateProfile(ProfilePatchDto(studentGroup = group.code)))
    }
    override suspend fun updateMySchedulePrivacy(privacy: SchedulePrivacy): Result<Unit> = networkResult {
        sessions.updateUser(api.updateProfile(ProfilePatchDto(schedulePrivacy = privacy.name)))
    }
    override suspend fun updateMyPhoto(photo: String?): Result<Unit> = networkResult {
        val user = if (photo == null) api.deleteProfilePhoto() else {
            val file = java.io.File(photo).takeIf { it.isFile } ?: throw IOException("Фото не найдено на устройстве")
            api.uploadProfilePhoto(file, file.name, FileStore.mimeType(file.name))
        }
        sessions.updateUser(user)
    }
    override suspend fun createAccount(account: NewAccount): Result<TeamMember> = networkResult {
        val created = api.createUser(CreateUserDto(
            account.fullName, account.login, account.temporaryPassword,
            account.studyGroup.code, account.role.name,
        ))
        refresh.changed()
        NetworkModelMapper.member(created)
    }
    override suspend fun setMemberActive(userId: UserId, active: Boolean): Result<Unit> = networkResult {
        api.setUserActive(userId.value, active); refresh.changed()
    }
    override suspend fun resetPassword(userId: UserId): Result<String> = networkResult {
        api.resetPassword(userId.value)
    }
    override suspend fun changePassword(current: String, new: String): Result<Unit> = networkResult {
        api.changePassword(current, new)
    }
    override suspend fun setMemberRole(userId: UserId, role: TeamRole): Result<Unit> = networkResult {
        api.setUserRole(userId.value, role.name); refresh.changed()
    }
    override suspend fun deleteAccount(userId: UserId): Result<Unit> = networkResult {
        api.deleteUser(userId.value); refresh.changed()
    }
}

internal class LiveScheduleRepository(
    private val api: WorkspaceApi,
    private val refresh: NetworkRefresh,
) : ScheduleRepository {
    override fun lessons(group: StudyGroup, from: LocalDate, to: LocalDate): Flow<LessonsSnapshot> = refresh.data {
        NetworkModelMapper.schedule(api.schedule(group.code, from.toString(), to.toString()))
    }
    override fun events(personId: UserId, groups: Set<StudyGroup>, from: Instant, to: Instant): Flow<List<ScheduleEvent>> =
        refresh.data {
            api.events(personId.value, groups.map { it.code }, from.atZone(ZoneOffset.UTC).toLocalDate().toString(),
                to.atZone(ZoneOffset.UTC).toLocalDate().toString()).map(NetworkModelMapper::event)
        }
    override fun event(id: EventId): Flow<ScheduleEvent?> = refresh.data {
        try { NetworkModelMapper.event(api.event(id.value)) }
        catch (e: IOException) { if (e.message?.contains("404") == true) null else throw e }
    }
    override val eventTypes: Flow<List<EventType>> = refresh.data {
        api.eventTypes().map(NetworkModelMapper::eventType)
    }
    override val myReminders: Flow<Map<String, Set<Int>>> = refresh.data { api.reminders() }
    override suspend fun createEvent(draft: EventDraft): Result<ScheduleEvent> = networkResult {
        NetworkModelMapper.event(api.createEvent(draft.toCreate())).also { refresh.changed() }
    }
    override suspend fun updateEvent(id: EventId, draft: EventDraft): Result<Unit> = networkResult {
        api.updateEvent(id.value, draft.toPatch()); refresh.changed()
    }
    override suspend fun deleteEvent(id: EventId): Result<Unit> = networkResult {
        api.deleteEvent(id.value); refresh.changed()
    }
    override suspend fun saveEventType(id: EventTypeId?, draft: EventTypeDraft): Result<EventType> = networkResult {
        val request = EventTypeRequestDto(draft.title, NetworkModelMapper.hexColor(draft.color), draft.defaultReminders)
        val result = if (id == null) api.createEventType(request) else api.updateEventType(id.value, request)
        NetworkModelMapper.eventType(result).also { refresh.changed() }
    }
    override suspend fun deleteEventType(id: EventTypeId): Result<Unit> = networkResult {
        api.deleteEventType(id.value); refresh.changed()
    }
    override suspend fun setMyReminders(itemKey: String, minutes: Set<Int>): Result<Unit> = networkResult {
        api.setReminders(itemKey, minutes); refresh.changed()
    }
    private fun EventDraft.toCreate() = EventRequestDto(
        title, description, location, startsAt.toString(), endsAt?.toString(),
        visibility.name, group?.code, typeId.value, myReminders,
    )
    private fun EventDraft.toPatch() = EventPatchDto(
        title, description, location, startsAt.toString(), endsAt?.toString(),
        visibility.name, group?.code, typeId.value,
    )
}

internal class LiveNotificationRepository(
    private val api: WorkspaceApi,
    private val refresh: NetworkRefresh,
    private val sessions: LiveSessionRepository,
) : NotificationRepository {
    override val notifications: Flow<List<AppNotification>> = sessions.session.flatMapLatest { session ->
        if (session == null) flowOf(emptyList()) else refresh.polling(30_000) {
            api.notifications().map(NetworkModelMapper::notification)
        }
    }
    override suspend fun markAllRead() {
        api.markNotificationsRead()
        refresh.changed()
    }
}
