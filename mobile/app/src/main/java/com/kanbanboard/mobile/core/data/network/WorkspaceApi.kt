package com.kanbanboard.mobile.core.data.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.io.File

/** Типизированные чтения рабочих данных по backend/docs/openapi.yaml. */
class WorkspaceApi(
    private val http: BackendHttp,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun projects(): List<ProjectDto> = decode(http.get("projects"))
    suspend fun project(id: String): ProjectDto = decode(http.get("projects/$id"))
    suspend fun columns(projectId: String): List<ColumnDto> = decode(http.get("projects/$projectId/columns"))
    suspend fun tasks(projectId: String): List<TaskDto> = decode(http.get("projects/$projectId/tasks"))
    suspend fun task(id: String): TaskDto = decode(http.get("tasks/$id"))
    suspend fun team(): TeamDto = decode(http.get("team"))
    suspend fun me(): UserDto = decode(http.get("me"))
    suspend fun updateProfile(request: ProfilePatchDto): UserDto = decode(http.patch("me", encode(request)))
    suspend fun uploadProfilePhoto(file: File, fileName: String, mimeType: String): UserDto =
        decode(http.upload("me/photo", file, fileName, mimeType, method = "PUT"))
    suspend fun deleteProfilePhoto(): UserDto = decode(http.delete("me/photo"))
    suspend fun changePassword(currentPassword: String, newPassword: String) {
        http.post("auth/change-password", encode(ChangePasswordDto(currentPassword, newPassword)))
    }
    suspend fun users(search: String? = null): List<UserDto> = decode(http.get("users", search?.let { mapOf("search" to it) } ?: emptyMap()))
    suspend fun user(id: String): UserDto = decode(http.get("users/$id"))
    suspend fun createUser(request: CreateUserDto): UserDto = decode(http.post("users", encode(request)))
    suspend fun deleteUser(id: String) { http.delete("users/$id") }
    suspend fun setUserActive(id: String, active: Boolean): UserDto =
        decode(http.patch("users/$id/status", encode(mapOf("status" to if (active) "ACTIVE" else "BLOCKED"))))
    suspend fun setUserRole(id: String, role: String): UserDto =
        decode(http.patch("users/$id/role", encode(mapOf("role" to role))))
    suspend fun resetPassword(id: String): String =
        decode<ResetPasswordDto>(http.post("users/$id/reset-password", encode(emptyMap<String, String>()))).temporaryPassword
    suspend fun createProject(request: ProjectRequestDto): ProjectDto = decode(http.post("projects", encode(request)))
    suspend fun updateProject(id: String, request: ProjectRequestDto): ProjectDto = decode(http.patch("projects/$id", encode(request)))
    suspend fun deleteProject(id: String) { http.delete("projects/$id") }
    suspend fun addProjectLink(projectId: String, title: String, url: String): JsonElement =
        http.post("projects/$projectId/sources/links", encode(mapOf("title" to title, "url" to url)))
    suspend fun removeProjectSource(projectId: String, sourceId: String) {
        http.delete("projects/$projectId/sources/$sourceId")
    }
    suspend fun addProjectFile(projectId: String, file: File, fileName: String, mimeType: String): ProjectSourceDto =
        decode(http.upload("projects/$projectId/sources/files", file, fileName, mimeType))
    suspend fun saveColumns(projectId: String, columns: List<ColumnInputDto>): List<ColumnDto> =
        decode(http.put("projects/$projectId/columns", encode(columns)))
    suspend fun createTask(projectId: String, request: TaskRequestDto): TaskDto =
        decode(http.post("projects/$projectId/tasks", encode(request)))
    suspend fun updateTask(id: String, request: TaskPatchDto): TaskDto = decode(http.patch("tasks/$id", encode(request)))
    suspend fun deleteTask(id: String) { http.delete("tasks/$id") }
    suspend fun takeTask(id: String): TaskDto = decode(http.post("tasks/$id/take", encode(emptyMap<String, String>())))
    suspend fun moveTask(id: String, columnId: String, comment: String? = null): TaskDto =
        decode(http.post("tasks/$id/move", encode(MoveTaskDto(columnId, comment))))
    suspend fun assignToSelf(id: String): TaskDto = decode(http.post("tasks/$id/assign-to-me", encode(emptyMap<String, String>())))
    suspend fun completedTasks(query: Map<String, String> = emptyMap()): List<TaskDto> = decode(http.get("tasks/completed", query))
    suspend fun assignedTasks(userId: String): List<TaskDto> = decode(http.get("tasks/assigned", mapOf("userId" to userId)))
    suspend fun addComment(taskId: String, text: String): CommentDto =
        decode(http.post("tasks/$taskId/comments", encode(mapOf("text" to text))))
    suspend fun comments(taskId: String): List<CommentDto> = decode(http.get("tasks/$taskId/comments"))
    suspend fun addAttachment(taskId: String, file: File, fileName: String, mimeType: String, kind: String): AttachmentDto =
        decode(http.upload("tasks/$taskId/attachments", file, fileName, mimeType, mapOf("kind" to kind)))
    suspend fun removeAttachment(taskId: String, attachmentId: String) {
        http.delete("tasks/$taskId/attachments/$attachmentId")
    }
    suspend fun downloadFile(fileId: String): ByteArray = http.download("files/$fileId")
    suspend fun downloadUserPhoto(userId: String): ByteArray = http.download("users/$userId/photo")
    suspend fun studentGroups(): List<StudentGroupDto> = decode(http.get("student-groups"))
    suspend fun events(personId: String, groups: List<String>, from: String, to: String): List<EventDto> =
        decode(http.get("events", mapOf("personId" to personId, "groups" to groups.joinToString(","), "from" to from, "to" to to)))
    suspend fun event(id: String): EventDto = decode(http.get("events/$id"))
    suspend fun createEvent(request: EventRequestDto): EventDto = decode(http.post("events", encode(request)))
    suspend fun updateEvent(id: String, request: EventPatchDto): EventDto = decode(http.patch("events/$id", encode(request)))
    suspend fun deleteEvent(id: String) { http.delete("events/$id") }
    suspend fun eventTypes(): List<EventTypeDto> = decode(http.get("event-types"))
    suspend fun createEventType(request: EventTypeRequestDto): EventTypeDto = decode(http.post("event-types", encode(request)))
    suspend fun updateEventType(id: String, request: EventTypeRequestDto): EventTypeDto = decode(http.patch("event-types/$id", encode(request)))
    suspend fun deleteEventType(id: String) { http.delete("event-types/$id") }
    suspend fun reminders(): Map<String, Set<Int>> = decode(http.get("me/reminders"))
    suspend fun setReminders(itemKey: String, minutes: Set<Int>) {
        http.put("me/reminders/$itemKey", encode(RemindersRequestDto(minutes)))
    }
    suspend fun notifications(): List<NotificationDto> = decode(http.get("notifications"))
    suspend fun markNotificationsRead() { http.post("notifications/read-all", encode(emptyMap<String, String>())) }
    suspend fun registerPushToken(token: String) { http.post("me/push-tokens", encode(PushTokenDto(token))) }
    suspend fun deletePushToken(token: String) { http.delete("me/push-tokens/$token") }
    suspend fun schedule(group: String, from: String, to: String): ScheduleDto = decode(
        http.get("schedule/lessons", mapOf("group" to group, "from" to from, "to" to to))
    )

    private inline fun <reified T> decode(value: JsonElement): T = json.decodeFromJsonElement(value)
    private inline fun <reified T> encode(value: T): JsonElement = json.encodeToJsonElement(value)
}

