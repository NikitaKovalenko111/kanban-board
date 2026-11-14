@file:UseSerializers(InstantSerializer::class, LocalDateSerializer::class)

package com.kanbanboard.mobile.core.model

import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// Идентификаторы — строки (бэкенд выдаёт UUID). Value-классы не дают перепутать ID разных сущностей.
@Serializable @JvmInline value class UserId(val value: String)
@Serializable @JvmInline value class ProjectId(val value: String)
@Serializable @JvmInline value class ColumnId(val value: String)
@Serializable @JvmInline value class TaskId(val value: String)

@Serializable(with = StudyGroupSerializer::class)
data class StudyGroup(val code: String) : java.io.Serializable {
    companion object {
        val G203 = StudyGroup("203")
        val G204 = StudyGroup("204")
        /** Группы демо-режима; сервер может возвращать другие коды. */
        val entries = listOf(G203, G204)

        fun fromCode(code: String?): StudyGroup? = when (code?.trim()) {
            null, "" -> null
            "G203" -> G203
            "G204" -> G204
            else -> StudyGroup(code.trim())
        }
    }
}

/** Сохраняем строковое представление, чтобы читать старые локальные данные с G203/G204. */
object StudyGroupSerializer : KSerializer<StudyGroup> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("StudyGroup", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: StudyGroup) = encoder.encodeString(value.code)
    override fun deserialize(decoder: Decoder): StudyGroup =
        StudyGroup.fromCode(decoder.decodeString()) ?: error("Пустой код учебной группы")
}

@Serializable
data class User(
    val id: UserId,
    val fullName: String,
    val login: String,
    val studyGroup: StudyGroup,
    val contact: String?,
    val isActive: Boolean,
    val joinedAt: LocalDate,
    /** Ссылка на фото профиля (в демо — путь к файлу в памяти приложения), null — инициалы. */
    val photo: String? = null,
    /** Что другие участники видят о личных событиях этого пользователя. */
    val schedulePrivacy: SchedulePrivacy = SchedulePrivacy.BUSY,
)

/** Приватность личных событий в чужом расписании (Профиль → Настройки аккаунта). */
@Serializable
enum class SchedulePrivacy(val title: String) {
    NOTHING("Ничего"),
    BUSY("Только «Занят»"),
    TITLE("Название события"),
    TITLE_AND_DESCRIPTION("Название и описание"),
}

/** Роль в команде. Капитан (Team Lead) — статус в общей команде; роли в проектах описаны в [Project.members]. */
@Serializable
enum class TeamRole { CAPTAIN, MEMBER }

@Serializable
data class TeamMember(val user: User, val role: TeamRole)

@Serializable
data class Team(val name: String, val description: String)

data class Session(val user: User, val role: TeamRole) {
    val isCaptain: Boolean get() = role == TeamRole.CAPTAIN
}

@Serializable
data class Project(
    val id: ProjectId,
    val name: String,
    val description: String,
    /** Участник проекта → его функциональная роль в этом проекте («Backend», «Капитан хакатона»…). */
    val members: Map<UserId, String>,
    /** Источники проекта: ссылки (ТЗ, Google Docs, чаты) и файлы. */
    val sources: List<ProjectSource> = emptyList(),
    val canViewBoard: Boolean = true,
) {
    fun isMember(userId: UserId): Boolean = userId in members
}

@Serializable
enum class SourceKind { LINK, FILE }

@Serializable
data class ProjectSource(
    val id: String,
    val kind: SourceKind,
    val title: String,
    /** Для ссылки — адрес; для файла — null. */
    val url: String?,
    val sizeBytes: Long?,
    val addedBy: UserId,
    val addedAt: Instant,
    /** Для файла — ссылка на содержимое в хранилище ([com.kanbanboard.mobile.core.files.FileStore]). */
    val fileRef: String? = null,
)

data class ProjectDraft(
    val name: String,
    val description: String,
    val members: Map<UserId, String>,
)

/**
 * Роль колонки в процессе. Капитан задаёт только название и цвет колонки (спринт 3),
 * а роль определяется её местом на доске — см. [ColumnKind.forPosition].
 */
@Serializable
enum class ColumnKind {
    BACKLOG,
    IN_PROGRESS,
    REVIEW,
    DONE;

