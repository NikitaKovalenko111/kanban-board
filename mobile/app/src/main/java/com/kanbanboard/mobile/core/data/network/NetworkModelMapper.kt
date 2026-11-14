package com.kanbanboard.mobile.core.data.network

import com.kanbanboard.mobile.core.model.BoardColumn
import com.kanbanboard.mobile.core.model.Attachment
import com.kanbanboard.mobile.core.model.AttachmentKind
import com.kanbanboard.mobile.core.model.AppNotification
import com.kanbanboard.mobile.core.model.NotificationKind
import com.kanbanboard.mobile.core.model.ColumnId
import com.kanbanboard.mobile.core.model.ColumnKind
import com.kanbanboard.mobile.core.model.EventId
import com.kanbanboard.mobile.core.model.EventType
import com.kanbanboard.mobile.core.model.EventTypeId
import com.kanbanboard.mobile.core.model.EventVisibility
import com.kanbanboard.mobile.core.model.Lesson
import com.kanbanboard.mobile.core.model.LessonKind
import com.kanbanboard.mobile.core.model.LessonsOrigin
import com.kanbanboard.mobile.core.model.LessonsSnapshot
import com.kanbanboard.mobile.core.model.Project
import com.kanbanboard.mobile.core.model.ProjectId
import com.kanbanboard.mobile.core.model.ProjectSource
import com.kanbanboard.mobile.core.model.ScheduleEvent
import com.kanbanboard.mobile.core.model.SchedulePrivacy
import com.kanbanboard.mobile.core.model.SourceKind
import com.kanbanboard.mobile.core.model.StudyGroup
import com.kanbanboard.mobile.core.model.TeamMember
import com.kanbanboard.mobile.core.model.TeamRole
import com.kanbanboard.mobile.core.model.Task
import com.kanbanboard.mobile.core.model.TaskAction
import com.kanbanboard.mobile.core.model.TaskComment
import com.kanbanboard.mobile.core.model.TaskHistoryEntry
import com.kanbanboard.mobile.core.model.TaskId
import com.kanbanboard.mobile.core.model.TaskPriority
import com.kanbanboard.mobile.core.model.User
import com.kanbanboard.mobile.core.model.UserId
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Преобразование OpenAPI-моделей в модели экранов; неизвестные значения не подменяем демо-данными. */
internal object NetworkModelMapper {
    fun user(dto: UserDto): User = User(
        id = UserId(dto.id), fullName = dto.fullName, login = dto.username,
        studyGroup = group(dto.studentGroup), contact = dto.contact, isActive = dto.active,
        joinedAt = Instant.parse(dto.joinedAt).atZone(java.time.ZoneOffset.UTC).toLocalDate(),
        photo = dto.imageUrl, schedulePrivacy = SchedulePrivacy.valueOf(dto.schedulePrivacy),
    )

    fun member(dto: UserDto): TeamMember = TeamMember(user(dto), TeamRole.valueOf(dto.role))

    fun project(dto: ProjectDto): Project = Project(
        id = ProjectId(dto.id), name = dto.name, description = dto.description,
        canViewBoard = dto.canViewBoard,
        members = dto.members.associate { UserId(it.userId) to it.role },
        sources = dto.sources.map { source ->
            ProjectSource(
                id = source.id, kind = SourceKind.valueOf(source.kind), title = source.title,
                url = source.url, sizeBytes = source.file?.sizeBytes,
                addedBy = UserId(source.addedBy ?: DELETED_USER_ID),
                addedAt = Instant.parse(source.addedAt), fileRef = source.file?.downloadUrl,
            )
        },
    )

    fun column(dto: ColumnDto): BoardColumn = BoardColumn(
        id = ColumnId(dto.id), projectId = ProjectId(dto.projectId), title = dto.title,
        position = dto.position, kind = ColumnKind.valueOf(dto.kind), color = color(dto.color),
    )

