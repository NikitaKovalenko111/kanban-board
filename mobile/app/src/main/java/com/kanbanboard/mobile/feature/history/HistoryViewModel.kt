package com.kanbanboard.mobile.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kanbanboard.mobile.core.data.BoardRepository
import com.kanbanboard.mobile.core.data.ProjectRepository
import com.kanbanboard.mobile.core.data.TeamRepository
import com.kanbanboard.mobile.core.model.Project
import com.kanbanboard.mobile.core.model.ProjectId
import com.kanbanboard.mobile.core.model.Task
import com.kanbanboard.mobile.core.model.TaskAction
import com.kanbanboard.mobile.core.model.User
import com.kanbanboard.mobile.core.model.UserId
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class HistoryPeriod(val title: String, val days: Long?) {
    WEEK("Неделя", 7),
    MONTH("Месяц", 30),
    ALL("Всё время", null),
}

data class HistoryFilters(
    val period: HistoryPeriod = HistoryPeriod.WEEK,
    val projectId: ProjectId? = null,
    val assigneeId: UserId? = null,
    val query: String = "",
)

data class HistoryRow(
    val task: Task,
    val project: Project?,
    val assignee: User?,
    val completedAt: Instant,
    /** Сколько раз задачу возвращали на доработку. */
    val returns: Int,
    /** От взятия в работу до принятия. */
    val cycle: Duration?,
)

data class HistoryStats(val done: Int, val returns: Int, val averageCycle: Duration?)

data class HistoryUiState(
    val loading: Boolean = true,
    val filters: HistoryFilters = HistoryFilters(),
    val projects: List<Project> = emptyList(),
    val members: List<User> = emptyList(),
    val days: List<Pair<LocalDate, List<HistoryRow>>> = emptyList(),
    val stats: HistoryStats = HistoryStats(0, 0, null),
)

class HistoryViewModel(
    board: BoardRepository,
    projects: ProjectRepository,
    team: TeamRepository,
) : ViewModel() {

    private val filters = MutableStateFlow(HistoryFilters())

    val state: StateFlow<HistoryUiState> = combine(
        board.completedTasks(),
        projects.projects,
        team.members,
        filters,
    ) { tasks, projectList, members, f ->
        val users = members.associate { it.user.id to it.user }
        val projectsById = projectList.associateBy { it.id }
        val now = Instant.now()
        val from = f.period.days?.let { now.minus(Duration.ofDays(it)) }
        val query = f.query.trim()
        val rows = tasks.mapNotNull { task ->
            // Время выполнения — completedAt; если его нет, берём последнее «принята» из аудита.
            val completedAt = task.completedAt
                ?: task.history.lastOrNull { it.action == TaskAction.ACCEPTED }?.at
                ?: return@mapNotNull null
            if (from != null && completedAt.isBefore(from)) return@mapNotNull null
            if (f.projectId != null && task.projectId != f.projectId) return@mapNotNull null
            if (f.assigneeId != null && task.assigneeId != f.assigneeId) return@mapNotNull null
            val assignee = task.assigneeId?.let { users[it] }
            if (query.isNotEmpty() &&
                !"${task.title} ${task.description} ${assignee?.fullName.orEmpty()}".contains(query, ignoreCase = true)
            ) return@mapNotNull null
            val taken = task.history.firstOrNull { it.action == TaskAction.TAKEN }?.at
            HistoryRow(
                task = task,
                project = projectsById[task.projectId],
                assignee = assignee,
                completedAt = completedAt,
                returns = task.history.count { it.action == TaskAction.RETURNED },
                cycle = taken?.let { Duration.between(it, completedAt) }?.takeIf { !it.isNegative },
            )
        }.sortedByDescending { it.completedAt }

        val zone = ZoneId.systemDefault()
        val cycles = rows.mapNotNull { it.cycle }
        HistoryUiState(
            loading = false,
            filters = f,
            projects = projectList,
            members = members.map { it.user },
            days = rows.groupBy { it.completedAt.atZone(zone).toLocalDate() }.toList(),
            stats = HistoryStats(
                done = rows.size,
                returns = rows.sumOf { it.returns },
                averageCycle = if (cycles.isEmpty()) null else Duration.ofSeconds(cycles.sumOf { it.seconds } / cycles.size),
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setPeriod(period: HistoryPeriod) {
        filters.value = filters.value.copy(period = period)
    }

    fun setProject(id: ProjectId?) {
        filters.value = filters.value.copy(projectId = id)
    }

    fun setAssignee(id: UserId?) {
        filters.value = filters.value.copy(assigneeId = id)
    }

    fun setQuery(query: String) {
        filters.value = filters.value.copy(query = query.take(100))
    }
}

/** «2 д 4 ч», «5 ч», «40 мин». */
fun Duration.shortLabel(): String {
    val days = toDays()
    val hours = toHours() % 24
    val minutes = toMinutes() % 60
    return when {
        days > 0 -> if (hours > 0) "$days д $hours ч" else "$days д"
        hours > 0 -> "$hours ч"
        else -> "$minutes мин"
    }
}
