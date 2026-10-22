package com.kanbanboard.mobile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.kanbanboard.mobile.data.FakeKanbanRepository
import com.kanbanboard.mobile.data.KanbanRepository
import com.kanbanboard.mobile.data.AppStorage
import com.kanbanboard.mobile.data.MemoryAppStorage
import com.kanbanboard.mobile.model.AppTheme
import com.kanbanboard.mobile.model.EventType
import com.kanbanboard.mobile.model.EventVisibility
import com.kanbanboard.mobile.model.TeamEvent
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
    val theme: AppTheme = AppTheme.LIGHT,
    val studentGroup: String = "203",
    val events: List<TeamEvent> = emptyList(),
    val eventTypes: List<EventType> = emptyList(),
)

class AppViewModel(
    private val repository: KanbanRepository = FakeKanbanRepository(),
    private val storage: AppStorage = MemoryAppStorage(),
) : ViewModel() {
    var uiState by mutableStateOf(AppUiState(
        tasks = repository.tasks(),
        members = repository.members(),
        teamSettings = repository.teamSettings(),
        teams = repository.teams(),
        theme = storage.theme(),
        studentGroup = storage.group(),
        events = repository.events() + storage.events(),
        eventTypes = listOf(
            EventType("meeting", "Созвон команды", 0xFF6576DF, defaultReminders = setOf(60, 30)),
            EventType("study", "Учёба", 0xFFDA8A37, defaultReminders = setOf(1440)),
            EventType("personal", "Личное", 0xFF38A585),
        ) + storage.types(),
    ))
        private set

    private val createdPasswords = mutableMapOf<String, String>()

    fun lessons(group: String) = repository.lessons(group)

    fun setTheme(theme: AppTheme) {
        storage.saveTheme(theme)
        uiState = uiState.copy(theme = theme)
    }

    fun setStudentGroup(group: String) {
        if (group !in setOf("203", "204")) return
        storage.saveGroup(group)
        uiState = uiState.copy(studentGroup = group)
    }

    fun visibleEvents(): List<TeamEvent> {
        val user = uiState.user ?: return emptyList()
        return uiState.events.filter { event ->
            when (event.visibility) {
                EventVisibility.TEAM -> event.teamId == uiState.selectedTeamId
                EventVisibility.GROUP -> event.group == uiState.studentGroup
                EventVisibility.PERSONAL -> event.ownerId == user.id
            }
        }
    }

    fun addEventType(title: String, color: Long, defaultReminders: Set<Int> = emptySet()): EventType? {
        val normalized = title.trim()
        if (normalized.isEmpty() || uiState.eventTypes.any { it.title.equals(normalized, true) }) return null
        val type = EventType("custom-${java.util.UUID.randomUUID()}", normalized, color, custom = true, defaultReminders = defaultReminders)
        val updated = uiState.eventTypes + type
        storage.saveTypes(updated)
        uiState = uiState.copy(eventTypes = updated)
        return type
    }

    fun createEvent(title: String, date: String, time: String, visibility: EventVisibility, typeId: String, reminders: Set<Int>): TeamEvent? {
        val user = uiState.user ?: return null
        val startsAt = runCatching { java.time.LocalDateTime.parse("${date}T${time}") }.getOrNull()
        if (title.isBlank() || startsAt == null || !startsAt.isAfter(java.time.LocalDateTime.now()) ||
            uiState.eventTypes.none { it.id == typeId } ||
            visibility == EventVisibility.TEAM && uiState.selectedTeamId == 0L
        ) return null
        val event = TeamEvent(
            id = (uiState.events.maxOfOrNull { it.id } ?: 0) + 1,
            title = title.trim(),
            date = date,
            time = time,
            visibility = visibility,
            group = if (visibility == EventVisibility.GROUP) uiState.studentGroup else null,
            typeId = typeId,
            reminderMinutes = reminders,
            ownerId = user.id,
            teamId = uiState.selectedTeamId,
        )
        val updated = uiState.events + event
        storage.saveEvents(updated.filterNot { old -> repository.events().any { it.id == old.id } })
        uiState = uiState.copy(events = updated)
        return event
    }

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