    fun task(dto: TaskDto): Task = Task(
        id = TaskId(dto.id), projectId = ProjectId(dto.projectId), columnId = ColumnId(dto.columnId),
        position = dto.position, title = dto.title, description = dto.description,
        priority = TaskPriority.valueOf(dto.priority), assigneeId = dto.assigneeId?.let(::UserId),
        createdBy = UserId(dto.createdBy), createdAt = Instant.parse(dto.createdAt),
        dueAt = dto.dueAt?.let(Instant::parse), completedAt = dto.completedAt?.let(Instant::parse),
        attachments = dto.attachments.map { attachment ->
            Attachment(
                id = attachment.id,
                name = attachment.file?.name ?: attachment.title ?: attachment.url ?: "Ссылка",
                sizeBytes = attachment.file?.sizeBytes ?: 0,
                kind = AttachmentKind.valueOf(attachment.kind), uploadedBy = UserId(attachment.uploadedBy),
                uploadedAt = Instant.parse(attachment.uploadedAt),
                fileRef = attachment.file?.downloadUrl,
                url = attachment.url?.takeIf { attachment.contentKind == "LINK" },
            )
        },
        comments = dto.comments.map { comment ->
            TaskComment(comment.id, UserId(comment.authorId), comment.text, Instant.parse(comment.createdAt))
        },
        history = dto.history.map { entry ->
            TaskHistoryEntry(
                id = entry.id, action = TaskAction.valueOf(entry.action),
                actorId = UserId(entry.actorId ?: DELETED_USER_ID), at = Instant.parse(entry.at),
                fromColumn = entry.fromColumn, toColumn = entry.toColumn, comment = entry.comment,
            )
        },
    )

    fun schedule(dto: ScheduleDto): LessonsSnapshot = LessonsSnapshot(
        lessons = dto.lessons.map(::lesson), updatedAt = dto.updatedAt?.let(Instant::parse),
        source = when (dto.source) {
            "ACTUAL" -> LessonsOrigin.ISU
            "CACHED" -> LessonsOrigin.CACHE
            "UNAVAILABLE" -> LessonsOrigin.UNAVAILABLE
            else -> error("Неизвестный источник расписания: ${dto.source}")
        },
    )

    fun lesson(dto: LessonDto): Lesson = Lesson(
        id = dto.id, group = group(dto.group), date = LocalDate.parse(dto.date),
        start = LocalTime.parse(dto.start), end = LocalTime.parse(dto.end), title = dto.title,
        room = dto.room, teacher = dto.teacher, kind = LessonKind.valueOf(dto.kind),
    )

    fun event(dto: EventDto): ScheduleEvent = ScheduleEvent(
        id = EventId(dto.id), ownerId = UserId(dto.ownerId), title = dto.title,
        description = dto.description, location = dto.location, startsAt = Instant.parse(dto.startsAt),
        endsAt = dto.endsAt?.let(Instant::parse), visibility = EventVisibility.valueOf(dto.visibility),
        group = dto.group?.let(::group), typeId = dto.typeId?.let(::EventTypeId),
        masked = dto.masked, partial = dto.partial,
    )

    fun eventType(dto: EventTypeDto): EventType = EventType(
        id = EventTypeId(dto.id), title = dto.title, color = color(dto.color),
        ownerId = dto.ownerId?.let(::UserId), defaultReminders = dto.defaultReminders,
    )

    fun notification(dto: NotificationDto): AppNotification = AppNotification(
        id = dto.id, kind = NotificationKind.valueOf(dto.kind), title = dto.title,
        text = dto.text, at = Instant.parse(dto.at), read = dto.read,
        taskId = dto.taskId?.let(::TaskId), eventId = dto.eventId,
    )

    fun color(hex: String): Long {
        require(hex.matches(Regex("#[0-9A-Fa-f]{6}"))) { "Неверный цвет сервера" }
        return 0xff000000L or hex.substring(1).toLong(16)
    }

    fun hexColor(argb: Long): String = "#%06X".format(argb and 0xffffff)

    private fun group(code: String): StudyGroup =
        StudyGroup.fromCode(code) ?: error("Неизвестная учебная группа: $code")

    private const val DELETED_USER_ID = "00000000-0000-0000-0000-000000000000"
}