    companion object {
        /** На доске должно быть не меньше 4 колонок: новые, хотя бы одна рабочая, проверка, готово. */
        const val MIN_COLUMNS = 4

        /**
         * Первая колонка — новые задачи, последняя — выполненные, предпоследняя — проверка,
         * все промежуточные (например, «В работе», «Pull request») — рабочие этапы.
         */
        fun forPosition(index: Int, count: Int): ColumnKind = when {
            index == 0 -> BACKLOG
            index == count - 1 -> DONE
            index == count - 2 -> REVIEW
            else -> IN_PROGRESS
        }
    }
}

@Serializable
data class BoardColumn(
    val id: ColumnId,
    val projectId: ProjectId,
    val title: String,
    val position: Int,
    val kind: ColumnKind,
    /** Цвет точки и индикатора колонки, ARGB. */
    val color: Long,
)

@Serializable
enum class TaskPriority(val title: String) {
    LOW("Низкий"),
    NORMAL("Обычный"),
    HIGH("Высокий"),
}

@Serializable
enum class AttachmentKind { SOURCE, RESULT }

@Serializable
data class Attachment(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val kind: AttachmentKind,
    val uploadedBy: UserId,
    val uploadedAt: Instant,
    /** Ссылка на содержимое в хранилище ([com.kanbanboard.mobile.core.files.FileStore]); null — файла нет на устройстве. */
    val fileRef: String? = null,
    /** Адрес внешней ссылки, если вложение не является файлом. */
    val url: String? = null,
)

@Serializable
data class TaskComment(
    val id: String,
    val authorId: UserId,
    val text: String,
    val createdAt: Instant,
)

@Serializable
enum class TaskAction(val title: String) {
    CREATED("Задача создана"),
    ASSIGNED("Назначен исполнитель"),
    EDITED("Задача изменена"),
    TAKEN("Взята в работу"),
    MOVED("Перемещена"),
    SUBMITTED("Сдана на проверку"),
    RETURNED("Возвращена на доработку"),
    ACCEPTED("Принята"),
}

/** Запись аудита. Названия колонок сохраняются снимком на момент действия. */
@Serializable
data class TaskHistoryEntry(
    val id: String,
    val action: TaskAction,
    val actorId: UserId,
    val at: Instant,
    val fromColumn: String? = null,
    val toColumn: String? = null,
    val comment: String? = null,
)

@Serializable
data class Task(
    val id: TaskId,
    val projectId: ProjectId,
    val columnId: ColumnId,
    val position: Int,
    val title: String,
    val description: String,
    val priority: TaskPriority,
    val assigneeId: UserId?,
    val createdBy: UserId,
    val createdAt: Instant,
    val dueAt: Instant?,
    val completedAt: Instant?,
    val attachments: List<Attachment>,
    val comments: List<TaskComment>,
    val history: List<TaskHistoryEntry>,
)

/** Данные формы создания/редактирования задачи. */
data class TaskDraft(
    val title: String,
    val description: String,
    val priority: TaskPriority,
    val assigneeId: UserId?,
    val dueAt: Instant?,
    /** Колонка для новой задачи; при редактировании игнорируется. */
    val columnId: ColumnId? = null,
)

/** Колонка в форме настройки доски; id == null — новая колонка. Роль задаёт порядок. */
data class ColumnDraft(val id: ColumnId?, val title: String, val color: Long)

/** Файл, выбранный пользователем и уже скопированный в хранилище приложения. */
data class PickedFile(val name: String, val sizeBytes: Long, val fileRef: String)

@Serializable
enum class NotificationKind(val title: String) {
    COMMENT("Комментарий"),
    RETURNED("Возврат на доработку"),
    STATUS("Смена статуса"),
    EVENT("Скоро событие"),
}

/** Уведомление в приложении. Для задач хранит taskId, для событий — eventId. */
@Serializable
data class AppNotification(
    val id: String,
    val kind: NotificationKind,
    val title: String,
    val text: String,
    val at: Instant,
    val read: Boolean,
    val taskId: TaskId? = null,
    val eventId: String? = null,
)

data class NewAccount(
    val fullName: String,
    val login: String,
    val temporaryPassword: String,
    val studyGroup: StudyGroup,
    val role: TeamRole,
)