@Serializable data class TeamDto(val name: String, val description: String)
@Serializable data class ProfilePatchDto(
    val fullName: String? = null, val studentGroup: String? = null, val schedulePrivacy: String? = null,
)
@Serializable data class ChangePasswordDto(val currentPassword: String, val newPassword: String)
@Serializable data class CreateUserDto(
    val fullName: String, val username: String, val temporaryPassword: String,
    val studentGroup: String, val role: String,
)
@Serializable data class ResetPasswordDto(val temporaryPassword: String)
@Serializable data class NotificationDto(
    val id: String, val kind: String, val title: String, val text: String, val at: String,
    val read: Boolean, val taskId: String? = null, val eventId: String? = null,
)
@Serializable data class PushTokenDto(val token: String, val platform: String = "android")
@Serializable data class ProjectRequestDto(val name: String, val description: String, val members: List<ProjectMemberDto>)
@Serializable data class ColumnInputDto(val id: String? = null, val title: String, val color: String)
@Serializable data class TaskRequestDto(
    val title: String, val description: String, val priority: String,
    val assigneeId: String? = null, val dueAt: String? = null, val columnId: String? = null,
)
@Serializable data class TaskPatchDto(
    val title: String, val description: String, val priority: String,
    val assigneeId: String?, val dueAt: String?,
)
@Serializable data class MoveTaskDto(val columnId: String, val comment: String? = null)
@Serializable data class CommentDto(val id: String, val authorId: String, val text: String, val createdAt: String)
@Serializable data class StudentGroupDto(val id: String, val groupCode: String, val name: String)
@Serializable data class EventRequestDto(
    val title: String, val description: String, val location: String, val startsAt: String,
    val endsAt: String? = null, val visibility: String, val group: String? = null,
    val typeId: String? = null, val myReminders: Set<Int> = emptySet(),
)
@Serializable data class EventPatchDto(
    val title: String, val description: String, val location: String, val startsAt: String,
    val endsAt: String?, val visibility: String, val group: String?, val typeId: String?,
)
@Serializable data class EventDto(
    val id: String, val ownerId: String, val title: String, val startsAt: String,
    val visibility: String, val masked: Boolean, val partial: Boolean,
    val description: String = "", val location: String = "", val endsAt: String? = null,
    val group: String? = null, val typeId: String? = null,
)
@Serializable data class EventTypeRequestDto(val title: String, val color: String, val defaultReminders: Set<Int>)
@Serializable data class EventTypeDto(
    val id: String, val title: String, val color: String,
    val ownerId: String? = null, val defaultReminders: Set<Int>,
)
@Serializable data class RemindersRequestDto(val minutes: Set<Int>)
@Serializable data class ProjectMemberDto(val userId: String, val role: String)
@Serializable data class ProjectDto(
    val id: String,
    val name: String,
    val description: String,
    val members: List<ProjectMemberDto>,
    val sources: List<ProjectSourceDto>,
)
@Serializable data class FileInfoDto(
    val id: String, val name: String, val sizeBytes: Long,
    val mimeType: String, val downloadUrl: String,
)
@Serializable data class ProjectSourceDto(
    val id: String, val kind: String, val title: String, val addedAt: String,
    val url: String? = null, val file: FileInfoDto? = null, val addedBy: String? = null,
)
@Serializable data class ColumnDto(
    val id: String,
    val projectId: String,
    val title: String,
    val position: Int,
    val kind: String,
    val color: String,
)
@Serializable data class TaskDto(
    val id: String,
    val projectId: String,
    val columnId: String,
    val position: Int,
    val title: String,
    val description: String,
    val priority: String,
    val createdBy: String,
    val createdAt: String,
    val assigneeId: String? = null,
    val dueAt: String? = null,
    val completedAt: String? = null,
    val attachments: List<AttachmentDto> = emptyList(),
    val comments: List<CommentDto> = emptyList(),
    val history: List<TaskHistoryDto> = emptyList(),
)
@Serializable data class AttachmentDto(
    val id: String, val kind: String, val uploadedBy: String,
    val uploadedAt: String, val file: FileInfoDto,
)
@Serializable data class TaskHistoryDto(
    val id: String, val action: String, val at: String,
    val actorId: String? = null, val fromColumn: String? = null,
    val toColumn: String? = null, val comment: String? = null,
)
@Serializable data class ScheduleDto(
    val lessons: List<LessonDto>,
    val source: String,
    val updatedAt: String? = null,
)
@Serializable data class LessonDto(
    val id: String,
    val group: String,
    val date: String,
    val start: String,
    val end: String,
    val title: String,
    val room: String,
    val teacher: String,
    val kind: String,
)
