package com.kanbanboard.mobile.core.data

import com.kanbanboard.mobile.core.model.AttachmentKind
import com.kanbanboard.mobile.core.model.BoardColumn
import com.kanbanboard.mobile.core.model.ColumnDraft
import com.kanbanboard.mobile.core.model.ColumnId
import com.kanbanboard.mobile.core.model.NewAccount
import com.kanbanboard.mobile.core.model.Project
import com.kanbanboard.mobile.core.model.ProjectDraft
import com.kanbanboard.mobile.core.model.ProjectId
import com.kanbanboard.mobile.core.model.Session
import com.kanbanboard.mobile.core.model.StudyGroup
import com.kanbanboard.mobile.core.model.Task
import com.kanbanboard.mobile.core.model.TaskDraft
import com.kanbanboard.mobile.core.model.TaskId
import com.kanbanboard.mobile.core.model.Team
import com.kanbanboard.mobile.core.model.TeamMember
import com.kanbanboard.mobile.core.model.TeamRole
import com.kanbanboard.mobile.core.model.EventDraft
import com.kanbanboard.mobile.core.model.EventId
import com.kanbanboard.mobile.core.model.EventType
import com.kanbanboard.mobile.core.model.EventTypeDraft
import com.kanbanboard.mobile.core.model.EventTypeId
import com.kanbanboard.mobile.core.model.LessonsSnapshot
import com.kanbanboard.mobile.core.model.ScheduleEvent
import com.kanbanboard.mobile.core.model.UserId
import com.kanbanboard.mobile.core.model.AppNotification
import com.kanbanboard.mobile.core.model.PickedFile
import com.kanbanboard.mobile.core.model.SchedulePrivacy
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/*
 * Контракты данных. Сейчас реализованы демо-версии (core/data/demo), сетевые появятся,
 * когда будет OpenAPI. Экраны зависят только от этих интерфейсов.
 * Все мутации возвращают Result: ошибки — AppError с текстом для пользователя.
 */

interface SessionRepository {
    val session: StateFlow<Session?>
    suspend fun login(login: String, password: String): Result<Session>
    suspend fun logout()
}

interface TeamRepository {
    val team: Flow<Team>
    val members: Flow<List<TeamMember>>
    suspend fun updateMyStudyGroup(group: StudyGroup): Result<Unit>
    suspend fun updateMySchedulePrivacy(privacy: SchedulePrivacy): Result<Unit>
    /** Новое фото профиля (ссылка из FileStore) или null — удалить фото. */
    suspend fun updateMyPhoto(photo: String?): Result<Unit>
    suspend fun createAccount(account: NewAccount): Result<TeamMember>
    /** Капитан: заблокировать или восстановить доступ участнику. */
    suspend fun setMemberActive(userId: UserId, active: Boolean): Result<Unit>
    /** Капитан: сбросить пароль; возвращает новый временный пароль, который нужно передать участнику лично. */
    suspend fun resetPassword(userId: UserId): Result<String>

    /** Смена своего пароля. */
    suspend fun changePassword(current: String, new: String): Result<Unit>

    /** Капитан: сделать участника капитаном или снять роль. */
    suspend fun setMemberRole(userId: UserId, role: TeamRole): Result<Unit>

    /** Капитан: удалить аккаунт (не себя и не человека с незакрытыми задачами). */
    suspend fun deleteAccount(userId: UserId): Result<Unit>
}

interface NotificationRepository {
    /** Мои уведомления, новые сверху. */
    val notifications: Flow<List<AppNotification>>
    /** Отметить все прочитанными — вызывается при открытии раздела уведомлений. */
    suspend fun markAllRead()
}

interface ProjectRepository {
    val projects: Flow<List<Project>>
    fun project(id: ProjectId): Flow<Project?>
    suspend fun createProject(draft: ProjectDraft): Result<Project>
    suspend fun updateProject(id: ProjectId, draft: ProjectDraft): Result<Unit>
    suspend fun addLink(id: ProjectId, title: String, url: String): Result<Unit>
    suspend fun addFile(id: ProjectId, file: PickedFile): Result<Unit>
    suspend fun removeSource(id: ProjectId, sourceId: String): Result<Unit>
    suspend fun deleteProject(id: ProjectId): Result<Unit>
}

interface BoardRepository {
    /** Колонки проекта, отсортированные по позиции. */
    fun columns(projectId: ProjectId): Flow<List<BoardColumn>>
    fun tasks(projectId: ProjectId): Flow<List<Task>>
    fun task(taskId: TaskId): Flow<Task?>
    /** Незакрытые задачи участника во всех проектах — для дедлайнов в расписании. */
    fun assignedTasks(userId: UserId): Flow<List<Task>>
    /** Задачи в колонках вида «Готово» во всех проектах — для архива. */
    fun completedTasks(): Flow<List<Task>>

    suspend fun createTask(projectId: ProjectId, draft: TaskDraft): Result<Task>
    suspend fun updateTask(taskId: TaskId, draft: TaskDraft): Result<Task>
    suspend fun deleteTask(taskId: TaskId): Result<Unit>
    suspend fun takeTask(taskId: TaskId): Result<Unit>
    suspend fun moveTask(taskId: TaskId, columnId: ColumnId, comment: String?): Result<Unit>
    suspend fun addComment(taskId: TaskId, text: String): Result<Unit>
    suspend fun addAttachment(taskId: TaskId, file: PickedFile, kind: AttachmentKind): Result<Unit>
    suspend fun removeAttachment(taskId: TaskId, attachmentId: String): Result<Unit>
    suspend fun assignToSelf(taskId: TaskId): Result<Unit>
    suspend fun saveColumns(projectId: ProjectId, columns: List<ColumnDraft>): Result<Unit>
}

interface ScheduleRepository {
    /** Пары группы за период из ИСУ; при сбое — последний кэш (см. [LessonsSnapshot.source]). */
    fun lessons(group: StudyGroup, from: LocalDate, to: LocalDate): Flow<LessonsSnapshot>

    /**
     * События в расписании человека [personId] глазами текущего пользователя:
     * командные, групповые для [groups] и личные этого человека (чужие — замаскированы).
     */
    fun events(personId: UserId, groups: Set<StudyGroup>, from: Instant, to: Instant): Flow<List<ScheduleEvent>>

    fun event(id: EventId): Flow<ScheduleEvent?>

    val eventTypes: Flow<List<EventType>>

    /** Мои напоминания: ключ элемента ([com.kanbanboard.mobile.core.model.ReminderKeys]) → минуты до начала. */
    val myReminders: Flow<Map<String, Set<Int>>>

    suspend fun createEvent(draft: EventDraft): Result<ScheduleEvent>
    suspend fun updateEvent(id: EventId, draft: EventDraft): Result<Unit>
    suspend fun deleteEvent(id: EventId): Result<Unit>
    suspend fun saveEventType(id: EventTypeId?, draft: EventTypeDraft): Result<EventType>
    suspend fun deleteEventType(id: EventTypeId): Result<Unit>
    suspend fun setMyReminders(itemKey: String, minutes: Set<Int>): Result<Unit>
}
