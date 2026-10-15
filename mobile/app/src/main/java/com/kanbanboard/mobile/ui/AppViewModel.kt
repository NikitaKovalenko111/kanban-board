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
import com.kanbanboard.mobile.model.Team

data class AppUiState(
    val user: User? = null,
    val tasks: List<Task> = emptyList(),
    val members: List<User> = emptyList(),
    val teamSettings: TeamSettings = TeamSettings("", ""),
    val teams: List<Team> = emptyList(),
    val selectedTeamId: Long = 1,
    val loginError: String? = null,
)

class AppViewModel(
    private val repository: KanbanRepository = FakeKanbanRepository(),
) : ViewModel() {
    var uiState by mutableStateOf(AppUiState(
        tasks = repository.tasks(),
        members = repository.members(),
        teamSettings = repository.teamSettings(),
        teams = repository.teams(),
    ))
        private set

    val events get() = repository.events()
    private val createdPasswords = mutableMapOf<String, String>()

    fun lessons(group: String) = repository.lessons(group)

    fun login(login: String, password: String) {
        val normalizedLogin = login.trim().lowercase()
        val user = repository.login(LoginCredentials(normalizedLogin, password))
            ?: uiState.members.firstOrNull {
                it.login == normalizedLogin && it.active && createdPasswords[normalizedLogin] == password
            }
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
        val user = uiState.user ?: return
        val currentTask = uiState.tasks.firstOrNull { it.id == taskId } ?: return
        val isAssignee = currentTask.assignee == user.name
        val allowed = when {
            isAssignee && currentTask.status == TaskStatus.WAITING && status == TaskStatus.WORKING -> true
            isAssignee && currentTask.status == TaskStatus.WORKING && status == TaskStatus.CHECKING -> true
            user.role == UserRole.LEAD && currentTask.status == TaskStatus.CHECKING && status == TaskStatus.DONE -> true
            user.role == UserRole.LEAD && currentTask.status == TaskStatus.CHECKING && status == TaskStatus.WORKING -> true
            user.role == UserRole.LEAD && currentTask.status == TaskStatus.DONE && status == TaskStatus.WORKING -> true
            else -> false
        }
        if (!allowed) return
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
            assignee = uiState.user?.name ?: "Не назначен",
            deadline = deadline.ifBlank { "Без срока" },
            teamId = uiState.selectedTeamId,
        )
        uiState = uiState.copy(tasks = listOf(task) + uiState.tasks)
    }

    fun updateTeam(name: String, description: String) {
        if (uiState.user?.role != UserRole.LEAD) return
        val updated = TeamSettings(name.trim(), description.trim())
        uiState = uiState.copy(
            teamSettings = updated,
            teams = uiState.teams.map { team ->
                if (team.id == uiState.selectedTeamId) team.copy(name = updated.name, description = updated.description) else team
            },
        )
    }

    fun toggleMember(memberId: Long) {
        val currentUser = uiState.user ?: return
        if (currentUser.role != UserRole.LEAD || currentUser.id == memberId) return
        uiState = uiState.copy(members = uiState.members.map { member ->
            if (member.id == memberId) member.copy(active = !member.active) else member
        })
    }

    fun addMember(name: String, login: String, temporaryPassword: String, position: String, role: UserRole) {
        if (uiState.user?.role != UserRole.LEAD || name.isBlank() || login.isBlank() || temporaryPassword.length < 6 || position.isBlank()) return
        val normalizedLogin = login.trim().lowercase()
        if (uiState.members.any { it.login == normalizedLogin }) return
        val member = User(
            id = (uiState.members.maxOfOrNull { it.id } ?: 0) + 1,
            name = name.trim(),
            login = normalizedLogin,
            role = role,
            position = position.trim(),
            joinedAt = "15.10.2026",
        )
        createdPasswords[normalizedLogin] = temporaryPassword
        uiState = uiState.copy(
            members = uiState.members + member,
            teams = uiState.teams.map { team ->
                if (team.id == uiState.selectedTeamId) team.copy(memberIds = team.memberIds + member.id) else team
            },
        )
    }

    fun selectTeam(teamId: Long) {
        val user = uiState.user ?: return
        val team = uiState.teams.firstOrNull { it.id == teamId && user.id in it.memberIds } ?: return
        uiState = uiState.copy(
            selectedTeamId = team.id,
            teamSettings = TeamSettings(team.name, team.description),
        )
    }

    fun joinTeam(teamId: Long) {
        val user = uiState.user ?: return
        val team = uiState.teams.firstOrNull { it.id == teamId } ?: return
        uiState = uiState.copy(
            teams = uiState.teams.map { if (it.id == teamId) it.copy(memberIds = it.memberIds + user.id) else it },
            selectedTeamId = teamId,
            teamSettings = TeamSettings(team.name, team.description),
        )
    }

    fun leaveTeam(teamId: Long) {
        val user = uiState.user ?: return
        val updatedTeams = uiState.teams.map { team ->
            if (team.id == teamId) team.copy(memberIds = team.memberIds - user.id) else team
        }
        val nextTeam = updatedTeams.firstOrNull { user.id in it.memberIds }
        uiState = uiState.copy(
            teams = updatedTeams,
            selectedTeamId = if (uiState.selectedTeamId == teamId) nextTeam?.id ?: 0 else uiState.selectedTeamId,
            teamSettings = if (uiState.selectedTeamId == teamId) {
                nextTeam?.let { TeamSettings(it.name, it.description) } ?: TeamSettings("Команда не выбрана", "Выберите или вступите в команду")
            } else uiState.teamSettings,
        )
    }
}
