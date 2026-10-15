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
)

data class LoginCredentials(val login: String, val password: String)

data class TeamSettings(val name: String, val description: String)

data class Team(
    val id: Long,
    val name: String,
    val description: String,
    val memberIds: Set<Long>,
)

data class Lesson(
    val time: String,
    val title: String,
    val room: String,
    val teacher: String,
)

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
    val reminder: String,
)
