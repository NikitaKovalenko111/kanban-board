package com.kanbanboard.mobile.feature.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kanbanboard.mobile.core.data.BoardRepository
import com.kanbanboard.mobile.core.data.ProjectRepository
import com.kanbanboard.mobile.core.data.SessionRepository
import com.kanbanboard.mobile.core.data.TeamRepository
import com.kanbanboard.mobile.core.data.settings.SettingsRepository
import com.kanbanboard.mobile.core.model.BoardColumn
import com.kanbanboard.mobile.core.model.ColumnId
import com.kanbanboard.mobile.core.model.ColumnKind
import com.kanbanboard.mobile.core.model.Project
import com.kanbanboard.mobile.core.model.ProjectId
import com.kanbanboard.mobile.core.model.Session
import com.kanbanboard.mobile.core.model.Task
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

/** Карточка задачи на доске и быстрые действия из меню по долгому нажатию. */
data class TaskCardUi(
    val task: Task,
    val assignee: User?,
    /** «Взять в работу», если задача свободная или назначена на меня. */
    val take: QuickMove.Take?,
    /** Переходы без комментария (возврат на доработку делается из карточки задачи). */
    val moves: List<MoveOption>,
    val isMine: Boolean,
)

data class BoardColumnUi(val column: BoardColumn, val cards: List<TaskCardUi>)

data class BoardUiState(
    val loading: Boolean = true,
    val session: Session? = null,
    val teamName: String = "",
    val projects: List<Project> = emptyList(),
    val project: Project? = null,
    val columns: List<BoardColumnUi> = emptyList(),
    val selectedColumnId: ColumnId? = null,
) {
    val totalTasks: Int get() = columns.sumOf { it.cards.size }
    val doneTasks: Int get() = columns.filter { it.column.kind == ColumnKind.DONE }.sumOf { it.cards.size }
    val selectedIndex: Int get() = columns.indexOfFirst { it.column.id == selectedColumnId }.coerceAtLeast(0)
    val selectedColumn: BoardColumnUi?
        get() = columns.firstOrNull { it.column.id == selectedColumnId } ?: columns.firstOrNull()
    val canCreate: Boolean get() = project?.canViewBoard == true && session?.let { TaskPolicy.canCreateTask(it) } == true
}

private data class BoardData(
    val projects: List<Project>,
    val project: Project?,
    val columns: List<BoardColumn>,
    val tasks: List<Task>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class BoardViewModel(
    private val sessions: SessionRepository,
    team: TeamRepository,
    projects: ProjectRepository,
    private val board: BoardRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val selectedColumnId = MutableStateFlow<ColumnId?>(null)
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private val data: Flow<BoardData> = combine(projects.projects, settings.settings, sessions.session) { list, prefs, session ->
        list to resolveProject(list.filter { it.canViewBoard }, prefs.selectedProjectId, session)
    }.flatMapLatest { (list, project) ->
        if (project == null) {
            flowOf(BoardData(list, null, emptyList(), emptyList()))
        } else {
            combine(board.columns(project.id), board.tasks(project.id)) { columns, tasks ->
                BoardData(list, project, columns, tasks)
            }
        }
    }

    val state: StateFlow<BoardUiState> = combine(
        data,
        sessions.session,
        team.members,
        team.team,
        selectedColumnId,
    ) { data, session, members, teamInfo, selected ->
        val users = members.associate { it.user.id to it.user }
        BoardUiState(
            loading = false,
            session = session,
            teamName = teamInfo.name,
            projects = data.projects,
            project = data.project,
            columns = buildColumns(data, session, users),
            selectedColumnId = selected?.takeIf { id -> data.columns.any { it.id == id } }
                ?: defaultColumn(data.columns),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BoardUiState())

    private fun resolveProject(list: List<Project>, selected: ProjectId?, session: Session?): Project? =
        list.firstOrNull { it.id == selected }
            ?: list.firstOrNull { session != null && it.isMember(session.user.id) }
            ?: list.firstOrNull()

    private fun defaultColumn(columns: List<BoardColumn>): ColumnId? =
        (columns.firstOrNull { it.kind == ColumnKind.IN_PROGRESS } ?: columns.firstOrNull())?.id

    private fun buildColumns(data: BoardData, session: Session?, users: Map<UserId, User>): List<BoardColumnUi> =
        data.columns.map { column ->
            val inColumn = data.tasks.filter { it.columnId == column.id }
            val ordered = if (column.kind == ColumnKind.DONE) {
                inColumn.sortedByDescending { it.completedAt }
            } else {
                inColumn.sortedBy { it.position }
            }
            BoardColumnUi(
                column = column,
                cards = ordered.map { task ->
                    TaskCardUi(
                        task = task,
                        assignee = task.assigneeId?.let(users::get),
                        take = session?.let { TaskPolicy.quickMove(it, task, data.columns, +1) as? QuickMove.Take },
                        moves = session?.let { s -> TaskPolicy.moveOptions(s, task, data.columns).filter { !it.requiresComment } }.orEmpty(),
                        isMine = session != null && task.assigneeId == session.user.id,
                    )
                },
            )
        }

    fun selectColumn(id: ColumnId) {
        selectedColumnId.value = id
    }

    fun selectProject(id: ProjectId) {
        selectedColumnId.value = null
        viewModelScope.launch { settings.setSelectedProject(id) }
    }

    fun take(card: TaskCardUi) {
        val take = card.take ?: return
        viewModelScope.launch {
            board.takeTask(card.task.id)
                .onSuccess { _messages.trySend("«${card.task.title}» → ${take.target.title}") }
                .onFailure { _messages.trySend(it.message ?: "Не удалось взять задачу") }
        }
    }

    fun move(card: TaskCardUi, option: MoveOption) {
        viewModelScope.launch {
            board.moveTask(card.task.id, option.target.id, null)
                .onSuccess { _messages.trySend("«${card.task.title}» → ${option.target.title}") }
                .onFailure { _messages.trySend(it.message ?: "Не удалось переместить задачу") }
        }
    }
}
