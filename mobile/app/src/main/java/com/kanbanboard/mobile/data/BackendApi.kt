package com.kanbanboard.mobile.data

import com.kanbanboard.mobile.model.*
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

/** Контракт Android-клиента. Поля и маршруты требуют подтверждения серверным OpenAPI. */
interface BackendApi {
    @POST("auth/login") suspend fun login(@Body request: LoginRequest): LoginResponse
    @GET("users/me") suspend fun me(): UserDto
    @GET("teams") suspend fun teams(): List<TeamDto>
    @GET("teams/{id}/members") suspend fun members(@Path("id") teamId: Long): List<UserDto>
    @POST("teams/{id}/members/me") suspend fun joinTeam(@Path("id") teamId: Long): TeamDto
    @DELETE("teams/{id}/members/me") suspend fun leaveTeam(@Path("id") teamId: Long)
    @PATCH("teams/{id}") suspend fun updateTeam(@Path("id") teamId: Long, @Body request: UpdateTeamRequest): TeamDto
    @POST("teams/{id}/members") suspend fun addMember(@Path("id") teamId: Long, @Body request: CreateMemberRequest): UserDto
    @PATCH("teams/{id}/members/{userId}") suspend fun setMemberAccess(@Path("id") teamId: Long,
        @Path("userId") userId: Long, @Body request: MemberAccessRequest): UserDto
    @GET("projects") suspend fun projects(): List<ProjectDto>
    @GET("projects/{id}/board") suspend fun board(@Path("id") projectId: Long): BoardDto
    @GET("tasks/{id}/history") suspend fun history(@Path("id") taskId: Long): List<TaskHistoryDto>
    @PATCH("tasks/{id}/status") suspend fun moveTask(@Path("id") taskId: Long, @Body request: MoveTaskRequest): TaskDto
    @POST("tasks/{id}/take") suspend fun takeTask(@Path("id") taskId: Long): TaskDto
    @POST("projects/{id}/tasks") suspend fun createTask(@Path("id") projectId: Long, @Body request: CreateTaskRequest): TaskDto
    @POST("tasks/{id}/comments") suspend fun comment(@Path("id") taskId: Long, @Body request: CommentRequest): CommentDto
    @Multipart @POST("tasks/{id}/attachments") suspend fun uploadAttachment(@Path("id") taskId: Long,
        @Part file: MultipartBody.Part, @Part kind: MultipartBody.Part): AttachmentDto
    @Streaming @GET("attachments/{id}/download") suspend fun downloadAttachment(@Path("id") attachmentId: String): ResponseBody
    @PATCH("projects/{id}") suspend fun updateProject(@Path("id") projectId: Long, @Body request: UpdateProjectRequest): ProjectDto
    @POST("projects/{id}/members") suspend fun addProjectMember(@Path("id") projectId: Long, @Body request: ProjectMemberRequest): ProjectDto
    @DELETE("projects/{id}/members/{userId}") suspend fun removeProjectMember(@Path("id") projectId: Long, @Path("userId") userId: Long): ProjectDto
    @GET("events") suspend fun events(@Query("teamId") teamId: Long, @Query("group") group: String,
        @Query("from") from: String, @Query("to") to: String): List<EventDto>
    @POST("events") suspend fun createEvent(@Body request: CreateEventRequest): EventDto
    @GET("event-types") suspend fun eventTypes(): List<EventTypeDto>
    @POST("event-types") suspend fun createEventType(@Body request: EventTypeRequest): EventTypeDto
    @PATCH("event-types/{id}") suspend fun updateEventType(@Path("id") id: String, @Body request: EventTypeRequest): EventTypeDto
    @GET("schedule/lessons") suspend fun lessons(@Query("group") group: String,
        @Query("from") from: String, @Query("to") to: String): List<LessonDto>
}

data class LoginRequest(val login: String, val password: String)
data class LoginResponse(val accessToken: String)
data class UserDto(val id: Long, val name: String, val login: String, val role: String = "MEMBER",
    val position: String = "", val active: Boolean = true, val joinedAt: String = "", val studentGroup: String? = null,
    val contact: String? = null)
data class TeamDto(val id: Long, val name: String, val description: String = "", val memberIds: Set<Long> = emptySet(),
    val isMember: Boolean? = null)
data class ProjectDto(val id: Long, val teamId: Long, val name: String, val description: String = "",
    val memberIds: Set<Long> = emptySet(), val isMember: Boolean? = null)
data class BoardDto(val projectId: Long, val tasks: List<TaskDto> = emptyList())
data class TaskDto(val id: Long, val projectId: Long, val teamId: Long, val title: String, val description: String = "",
    val status: String = "WAITING", val assigneeId: Long? = null, val assigneeName: String? = null,
    val dueAt: String? = null, val completedAt: String? = null, val history: List<TaskHistoryDto> = emptyList(),
    val attachments: List<AttachmentDto> = emptyList(), val resultAttachments: List<AttachmentDto> = emptyList())
data class AttachmentDto(val id: String, val name: String, val sizeLabel: String = "")
data class TaskHistoryDto(val id: String, val action: String, val actorId: Long? = null,
    val actorName: String = "", val occurredAt: String, val comment: String? = null)
