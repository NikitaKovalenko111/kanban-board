package com.kanbanboard.mobile.feature.task

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kanbanboard.mobile.core.data.BoardRepository
import com.kanbanboard.mobile.core.data.ProjectRepository
import com.kanbanboard.mobile.core.data.TeamRepository
import com.kanbanboard.mobile.core.model.BoardColumn
import com.kanbanboard.mobile.core.model.ColumnId
import com.kanbanboard.mobile.core.model.ColumnKind
import com.kanbanboard.mobile.core.model.ProjectId
import com.kanbanboard.mobile.core.model.TaskDraft
import com.kanbanboard.mobile.core.model.TaskId
import com.kanbanboard.mobile.core.model.TaskPriority
import com.kanbanboard.mobile.core.model.User
import com.kanbanboard.mobile.core.model.UserId
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Участник проекта для выбора исполнителя. */
data class AssigneeOption(val user: User, val projectRole: String)

data class TaskEditorUiState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val projectName: String = "",
    val title: String = "",
    val description: String = "",
    val priority: TaskPriority = TaskPriority.NORMAL,
    val assigneeId: UserId? = null,
    val columnId: ColumnId? = null,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime = DEFAULT_DUE_TIME,
    val assignees: List<AssigneeOption> = emptyList(),
    val columns: List<BoardColumn> = emptyList(),
    val saving: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean get() = !loading && !saving && title.isNotBlank()
    fun assigneeName(): String = assignees.firstOrNull { it.user.id == assigneeId }?.user?.fullName ?: "Без исполнителя"

    companion object {
        val DEFAULT_DUE_TIME: LocalTime = LocalTime.of(18, 0)
    }
}

class TaskEditorViewModel(
    private val projectId: ProjectId,
    private val taskId: TaskId?,
    private val team: TeamRepository,
    private val projects: ProjectRepository,
    private val board: BoardRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TaskEditorUiState(isEdit = taskId != null))
    val state: StateFlow<TaskEditorUiState> = _state.asStateFlow()

    private val _saved = Channel<Unit>(Channel.CONFLATED)
    val saved: Flow<Unit> = _saved.receiveAsFlow()

    init {
        viewModelScope.launch { load() }
    }

    /** Форма загружается один раз: последующие изменения на сервере не перетирают ввод пользователя. */
    private suspend fun load() {
        val project = projects.projects.first().firstOrNull { it.id == projectId }
        if (project == null) {
            _state.update { it.copy(loading = false, error = "Проект не найден") }
            return
        }
        val members = team.members.first()
        val columns = board.columns(projectId).first()
        val options = members
            .filter { project.isMember(it.user.id) && it.user.isActive }
            .map { AssigneeOption(it.user, project.members[it.user.id].orEmpty()) }
            .sortedBy { it.user.fullName }
        val task = taskId?.let { id -> board.task(id).first() }
        if (taskId != null && task == null) {
            _state.update { it.copy(loading = false, error = "Задача не найдена") }
            return
        }
        val zone = ZoneId.systemDefault()
        _state.update { current ->
            current.copy(
                loading = false,
                projectName = project.name,
                assignees = options,
                columns = columns,
                title = task?.title.orEmpty(),
                description = task?.description.orEmpty(),
                priority = task?.priority ?: TaskPriority.NORMAL,
                assigneeId = task?.assigneeId,
                columnId = task?.columnId ?: columns.firstOrNull { it.kind == ColumnKind.BACKLOG }?.id,
                dueDate = task?.dueAt?.atZone(zone)?.toLocalDate(),
                dueTime = task?.dueAt?.atZone(zone)?.toLocalTime()?.withSecond(0)?.withNano(0)
                    ?: TaskEditorUiState.DEFAULT_DUE_TIME,
            )
        }
    }

    fun setTitle(value: String) = _state.update { it.copy(title = value.take(200), error = null) }
    fun setDescription(value: String) = _state.update { it.copy(description = value.take(5000)) }
    fun setPriority(value: TaskPriority) = _state.update { it.copy(priority = value) }
    fun setAssignee(value: UserId?) = _state.update { it.copy(assigneeId = value) }
    fun setColumn(value: ColumnId) = _state.update { it.copy(columnId = value) }
    fun setDueDate(value: LocalDate?) = _state.update { it.copy(dueDate = value) }
    fun setDueTime(value: LocalTime) = _state.update { it.copy(dueTime = value) }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(saving = true, error = null) }
        val draft = TaskDraft(
            title = s.title,
            description = s.description,
            priority = s.priority,
            assigneeId = s.assigneeId,
            dueAt = s.dueDate?.atTime(s.dueTime)?.atZone(ZoneId.systemDefault())?.toInstant(),
            columnId = s.columnId,
        )
        viewModelScope.launch {
            val result = if (taskId == null) board.createTask(projectId, draft) else board.updateTask(taskId, draft)
            result
                .onSuccess { _saved.trySend(Unit) }
                .onFailure { e -> _state.update { it.copy(saving = false, error = e.message) } }
        }
    }
}
