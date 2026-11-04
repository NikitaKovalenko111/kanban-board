package com.kanbanboard.mobile.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kanbanboard.mobile.core.designsystem.AppChip
import com.kanbanboard.mobile.core.designsystem.AppTopBar
import com.kanbanboard.mobile.core.designsystem.Caption
import com.kanbanboard.mobile.core.designsystem.EmptyState
import com.kanbanboard.mobile.core.designsystem.Tag
import com.kanbanboard.mobile.core.designsystem.UserAvatar
import com.kanbanboard.mobile.core.designsystem.ext
import com.kanbanboard.mobile.core.model.ProjectId
import com.kanbanboard.mobile.core.model.TaskId
import com.kanbanboard.mobile.core.model.UserId
import com.kanbanboard.mobile.core.time.Formats
import com.kanbanboard.mobile.di.LocalAppContainer
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HistoryRouteScreen(onOpenTask: (TaskId) -> Unit) {
    val c = LocalAppContainer.current
    val vm: HistoryViewModel = viewModel { HistoryViewModel(c.boardRepository, c.projectRepository, c.teamRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    HistoryScreen(
        state = state,
        onPeriod = vm::setPeriod,
        onProject = vm::setProject,
        onAssignee = vm::setAssignee,
        onQuery = vm::setQuery,
        onOpenTask = onOpenTask,
    )
}

@Composable
private fun HistoryScreen(
    state: HistoryUiState,
    onPeriod: (HistoryPeriod) -> Unit,
    onProject: (ProjectId?) -> Unit,
    onAssignee: (UserId?) -> Unit,
    onQuery: (String) -> Unit,
    onOpenTask: (TaskId) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { AppTopBar(title = "История") },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.filters.query,
                    onValueChange = onQuery,
                    placeholder = { Text("Поиск по задачам") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = if (state.filters.query.isNotEmpty()) {
                        { IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, contentDescription = "Очистить") } }
                    } else null,
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(HistoryPeriod.entries) { period ->
                        AppChip(period.title, selected = state.filters.period == period, onClick = { onPeriod(period) })
                    }
                    item {
                        PickerChip(
                            label = state.projects.firstOrNull { it.id == state.filters.projectId }?.name ?: "Все проекты",
                            active = state.filters.projectId != null,
                            options = listOf<Pair<ProjectId?, String>>(null to "Все проекты") + state.projects.map { it.id to it.name },
                            onPick = onProject,
                        )
                    }
                    item {
                        PickerChip(
                            label = state.members.firstOrNull { it.id == state.filters.assigneeId }?.fullName ?: "Все участники",
                            active = state.filters.assigneeId != null,
                            options = listOf<Pair<UserId?, String>>(null to "Все участники") + state.members.map { it.id to it.fullName },
                            onPick = onAssignee,
                        )
                    }
                }
            }
            item { StatsRow(state.stats) }
            if (state.days.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.History,
                        title = "Нет выполненных задач",
                        text = "За выбранный период ничего не закрыто.",
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
            state.days.forEach { (date, rows) ->
                item(key = "day-$date") { DayHeader(date) }
                items(rows, key = { it.task.id.value }) { row -> HistoryCard(row, onClick = { onOpenTask(row.task.id) }) }
            }
        }
    }
}

/** Три одинаковые некликабельные карточки-показателя M3: крупное число и подпись. */
@Composable
private fun StatsRow(stats: HistoryStats) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard("${stats.done}", "Выполнено", Modifier.weight(1f))
        StatCard("${stats.returns}", "Возвратов", Modifier.weight(1f))
        StatCard(stats.averageCycle?.shortLabel() ?: "—", "Средний цикл", Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
            Caption(label)
        }
    }
}

@Composable
private fun <T> PickerChip(
    label: String,
    active: Boolean,
    options: List<Pair<T, String>>,
    onPick: (T) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = active,
            onClick = { open = true },
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, title) ->
                DropdownMenuItem(text = { Text(title) }, onClick = { open = false; onPick(value) })
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate) {
    val ru = Locale.forLanguageTag("ru")
    val today = LocalDate.now()
    val label = when (date) {
        today -> "Сегодня"
        today.minusDays(1) -> "Вчера"
        else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, ru).replaceFirstChar { it.titlecase(ru) } + ", " + Formats.date(date)
    }
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

/** Запись истории — отдельная карточка, как карточка пары в расписании (дата — в заголовке дня). */
@Composable
private fun HistoryCard(row: HistoryRow, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Text(
                Formats.time(row.completedAt.atZone(ZoneId.systemDefault()).toLocalTime()),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.width(52.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(row.task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    val assignee = row.assignee
                    if (assignee != null) {
                        UserAvatar(assignee.fullName, size = 20.dp, photo = assignee.photo)
                        Spacer(Modifier.width(6.dp))
                    }
                    Caption(
                        listOfNotNull(
                            assignee?.fullName ?: if (row.task.assigneeId != null) "Удалённый участник" else "Без исполнителя",
                            row.project?.name,
                            row.cycle?.let { "цикл ${it.shortLabel()}" },
                        ).joinToString(" · "),
                        maxLines = 2,
                    )
                }
            }
            if (row.returns > 0) {
                Spacer(Modifier.width(8.dp))
                Tag("Возвратов: ${row.returns}", color = MaterialTheme.ext.warning)
            }
        }
    }
}
