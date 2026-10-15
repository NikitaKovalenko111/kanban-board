package com.kanbanboard.mobile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.kanbanboard.mobile.data.FakeKanbanRepository
import com.kanbanboard.mobile.data.KanbanRepository
import com.kanbanboard.mobile.model.Comment
import com.kanbanboard.mobile.model.Task
import com.kanbanboard.mobile.model.TaskStatus
import com.kanbanboard.mobile.model.TeamSettings
import com.kanbanboard.mobile.model.User
import com.kanbanboard.mobile.model.UserRole
import com.kanbanboard.mobile.model.LoginCredentials

data class AppUiState(
    val user: User? = null,
    val tasks: List<Task> = emptyList(),
    val members: List<User> = emptyList(),
    val teamSettings: TeamSettings = TeamSettings("", ""),
    val loginError: String? = null,
)

class AppViewModel(
    private val repository: KanbanRepository = FakeKanbanRepository(),
) : ViewModel() {
    var uiState by mutableStateOf(AppUiState(
        tasks = repository.tasks(),
        members = repository.members(),
        teamSettings = repository.teamSettings(),
    ))
        private set

    val events get() = repository.events()

    fun lessons(group: String) = repository.lessons(group)

    fun login(login: String, password: String) {
        val user = repository.login(LoginCredentials(login, password))
        uiState = if (user == null) {
            uiState.copy(loginError = "Неверный логин или пароль")
        } else {
            uiState.copy(user = user, loginError = null)
        }
    }

    fun logout() {
        uiState = uiState.copy(user = null, loginError = null)
    }

    fun moveTask(taskId: Long, status: TaskStatus) {
        uiState = uiState.copy(tasks = uiState.tasks.map { task ->
            if (task.id == taskId) task.copy(status = status) else task
        })
    }

    fun addComment(taskId: Long, text: String) {
        val normalized = text.trim()
        if (normalized.isEmpty()) return
        uiState = uiState.copy(tasks = uiState.tasks.map { task ->
            if (task.id == taskId) {
                task.copy(comments = task.comments + Comment(
                    id = "local-${System.currentTimeMillis()}",
                    author = uiState.user?.name ?: "Пользователь",
                    text = normalized,
                    time = "Только что",
                ))
            } else task
        })
    }

    fun createTask(title: String, description: String, deadline: String) {
        val normalizedTitle = title.trim()
        if (normalizedTitle.isEmpty() || description.trim().isEmpty()) return
        val task = Task(
            id = (uiState.tasks.maxOfOrNull { it.id } ?: 100) + 1,
            title = normalizedTitle,
            description = description.trim(),
            status = TaskStatus.WAITING,
            assignee = "Не назначен",
            deadline = deadline.ifBlank { "Без срока" },
        )
        uiState = uiState.copy(tasks = listOf(task) + uiState.tasks)
    }

    fun updateTeam(name: String, description: String) {
        if (uiState.user?.role != UserRole.LEAD) return
        uiState = uiState.copy(teamSettings = TeamSettings(name.trim(), description.trim()))
    }

    fun toggleMember(memberId: Long) {
        val currentUser = uiState.user ?: return
        if (currentUser.role != UserRole.LEAD || currentUser.id == memberId) return
        uiState = uiState.copy(members = uiState.members.map { member ->
            if (member.id == memberId) member.copy(active = !member.active) else member
        })
    }

    fun addMember(name: String, login: String, position: String, role: UserRole) {
        if (uiState.user?.role != UserRole.LEAD || name.isBlank() || login.isBlank() || position.isBlank()) return
        val member = User(
            id = (uiState.members.maxOfOrNull { it.id } ?: 0) + 1,
            name = name.trim(),
            login = login.trim().lowercase(),
            role = role,
            position = position.trim(),
            joinedAt = "15.10.2026",
        )
        uiState = uiState.copy(members = uiState.members + member)
    }
}
