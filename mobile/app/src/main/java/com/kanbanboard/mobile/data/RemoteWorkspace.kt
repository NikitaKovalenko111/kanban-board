package com.kanbanboard.mobile.data

import com.kanbanboard.mobile.model.*
import java.time.LocalDate
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.OutputStream

data class RemoteWorkspace(val user: User, val studentGroup: String, val teams: List<Team>,
    val members: List<User>, val projects: List<Project>, val tasks: List<Task>,
    val events: List<TeamEvent>, val eventTypes: List<EventType>, val lessons: List<Lesson>,
    val selectedTeamId: Long, val selectedProjectId: Long)

/** Все данные в этом режиме берутся с сервера; mock-данные не смешиваются с ответами API. */
class RemoteWorkspaceGateway(private val client: BackendClient) {
    private val api = client.api

    suspend fun loginAndLoad(login: String, password: String): RemoteWorkspace {
        val token = api.login(LoginRequest(login, password)).accessToken
        client.session.set(token)
        try {
            val me = api.me()
            val user = me.toModel()
            val teams = api.teams().map { it.toModel(user.id) }
            val selectedTeam = teams.firstOrNull { user.id in it.memberIds }
            val projects = api.projects().map { it.toModel(user.id) }
            val selectedProject = projects.firstOrNull { it.teamId == selectedTeam?.id && user.id in it.memberIds }
                ?: projects.firstOrNull { it.teamId == selectedTeam?.id }
            val members = selectedTeam?.let { api.members(it.id).map(UserDto::toModel) }.orEmpty()
            val tasks = selectedProject?.let { api.board(it.id).tasks.map(TaskDto::toModel) }.orEmpty()
            val group = me.studentGroup ?: "203"
            val from = LocalDate.now()
            val events = selectedTeam?.let {
                api.events(it.id, group, from.minusDays(7).toString(), from.plusDays(30).toString()).map(EventDto::toModel)
            }.orEmpty()
            val types = api.eventTypes().map(EventTypeDto::toModel)
            val lessons = runCatching { api.lessons(group, from.toString(), from.plusDays(14).toString()).map(LessonDto::toModel) }
                .getOrDefault(emptyList())
            return RemoteWorkspace(user, group, teams, members, projects, tasks, events, types, lessons,
                selectedTeam?.id ?: 0, selectedProject?.id ?: 0)
        } catch (failure: Exception) {
            client.session.clear()
            throw failure
        }
    }

    suspend fun board(projectId: Long): List<Task> = api.board(projectId).tasks.map(TaskDto::toModel)
    suspend fun history(taskId: Long): List<TaskHistoryEntry> = api.history(taskId).map(TaskHistoryDto::toModel)
    suspend fun members(teamId: Long): List<User> = api.members(teamId).map(UserDto::toModel)
    suspend fun projects(userId: Long): List<Project> = api.projects().map { it.toModel(userId) }
    suspend fun joinTeam(teamId: Long, userId: Long): Team = api.joinTeam(teamId).toModel(userId)
    suspend fun leaveTeam(teamId: Long) { api.leaveTeam(teamId) }
    suspend fun updateTeam(teamId: Long, name: String, description: String, userId: Long): Team =
        api.updateTeam(teamId, UpdateTeamRequest(name, description)).toModel(userId)
    suspend fun addMember(teamId: Long, name: String, login: String, password: String,
        position: String, role: UserRole): User = api.addMember(teamId,
        CreateMemberRequest(name, login, password, position, role.name)).toModel()
    suspend fun setMemberAccess(teamId: Long, userId: Long, active: Boolean): User =
        api.setMemberAccess(teamId, userId, MemberAccessRequest(active)).toModel()
    suspend fun events(teamId: Long, group: String): List<TeamEvent> {
        val today = LocalDate.now()
        return api.events(teamId, group, today.minusDays(7).toString(), today.plusDays(30).toString()).map(EventDto::toModel)
    }
    suspend fun lessons(group: String): List<Lesson> {
        val today = LocalDate.now()
        return api.lessons(group, today.toString(), today.plusDays(14).toString()).map(LessonDto::toModel)
    }
    suspend fun moveTask(id: Long, status: TaskStatus, comment: String): Task {
        val saved = api.moveTask(id, MoveTaskRequest(status.name, comment.ifBlank { null })).toModel()
        return saved.copy(history = history(id))
    }
    suspend fun takeTask(id: Long): Task = api.takeTask(id).toModel()
    suspend fun addComment(id: Long, text: String): Comment {
        val saved = api.comment(id, CommentRequest(text))
        return Comment(saved.id, saved.authorName, saved.text, saved.createdAt)
    }
    suspend fun uploadAttachment(taskId: Long, name: String, bytes: ByteArray, result: Boolean): Attachment {
        val file = MultipartBody.Part.createFormData("file", name, bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
        val kind = MultipartBody.Part.createFormData("kind", if (result) "RESULT" else "SOURCE")
        return api.uploadAttachment(taskId, file, kind).toModel()
    }
    suspend fun downloadAttachment(id: String, output: OutputStream) {
        api.downloadAttachment(id).use { body -> body.byteStream().use { input -> input.copyTo(output) } }
    }
    suspend fun createTask(projectId: Long, title: String, description: String, assigneeId: Long?, dueAt: String?): Task =
        api.createTask(projectId, CreateTaskRequest(title, description, assigneeId, dueAt)).toModel()
    suspend fun createEvent(event: TeamEvent): TeamEvent {
        val startsAt = java.time.LocalDateTime.parse("${event.date}T${event.time}")
            .atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime().toString()
        val endsAt = java.time.LocalDateTime.parse("${event.date}T${event.endTime}")
            .atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime().toString()
        return api.createEvent(CreateEventRequest(event.title, event.description, event.location, startsAt, endsAt,
            event.visibility.name, if (event.visibility == EventVisibility.TEAM) event.teamId else null,
            event.group, event.typeId, event.reminderMinutes)).toModel()
    }
    suspend fun createEventType(title: String, color: Long, reminders: Set<Int>): EventType =
        api.createEventType(EventTypeRequest(title, color.toHexColor(), reminders)).toModel()
    suspend fun updateEventType(id: String, title: String, color: Long, reminders: Set<Int>): EventType =
        api.updateEventType(id, EventTypeRequest(title, color.toHexColor(), reminders)).toModel()
    suspend fun updateProject(id: Long, name: String, description: String, userId: Long): Project =
        api.updateProject(id, UpdateProjectRequest(name, description)).toModel(userId)
    suspend fun setProjectMember(id: Long, userId: Long, member: Boolean, currentUserId: Long): Project =
        (if (member) api.addProjectMember(id, ProjectMemberRequest(userId))
        else api.removeProjectMember(id, userId)).toModel(currentUserId)
    fun logout() = client.session.clear()
}

private fun Long.toHexColor(): String = "#%06X".format(this and 0xFFFFFF)
