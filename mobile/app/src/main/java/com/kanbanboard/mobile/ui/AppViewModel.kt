package com.kanbanboard.mobile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.kanbanboard.mobile.data.FakeKanbanRepository
import com.kanbanboard.mobile.data.KanbanRepository
import com.kanbanboard.mobile.data.AppStorage
import com.kanbanboard.mobile.data.MemoryAppStorage
import com.kanbanboard.mobile.data.RemoteWorkspaceGateway
import com.kanbanboard.mobile.model.AppTheme
import com.kanbanboard.mobile.model.EventType
import com.kanbanboard.mobile.model.EventVisibility
import com.kanbanboard.mobile.model.TeamEvent
import com.kanbanboard.mobile.model.Comment
import com.kanbanboard.mobile.model.Task
import com.kanbanboard.mobile.model.TaskHistoryEntry
import com.kanbanboard.mobile.model.TaskStatus
import com.kanbanboard.mobile.model.TeamSettings
import com.kanbanboard.mobile.model.User
import com.kanbanboard.mobile.model.UserRole
import com.kanbanboard.mobile.model.LoginCredentials
import com.kanbanboard.mobile.model.Team
import com.kanbanboard.mobile.model.Project
import java.time.OffsetDateTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kanbanboard.mobile.model.Lesson
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream

data class AppUiState(
    val user: User? = null,
    val tasks: List<Task> = emptyList(),
    val members: List<User> = emptyList(),
    val teamSettings: TeamSettings = TeamSettings("", ""),
    val teams: List<Team> = emptyList(),
    val selectedTeamId: Long = 1,
    val projects: List<Project> = emptyList(),
    val selectedProjectId: Long = 1,
    val loginError: String? = null,
    val theme: AppTheme = AppTheme.LIGHT,
    val studentGroup: String = "203",
    val events: List<TeamEvent> = emptyList(),
    val eventTypes: List<EventType> = emptyList(),
    val lessons: List<Lesson> = emptyList(),
    val loginLoading: Boolean = false,
    val apiError: String? = null,
)

