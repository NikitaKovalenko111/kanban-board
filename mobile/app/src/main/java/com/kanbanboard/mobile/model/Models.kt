package com.kanbanboard.mobile.model

enum class TaskStatus(val title: String) {
    WAITING("Ожидает"),
    WORKING("В работе"),
    CHECKING("На проверке"),
    DONE("Готово");

    fun next(): TaskStatus? = entries.getOrNull(ordinal + 1)
}

data class Attachment(
    val id: String,
    val name: String,
    val sizeLabel: String,
)

data class Comment(
    val id: String,
    val author: String,
    val text: String,
    val time: String,
)

data class Task(
    val id: Long,
    val title: String,
    val description: String,
    val status: TaskStatus,
    val assignee: String,
    val deadline: String,
    val attachments: List<Attachment> = emptyList(),
    val resultAttachments: List<Attachment> = emptyList(),
    val comments: List<Comment> = emptyList(),
    val completedAt: String? = null,
    val teamId: Long = 1,
    val projectId: Long = 1,
    val assigneeId: Long? = null,
    val dueAt: String? = null,
    val history: List<TaskHistoryEntry> = emptyList(),
)

data class TaskHistoryEntry(
    val id: String,
    val action: String,
    val actorId: Long?,
    val actorName: String,
    val occurredAt: String,
    val comment: String? = null,
)

enum class UserRole { LEAD, MEMBER }

data class User(
    val id: Long,
    val name: String,
    val login: String,
    val role: UserRole,
    val position: String,
    val active: Boolean = true,
    val joinedAt: String,
    val contact: String? = null,
)

data class LoginCredentials(val login: String, val password: String)

data class TeamSettings(val name: String, val description: String)

data class Team(
    val id: Long,
    val name: String,
    val description: String,
    val memberIds: Set<Long>,
)

data class Project(
    val id: Long,
    val teamId: Long,
    val name: String,
    val description: String = "",
    val memberIds: Set<Long> = emptySet(),
)

data class Lesson(
    val date: String,
    val time: String,
    val title: String,
    val room: String,
    val teacher: String,
    val kind: LessonKind,
)

enum class LessonKind(val title: String, val color: Long) {
    LECTURE("Лекция", 0xFF6576DF),
    PRACTICE("Практика", 0xFFDA8A37),
    LAB("Лабораторная", 0xFF38A585),
}

enum class EventVisibility(val title: String) {
    TEAM("Команда"),
    GROUP("Группа"),
    PERSONAL("Личное"),
}

data class TeamEvent(
    val id: Long,
    val title: String,
    val date: String,
    val time: String,
    val visibility: EventVisibility,
    val endTime: String = time,
    val description: String = "",
    val group: String? = null,
    val typeId: String = "meeting",
    val reminderMinutes: Set<Int> = emptySet(),
    val ownerId: Long? = null,
    val teamId: Long = 1,
    val location: String = "",
)

data class EventType(
    val id: String,
    val title: String,
    val color: Long,
    val custom: Boolean = false,
    val defaultReminders: Set<Int> = emptySet(),
)

enum class AppTheme { LIGHT, DARK, AMOLED }
