package com.kanbanboard.mobile.feature.task

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kanbanboard.mobile.core.data.BoardRepository
import com.kanbanboard.mobile.core.data.ProjectRepository
import com.kanbanboard.mobile.core.data.SessionRepository
import com.kanbanboard.mobile.core.data.TeamRepository
import com.kanbanboard.mobile.core.model.AttachmentKind
import com.kanbanboard.mobile.core.model.BoardColumn
import com.kanbanboard.mobile.core.model.ColumnId
import com.kanbanboard.mobile.core.model.PickedFile
import com.kanbanboard.mobile.core.model.Session
import com.kanbanboard.mobile.core.model.Task
import com.kanbanboard.mobile.core.model.TaskId
import com.kanbanboard.mobile.core.model.User
import com.kanbanboard.mobile.core.model.UserId
import com.kanbanboard.mobile.core.policy.MoveOption
import com.kanbanboard.mobile.core.policy.QuickMove
import com.kanbanboard.mobile.core.policy.TaskPolicy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TaskDetailUiState(
    val loading: Boolean = true,
    val session: Session? = null,
    val task: Task? = null,
    val column: BoardColumn? = null,
    val members: Map<UserId, User> = emptyMap(),
    val moveOptions: List<MoveOption> = emptyList(),
    val primary: QuickMove? = null,
    val canEdit: Boolean = false,
    val canDelete: Boolean = false,
    val canUploadSource: Boolean = false,
    val canUploadResult: Boolean = false,
    val busy: Boolean = false,
    val projectName: String = "",
    val canAssignToSelf: Boolean = false,
) {
    fun canRemove(attachment: com.kanbanboard.mobile.core.model.Attachment): Boolean {
        val s = session ?: return false
        val c = column ?: return false
        return TaskPolicy.canRemoveAttachment(s, attachment, c)
    }

    val returnOptions: List<MoveOption> get() = moveOptions.filter { it.requiresComment }
    fun name(id: UserId?): String = id?.let { members[it]?.fullName ?: "Удалённый участник" } ?: "—"
}

sealed interface TaskDetailEvent {
    data class Message(val text: String) : TaskDetailEvent
    data object Deleted : TaskDetailEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class TaskDetailViewModel(
    private val taskId: TaskId,
    sessions: SessionRepository,
    team: TeamRepository,
    private val board: BoardRepository,
    projects: ProjectRepository,
) : ViewModel() {

    private val busy = MutableStateFlow(false)
    private val _events = Channel<TaskDetailEvent>(Channel.BUFFERED)
    val events: Flow<TaskDetailEvent> = _events.receiveAsFlow()

    private data class Loaded(val task: Task?, val columns: List<BoardColumn>, val projectName: String)

    private val taskWithColumns: Flow<Loaded> = board.task(taskId).flatMapLatest { task ->
        if (task == null) flowOf(Loaded(null, emptyList(), ""))
        else combine(board.columns(task.projectId), projects.project(task.projectId)) { columns, project ->
            Loaded(task, columns, project?.name.orEmpty())
        }
    }

    val state: StateFlow<TaskDetailUiState> = combine(
        taskWithColumns,
        sessions.session,
        team.members,
        busy,
    ) { (task, columns, projectName), session, members, isBusy ->
        val column = task?.let { t -> columns.firstOrNull { it.id == t.columnId } }
        val users = members.associate { it.user.id to it.user }
        if (task == null || column == null || session == null) {
            TaskDetailUiState(loading = false, session = session, members = users, busy = isBusy)
        } else {
            TaskDetailUiState(
                loading = false,
                session = session,
                task = task,
                column = column,
                members = users,
                moveOptions = TaskPolicy.moveOptions(session, task, columns),
                primary = TaskPolicy.quickMove(session, task, columns, +1),
                canEdit = TaskPolicy.canEditTask(session),
                canDelete = TaskPolicy.canDeleteTask(session),
                canUploadSource = TaskPolicy.canUpload(session, task, column, AttachmentKind.SOURCE),
                canUploadResult = TaskPolicy.canUpload(session, task, column, AttachmentKind.RESULT),
                busy = isBusy,
                projectName = projectName,
                canAssignToSelf = TaskPolicy.canAssignToSelf(session, task, column),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskDetailUiState())

    /** Одна операция за раз: повторные нажатия, пока идёт запрос, игнорируются (MOB-05). */
    private fun perform(successMessage: String?, onSuccess: () -> Unit = {}, action: suspend () -> Result<*>) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            action()
                .onSuccess {
                    onSuccess()
                    successMessage?.let { _events.trySend(TaskDetailEvent.Message(it)) }
                }
                .onFailure { _events.trySend(TaskDetailEvent.Message(it.message ?: "Ошибка")) }
            busy.value = false
        }
    }

    fun primaryAction() {
        val move = state.value.primary ?: return
        when (move) {
            is QuickMove.Take -> perform("Задача взята в работу") { board.takeTask(taskId) }
            is QuickMove.Move -> perform("Перемещено: ${move.target.title}") { board.moveTask(taskId, move.target.id, null) }
        }
    }

    fun move(columnId: ColumnId, comment: String?) {
        val target = state.value.moveOptions.firstOrNull { it.target.id == columnId }?.target ?: return
        perform("Перемещено: ${target.title}") { board.moveTask(taskId, columnId, comment) }
    }

    fun addComment(text: String, onSent: () -> Unit) {
        if (text.isBlank()) return
        perform(null, onSuccess = onSent) { board.addComment(taskId, text) }
    }

    /** Файл уже скопирован в хранилище приложения ([com.kanbanboard.mobile.core.files.FileStore]). */
    fun addAttachment(file: PickedFile, kind: AttachmentKind) {
        perform(if (kind == AttachmentKind.RESULT) "Результат прикреплён" else "Файл прикреплён") {
            board.addAttachment(taskId, file, kind)
        }
    }

    /** BUG-08: капитан явно переназначает задачу на себя, чтобы сдать её самому. */
    fun assignToSelf() {
        perform("Задача назначена на вас") { board.assignToSelf(taskId) }
    }

    fun removeAttachment(id: String) {
        perform("Файл удалён") { board.removeAttachment(taskId, id) }
    }

    fun report(text: String) {
        _events.trySend(TaskDetailEvent.Message(text))
    }

    fun delete() {
        perform(null, onSuccess = { _events.trySend(TaskDetailEvent.Deleted) }) { board.deleteTask(taskId) }
    }
}