class AppViewModel(
    private val repository: KanbanRepository = FakeKanbanRepository(),
    private val storage: AppStorage = MemoryAppStorage(),
    private val remote: RemoteWorkspaceGateway? = null,
) : ViewModel() {
    var uiState by mutableStateOf(AppUiState(
        tasks = if (remote == null) repository.tasks() else emptyList(),
        members = if (remote == null) repository.members() else emptyList(),
        teamSettings = repository.teamSettings(),
        teams = if (remote == null) repository.teams() else emptyList(),
        projects = if (remote == null) repository.projects() else emptyList(),
        theme = storage.theme(),
        studentGroup = storage.group(),
        events = if (remote == null) repository.events() + storage.events() else emptyList(),
        eventTypes = listOf(
            EventType("meeting", "Созвон команды", 0xFF6576DF, defaultReminders = setOf(60, 30)),
            EventType("study", "Учёба", 0xFFDA8A37, defaultReminders = setOf(1440)),
            EventType("personal", "Личное", 0xFF38A585),
        ) + if (remote == null) storage.types() else emptyList(),
    ))
        private set

    private val createdPasswords = mutableMapOf<String, String>()

    fun clearApiError() { uiState = uiState.copy(apiError = null) }

    private fun remoteFailure(error: Throwable, message: String) {
        if (error is retrofit2.HttpException && error.code() == 401) {
            remote?.logout()
            uiState = uiState.copy(user = null, loginError = "Сессия истекла. Войдите снова", apiError = null)
        } else uiState = uiState.copy(apiError = message)
    }

    fun lessons(group: String) = if (remote == null) repository.lessons(group) else uiState.lessons.ifEmpty { storage.lessons(group) }

    fun setTheme(theme: AppTheme) {
        storage.saveTheme(theme)
        uiState = uiState.copy(theme = theme)
    }

    fun setStudentGroup(group: String) {
        if (group !in setOf("203", "204")) return
        storage.saveGroup(group)
        uiState = uiState.copy(studentGroup = group)
        if (remote != null) viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { remote.lessons(group) } }
                .onSuccess { storage.saveLessons(group, it); uiState = uiState.copy(lessons = it) }
                .onFailure { uiState = uiState.copy(lessons = storage.lessons(group)); remoteFailure(it, "Показано сохранённое расписание") }
        }
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
        if (remote != null) return null
        val normalized = title.trim()
        if (normalized.isEmpty() || uiState.eventTypes.any { it.title.equals(normalized, true) }) return null
        val type = EventType("custom-${java.util.UUID.randomUUID()}", normalized, color, custom = true, defaultReminders = defaultReminders)
        val updated = uiState.eventTypes + type
        storage.saveTypes(updated)
        uiState = uiState.copy(eventTypes = updated)
        return type
    }

    fun submitEventType(title: String, color: Long, reminders: Set<Int>, onResult: (EventType?) -> Unit) {
        if (remote == null) { onResult(addEventType(title, color, reminders)); return }
        if (title.isBlank() || uiState.eventTypes.any { it.title.equals(title.trim(), true) }) { onResult(null); return }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { remote.createEventType(title.trim(), color, reminders) } }
                .onSuccess { saved -> uiState = uiState.copy(eventTypes = uiState.eventTypes + saved); onResult(saved) }
                .onFailure { remoteFailure(it, "Не удалось создать тип события"); onResult(null) }
        }
    }

    fun updateEventType(id: String, title: String, color: Long): Boolean {
        if (remote != null) return false
        val normalized = title.trim()
        val current = uiState.eventTypes.firstOrNull { it.id == id && it.custom } ?: return false
        if (normalized.isEmpty() || uiState.eventTypes.any { it.id != id && it.title.equals(normalized, true) }) return false
        val updated = uiState.eventTypes.map { if (it.id == id) current.copy(title = normalized, color = color) else it }
        storage.saveTypes(updated)
        uiState = uiState.copy(eventTypes = updated)
        return true
    }

    fun submitUpdateEventType(id: String, title: String, color: Long, onResult: (Boolean) -> Unit) {
        if (remote == null) { onResult(updateEventType(id, title, color)); return }
        val current = uiState.eventTypes.firstOrNull { it.id == id && it.custom }
        if (current == null || title.isBlank()) { onResult(false); return }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { remote.updateEventType(id, title.trim(), color, current.defaultReminders) } }
                .onSuccess { saved -> uiState = uiState.copy(eventTypes = uiState.eventTypes.map { if (it.id == id) saved else it }); onResult(true) }
                .onFailure { remoteFailure(it, "Не удалось изменить тип события"); onResult(false) }
        }
    }

    fun createEvent(title: String, date: String, time: String, visibility: EventVisibility, typeId: String, reminders: Set<Int>, endTime: String = java.time.LocalTime.parse(time).plusHours(1).toString(), description: String = "", location: String = ""): TeamEvent? {
        val user = uiState.user ?: return null
        val startsAt = runCatching { java.time.LocalDateTime.parse("${date}T${time}") }.getOrNull()
        val endsAt = runCatching { java.time.LocalDateTime.parse("${date}T${endTime}") }.getOrNull()
        if (title.isBlank() || startsAt == null || endsAt == null || !endsAt.isAfter(startsAt) || !startsAt.isAfter(java.time.LocalDateTime.now()) || reminders.any { it <= 0 } ||
            uiState.eventTypes.none { it.id == typeId } ||
            visibility == EventVisibility.TEAM && uiState.selectedTeamId == 0L
        ) return null
        val event = TeamEvent(
            id = (uiState.events.maxOfOrNull { it.id } ?: 0) + 1,
            title = title.trim(),
            date = date,
            time = time,
            endTime = endTime,
            description = description.trim(),
            location = location.trim(),
            visibility = visibility,
            group = if (visibility == EventVisibility.GROUP) uiState.studentGroup else null,
            typeId = typeId,
            reminderMinutes = reminders,
            ownerId = user.id,
            teamId = uiState.selectedTeamId,
        )
        val updated = uiState.events + event
        if (remote == null) storage.saveEvents(updated.filterNot { old -> repository.events().any { it.id == old.id } })
        uiState = uiState.copy(events = updated)
        return event
    }

    fun submitEvent(title: String, date: String, time: String, visibility: EventVisibility, typeId: String,
        reminders: Set<Int>, endTime: String, description: String, location: String, onResult: (TeamEvent?) -> Unit) {
        val draft = createEvent(title, date, time, visibility, typeId, reminders, endTime, description, location)
        if (draft == null || remote == null) { onResult(draft); return }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { remote.createEvent(draft) } }
                .onSuccess { saved ->
                    uiState = uiState.copy(events = uiState.events.map { if (it === draft) saved else it })
                    onResult(saved)
                }.onFailure {
                    uiState = uiState.copy(events = uiState.events.filterNot { event -> event === draft })
                    remoteFailure(it, "Не удалось сохранить событие")
                    onResult(null)
                }
        }
    }

    fun login(login: String, password: String) {
        if (remote != null) {
            if (uiState.loginLoading) return
            uiState = uiState.copy(loginLoading = true, loginError = null)
            viewModelScope.launch {
                runCatching { withContext(Dispatchers.IO) { remote.loginAndLoad(login.trim(), password) } }
                    .onSuccess { workspace ->
                        storage.saveLessons(workspace.studentGroup, workspace.lessons)
                        uiState = uiState.copy(user = workspace.user, tasks = workspace.tasks, members = workspace.members,
                            teams = workspace.teams, projects = workspace.projects, events = workspace.events,
                            eventTypes = workspace.eventTypes, lessons = workspace.lessons,
                            selectedTeamId = workspace.selectedTeamId, selectedProjectId = workspace.selectedProjectId,
                            studentGroup = workspace.studentGroup, loginLoading = false, loginError = null)
                    }.onFailure {
                        uiState = uiState.copy(loginLoading = false, loginError = "Не удалось войти или загрузить данные с сервера")
                    }
            }
            return
        }
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
        remote?.logout()
        uiState = uiState.copy(user = null, loginError = null)
    }

    fun moveTask(taskId: Long, status: TaskStatus, comment: String = "") {
        val user = uiState.user ?: return
        val currentTask = uiState.tasks.firstOrNull { it.id == taskId } ?: return
        val isAssignee = currentTask.assigneeId == user.id || currentTask.assignee == user.name
        val allowed = when {
            isAssignee && currentTask.status == TaskStatus.WAITING && status == TaskStatus.WORKING -> true
            isAssignee && currentTask.status == TaskStatus.WORKING && status == TaskStatus.CHECKING -> true
            user.role == UserRole.LEAD && currentTask.status == TaskStatus.CHECKING && status == TaskStatus.DONE -> true
            user.role == UserRole.LEAD && currentTask.status == TaskStatus.CHECKING && status == TaskStatus.WORKING && comment.isNotBlank() -> true
            user.role == UserRole.LEAD && currentTask.status == TaskStatus.DONE && status == TaskStatus.WORKING && comment.isNotBlank() -> true
            else -> false
        }
        if (!allowed) return
        if (remote != null) {
            viewModelScope.launch {
                runCatching { withContext(Dispatchers.IO) { remote.moveTask(taskId, status, comment) } }
                    .onSuccess { saved -> uiState = uiState.copy(tasks = uiState.tasks.map { if (it.id == taskId) saved else it }) }
                    .onFailure { remoteFailure(it, "Сервер отклонил смену статуса") }
            }
            return
        }
        uiState = uiState.copy(tasks = uiState.tasks.map { task ->
            if (task.id == taskId) task.copy(
                status = status,
                completedAt = if (status == TaskStatus.DONE) LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) else null,
                history = task.history + TaskHistoryEntry(
                    id = UUID.randomUUID().toString(),
                    action = when (status) {
                        TaskStatus.WORKING -> if (comment.isNotBlank()) "Возврат на доработку" else "Взята в работу"
                        TaskStatus.CHECKING -> "Сдана на проверку"
                        TaskStatus.DONE -> "Выполнена"
                        TaskStatus.WAITING -> "Возвращена в ожидание"
                    },
                    actorId = user.id,
                    actorName = user.name,
                    occurredAt = OffsetDateTime.now().toString(),
                    comment = comment.trim().ifBlank { null },
                ),
            ) else task
        })
    }

    fun loadTaskHistory(taskId: Long) {
        if (remote == null) return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { remote.history(taskId) } }
                .onSuccess { history -> uiState = uiState.copy(tasks = uiState.tasks.map { task ->
                    if (task.id == taskId) task.copy(history = history) else task
                }) }
                .onFailure { remoteFailure(it, "Не удалось загрузить историю задачи") }
        }
    }

    fun takeTask(taskId: Long) {
        val user = uiState.user ?: return
        if (remote != null) {
            viewModelScope.launch {
                runCatching { withContext(Dispatchers.IO) { remote.takeTask(taskId) } }
                    .onSuccess { saved -> uiState = uiState.copy(tasks = uiState.tasks.map { if (it.id == taskId) saved else it }) }
                    .onFailure { remoteFailure(it, "Не удалось взять задачу") }
            }
            return
        }
        uiState = uiState.copy(tasks = uiState.tasks.map { task ->
            if (task.id != taskId || task.status != TaskStatus.WAITING || task.assignee != "Не назначен") task
            else task.copy(assignee = user.name, assigneeId = user.id, status = TaskStatus.WORKING,
                history = task.history + TaskHistoryEntry(UUID.randomUUID().toString(), "Взята в работу", user.id, user.name, OffsetDateTime.now().toString()))
        })
    }

    fun addComment(taskId: Long, text: String) {
        val normalized = text.trim()
        if (normalized.isEmpty()) return
        if (remote != null) {
            viewModelScope.launch {
                runCatching { withContext(Dispatchers.IO) { remote.addComment(taskId, normalized) } }
                    .onSuccess { saved -> uiState = uiState.copy(tasks = uiState.tasks.map { task ->
                        if (task.id == taskId) task.copy(comments = task.comments + saved) else task
                    }) }
                    .onFailure { remoteFailure(it, "Не удалось отправить комментарий") }
            }
            return
        }
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

    fun uploadAttachment(taskId: Long, uri: Uri, context: Context, result: Boolean) {
        if (remote == null) { uiState = uiState.copy(apiError = "Для загрузки файлов нужен backend API"); return }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val resolver = context.applicationContext.contentResolver
                val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: "attachment"
                val bytes = resolver.openInputStream(uri)?.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 10_000_000) { "Файл больше 10 МБ" }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } ?: error("Файл недоступен")
                remote.uploadAttachment(taskId, name, bytes, result)
            } }.onSuccess { saved -> uiState = uiState.copy(tasks = uiState.tasks.map { task ->
                if (task.id == taskId) {
                    if (result) task.copy(resultAttachments = task.resultAttachments + saved)
                    else task.copy(attachments = task.attachments + saved)
                } else task
            }) }.onFailure { remoteFailure(it, "Не удалось загрузить файл (лимит 10 МБ)") }
        }
    }

    fun downloadAttachment(attachmentId: String, uri: Uri, context: Context) {
        if (remote == null) { uiState = uiState.copy(apiError = "Скачивание доступно после подключения backend API"); return }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) {
                context.applicationContext.contentResolver.openOutputStream(uri)?.use { output ->
                    remote.downloadAttachment(attachmentId, output)
                } ?: error("Не удалось открыть файл для сохранения")
            } }.onFailure { remoteFailure(it, "Не удалось скачать вложение") }
        }
    }

    fun createTask(title: String, description: String, deadline: String, deadlineTime: String = "23:59") {
        val normalizedTitle = title.trim()
        if (normalizedTitle.isEmpty() || description.trim().isEmpty()) return
        val dueAt = runCatching {
            LocalDate.parse(deadline, DateTimeFormatter.ofPattern("dd.MM.yyyy")).atTime(java.time.LocalTime.parse(deadlineTime)).atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime().toString()
        }.getOrNull()
        if (remote != null) {
            val projectId = uiState.selectedProjectId
            viewModelScope.launch {
                runCatching { withContext(Dispatchers.IO) { remote.createTask(projectId, normalizedTitle, description.trim(), uiState.user?.id, dueAt) } }
                    .onSuccess { saved -> uiState = uiState.copy(tasks = listOf(saved) + uiState.tasks) }
                    .onFailure { remoteFailure(it, "Не удалось создать задачу") }
            }
            return
        }
        val task = Task(
            id = (uiState.tasks.maxOfOrNull { it.id } ?: 100) + 1,
            title = normalizedTitle,
            description = description.trim(),
            status = TaskStatus.WAITING,
            assignee = uiState.user?.name ?: "Не назначен",
            deadline = deadline.ifBlank { "Без срока" },
            dueAt = dueAt,
            teamId = uiState.selectedTeamId,
            projectId = uiState.selectedProjectId,
            assigneeId = uiState.user?.id,
        )
        uiState = uiState.copy(tasks = listOf(task) + uiState.tasks)
    }

    fun updateTeam(name: String, description: String) {
        if (uiState.user?.role != UserRole.LEAD) return
        if (remote != null) {
            val teamId = uiState.selectedTeamId
            val userId = uiState.user?.id ?: return
            viewModelScope.launch {
                runCatching { withContext(Dispatchers.IO) { remote.updateTeam(teamId, name.trim(), description.trim(), userId) } }
                    .onSuccess { saved -> uiState = uiState.copy(teams = uiState.teams.map { if (it.id == teamId) saved else it },
                        teamSettings = TeamSettings(saved.name, saved.description)) }
                    .onFailure { remoteFailure(it, "Не удалось изменить команду") }
            }
            return
        }
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
        if (remote != null) {
            val current = uiState.members.firstOrNull { it.id == memberId } ?: return
            viewModelScope.launch {
                runCatching { withContext(Dispatchers.IO) { remote.setMemberAccess(uiState.selectedTeamId, memberId, !current.active) } }
                    .onSuccess { saved -> uiState = uiState.copy(members = uiState.members.map { if (it.id == memberId) saved else it }) }
                    .onFailure { remoteFailure(it, "Не удалось изменить доступ участника") }
            }
            return
        }
        uiState = uiState.copy(members = uiState.members.map { member ->
            if (member.id == memberId) member.copy(active = !member.active) else member
        })
    }

    fun addMember(name: String, login: String, temporaryPassword: String, position: String, role: UserRole) {
        if (uiState.user?.role != UserRole.LEAD || name.isBlank() || login.isBlank() || temporaryPassword.length < 6 || position.isBlank()) return
        val normalizedLogin = login.trim().lowercase()
        if (uiState.members.any { it.login == normalizedLogin }) return
        if (remote != null) {
            val teamId = uiState.selectedTeamId
            viewModelScope.launch {
                runCatching { withContext(Dispatchers.IO) { remote.addMember(teamId, name.trim(), normalizedLogin, temporaryPassword, position.trim(), role) } }
                    .onSuccess { saved -> uiState = uiState.copy(members = uiState.members + saved,
                        teams = uiState.teams.map { if (it.id == teamId) it.copy(memberIds = it.memberIds + saved.id) else it }) }
                    .onFailure { remoteFailure(it, "Не удалось добавить участника") }
            }
            return
        }
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
            selectedProjectId = uiState.projects.firstOrNull { it.teamId == team.id }?.id ?: 0,
            teamSettings = TeamSettings(team.name, team.description),
        )
        if (remote != null) {
            val projectId = uiState.selectedProjectId
            uiState = uiState.copy(tasks = emptyList(), members = emptyList(), events = emptyList())
            viewModelScope.launch {
                runCatching { withContext(Dispatchers.IO) {
                    Triple(remote.members(teamId), if (projectId != 0L) remote.board(projectId) else emptyList(),
                        remote.events(teamId, uiState.studentGroup))
                } }.onSuccess { (members, tasks, events) ->
                    uiState = uiState.copy(members = members, tasks = tasks, events = events)
                }.onFailure { remoteFailure(it, "Не удалось загрузить команду") }
            }
        }
    }

    fun joinTeam(teamId: Long) {
        val user = uiState.user ?: return
        val team = uiState.teams.firstOrNull { it.id == teamId } ?: return
        if (remote != null) {
            viewModelScope.launch {
                runCatching { withContext(Dispatchers.IO) {
                    remote.joinTeam(teamId, user.id) to remote.projects(user.id)
                } }.onSuccess { (joined, projects) ->
                    uiState = uiState.copy(teams = uiState.teams.map { if (it.id == teamId) joined else it }, projects = projects)
                    selectTeam(teamId)
                }.onFailure { remoteFailure(it, "Не удалось вступить в команду") }
            }
            return
        }
        uiState = uiState.copy(
            teams = uiState.teams.map { if (it.id == teamId) it.copy(memberIds = it.memberIds + user.id) else it },
            selectedTeamId = teamId,
            selectedProjectId = uiState.projects.firstOrNull { it.teamId == teamId }?.id ?: 0,
            teamSettings = TeamSettings(team.name, team.description),
        )
    }

    fun leaveTeam(teamId: Long) {
        val user = uiState.user ?: return
        if (remote != null) {
            viewModelScope.launch {
                runCatching { withContext(Dispatchers.IO) { remote.leaveTeam(teamId) } }
                    .onSuccess {
                        val teams = uiState.teams.map { team ->
                            if (team.id == teamId) team.copy(memberIds = team.memberIds - user.id) else team
                        }
                        val next = teams.firstOrNull { user.id in it.memberIds }
                        uiState = uiState.copy(teams = teams, selectedTeamId = 0, selectedProjectId = 0,
                            tasks = emptyList(), members = emptyList(), events = emptyList())
                        if (next != null) selectTeam(next.id)
                    }.onFailure { remoteFailure(it, "Не удалось выйти из команды") }
            }
            return
        }
        val updatedTeams = uiState.teams.map { team ->
            if (team.id == teamId) team.copy(memberIds = team.memberIds - user.id) else team
        }
        val nextTeam = updatedTeams.firstOrNull { user.id in it.memberIds }
        uiState = uiState.copy(
            teams = updatedTeams,
            selectedTeamId = if (uiState.selectedTeamId == teamId) nextTeam?.id ?: 0 else uiState.selectedTeamId,
            selectedProjectId = if (uiState.selectedTeamId == teamId) uiState.projects.firstOrNull { it.teamId == nextTeam?.id }?.id ?: 0 else uiState.selectedProjectId,
            teamSettings = if (uiState.selectedTeamId == teamId) {
                nextTeam?.let { TeamSettings(it.name, it.description) } ?: TeamSettings("Команда не выбрана", "Выберите или вступите в команду")
            } else uiState.teamSettings,
        )
    }

    fun selectProject(projectId: Long) {
        if (uiState.projects.none { it.id == projectId && it.teamId == uiState.selectedTeamId }) return
        uiState = uiState.copy(selectedProjectId = projectId)
        if (remote != null) {
            uiState = uiState.copy(tasks = emptyList())
            viewModelScope.launch {
                runCatching { withContext(Dispatchers.IO) { remote.board(projectId) } }
                    .onSuccess { uiState = uiState.copy(tasks = it) }
                    .onFailure { remoteFailure(it, "Не удалось загрузить доску") }
            }
        }
    }

    fun updateProject(projectId: Long, name: String, description: String) {
        if (uiState.user?.role != UserRole.LEAD || name.isBlank()) return
        if (remote != null) {
            val userId = uiState.user?.id ?: return
            viewModelScope.launch {
                runCatching { withContext(Dispatchers.IO) { remote.updateProject(projectId, name.trim(), description.trim(), userId) } }
                    .onSuccess { saved -> uiState = uiState.copy(projects = uiState.projects.map { if (it.id == projectId) saved else it }) }
                    .onFailure { remoteFailure(it, "Не удалось изменить проект") }
            }
            return
        }
        uiState = uiState.copy(projects = uiState.projects.map {
            if (it.id == projectId) it.copy(name = name.trim(), description = description.trim()) else it
        })
    }

    fun setProjectMember(projectId: Long, userId: Long, member: Boolean) {
        if (uiState.user?.role != UserRole.LEAD) return
        if (remote != null) {
            val currentUserId = uiState.user?.id ?: return
            viewModelScope.launch {
                runCatching { withContext(Dispatchers.IO) { remote.setProjectMember(projectId, userId, member, currentUserId) } }
                    .onSuccess { saved -> uiState = uiState.copy(projects = uiState.projects.map { if (it.id == projectId) saved else it }) }
                    .onFailure { remoteFailure(it, "Не удалось изменить состав проекта") }
            }
            return
        }
        val project = uiState.projects.firstOrNull { it.id == projectId } ?: return
        if (uiState.teams.none { it.id == project.teamId && userId in it.memberIds }) return
        uiState = uiState.copy(projects = uiState.projects.map {
            if (it.id == projectId) it.copy(memberIds = if (member) it.memberIds + userId else it.memberIds - userId) else it
        })
    }
}