data class MoveTaskRequest(val status: String, val comment: String? = null)
data class CreateTaskRequest(val title: String, val description: String, val assigneeId: Long?, val dueAt: String?)
data class CommentRequest(val text: String)
data class CommentDto(val id: String, val authorId: Long, val authorName: String, val text: String, val createdAt: String)
data class UpdateProjectRequest(val name: String, val description: String)
data class ProjectMemberRequest(val userId: Long)
data class UpdateTeamRequest(val name: String, val description: String)
data class CreateMemberRequest(val name: String, val login: String, val temporaryPassword: String,
    val position: String, val role: String)
data class MemberAccessRequest(val active: Boolean)
data class EventDto(val id: Long, val title: String, val description: String = "", val location: String = "",
    val startsAt: String, val endsAt: String, val visibility: String, val teamId: Long = 0,
    val group: String? = null, val ownerId: Long? = null, val typeId: String = "meeting",
    val reminderMinutes: Set<Int> = emptySet())
data class CreateEventRequest(val title: String, val description: String, val location: String,
    val startsAt: String, val endsAt: String, val visibility: String, val teamId: Long?,
    val group: String?, val typeId: String, val reminderMinutes: Set<Int>)
data class EventTypeDto(val id: String, val title: String, val color: String, val custom: Boolean = false,
    val defaultReminderMinutes: Set<Int> = emptySet())
data class EventTypeRequest(val title: String, val color: String, val defaultReminderMinutes: Set<Int>)
data class LessonDto(val date: String, val startsAt: String, val endsAt: String, val title: String,
    val room: String = "", val teacher: String = "", val kind: String)

class JwtSession {
    @Volatile private var accessToken: String? = null
    fun set(token: String) { require(token.isNotBlank()); accessToken = token }
    fun clear() { accessToken = null }
    fun current(): String? = accessToken
}

class BearerTokenInterceptor(private val session: JwtSession) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val token = session.current()
        val request = chain.request().newBuilder().apply {
            if (token != null) header("Authorization", "Bearer $token")
        }.build()
        return chain.proceed(request)
    }
}

class BackendClient private constructor(val api: BackendApi, val session: JwtSession) {
    companion object {
        fun create(baseUrl: String): BackendClient {
            require(baseUrl.startsWith("https://") && baseUrl.endsWith("/")) { "Backend URL должен использовать HTTPS" }
            val session = JwtSession()
            val http = OkHttpClient.Builder().addInterceptor(BearerTokenInterceptor(session)).build()
            val api = Retrofit.Builder().baseUrl(baseUrl).client(http).addConverterFactory(GsonConverterFactory.create())
                .build().create(BackendApi::class.java)
            return BackendClient(api, session)
        }
    }
}

fun UserDto.toModel(): User = User(id, name, login, runCatching { UserRole.valueOf(role) }.getOrDefault(UserRole.MEMBER),
    position, active, joinedAt, contact)
fun TeamDto.toModel(currentUserId: Long): Team = Team(id, name, description,
    if (isMember == true) memberIds + currentUserId else memberIds)
fun ProjectDto.toModel(currentUserId: Long): Project = Project(id, teamId, name, description,
    if (isMember == true) memberIds + currentUserId else memberIds)
fun TaskDto.toModel(): Task = Task(id, title, description,
    runCatching { TaskStatus.valueOf(status) }.getOrDefault(TaskStatus.WAITING),
    assigneeName ?: "Не назначен", dueAt?.take(10) ?: "Без срока", completedAt = completedAt,
    teamId = teamId, projectId = projectId, assigneeId = assigneeId, dueAt = dueAt,
    history = history.map { it.toModel() }, attachments = attachments.map(AttachmentDto::toModel),
    resultAttachments = resultAttachments.map(AttachmentDto::toModel))
fun AttachmentDto.toModel(): Attachment = Attachment(id, name, sizeLabel)
fun TaskHistoryDto.toModel(): TaskHistoryEntry = TaskHistoryEntry(id, action, actorId, actorName, occurredAt, comment)
fun EventDto.toModel(): TeamEvent {
    val start = java.time.OffsetDateTime.parse(startsAt)
    val end = java.time.OffsetDateTime.parse(endsAt)
    return TeamEvent(id, title, start.toLocalDate().toString(), start.toLocalTime().toString(),
        runCatching { EventVisibility.valueOf(visibility) }.getOrDefault(EventVisibility.PERSONAL),
        endTime = end.toLocalTime().toString(), description = description, group = group, typeId = typeId,
        reminderMinutes = reminderMinutes, ownerId = ownerId, teamId = teamId, location = location)
}
fun EventTypeDto.toModel(): EventType = EventType(id, title, color.removePrefix("#").toLongOrNull(16)?.let {
    if (color.length == 7) it or 0xFF000000 else it
} ?: 0xFF6576DF, custom, defaultReminderMinutes)
fun LessonDto.toModel(): Lesson = Lesson(date, "${java.time.OffsetDateTime.parse(startsAt).toLocalTime()}–${java.time.OffsetDateTime.parse(endsAt).toLocalTime()}", title, room, teacher,
    runCatching { LessonKind.valueOf(kind) }.getOrDefault(LessonKind.LECTURE))
