package com.kanbanboard.mobile.feature.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kanbanboard.mobile.core.designsystem.AppTopBar
import com.kanbanboard.mobile.core.designsystem.Caption
import com.kanbanboard.mobile.core.designsystem.ColorDot
import com.kanbanboard.mobile.core.designsystem.AppButton
import com.kanbanboard.mobile.core.designsystem.EmptyState
import com.kanbanboard.mobile.core.model.ColumnId
import com.kanbanboard.mobile.core.model.ColumnKind
import com.kanbanboard.mobile.core.model.Project
import com.kanbanboard.mobile.core.model.ProjectId
import com.kanbanboard.mobile.core.model.TaskId
import com.kanbanboard.mobile.core.model.UserId
import com.kanbanboard.mobile.di.LocalAppContainer
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Сколько выполненных задач показывать на доске; остальные — в «Истории». */
private const val DONE_LIMIT = 5

@Composable
fun BoardRouteScreen(
    onOpenTask: (TaskId) -> Unit,
    onCreateTask: (ProjectId) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenProjects: () -> Unit,
    onCreateProject: () -> Unit,
) {
    val c = LocalAppContainer.current
    val vm: BoardViewModel = viewModel {
        BoardViewModel(c.sessionRepository, c.teamRepository, c.projectRepository, c.boardRepository, c.settingsRepository)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    var menuFor by remember { mutableStateOf<TaskCardUi?>(null) }

    BoardScreen(
        state = state,
        snackbar = snackbar,
        onSelectProject = vm::selectProject,
        onSelectColumn = vm::selectColumn,
        onOpenTask = onOpenTask,
        onTaskMenu = { menuFor = it },
        onCreateTask = onCreateTask,
        onOpenHistory = onOpenHistory,
        onOpenProjects = onOpenProjects,
        onCreateProject = onCreateProject,
    )

    menuFor?.let { card ->
        TaskActionsSheet(
            card = card,
            onDismiss = { menuFor = null },
            onOpen = { menuFor = null; onOpenTask(card.task.id) },
            onTake = { menuFor = null; vm.take(card) },
            onMove = { option -> menuFor = null; vm.move(card, option) },
        )
    }
}

@Composable
private fun BoardScreen(
    state: BoardUiState,
    snackbar: SnackbarHostState,
    onSelectProject: (ProjectId) -> Unit,
    onSelectColumn: (ColumnId) -> Unit,
    onOpenTask: (TaskId) -> Unit,
    onTaskMenu: (TaskCardUi) -> Unit,
    onCreateTask: (ProjectId) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenProjects: () -> Unit,
    onCreateProject: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            AppTopBar(
                title = state.project?.name ?: "Доска",
                titleContent = {
                    ProjectSwitcher(state.project, state.projects, state.session?.user?.id, onSelectProject, onOpenProjects)
                },
            )
        },
        floatingActionButton = {
            val project = state.project
            if (state.canCreate && project != null) {
                ExtendedFloatingActionButton(
                    onClick = { onCreateTask(project.id) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Задача") },
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.project == null -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val captain = state.session?.isCaptain == true
                EmptyState(
                    icon = Icons.Outlined.Inbox,
                    title = "Проектов пока нет",
                    text = if (captain) {
                        "Создайте первый проект — у него сразу будет доска со стандартными колонками."
                    } else {
                        "Капитан команды создаёт проекты, после этого здесь появится доска."
                    },
                    modifier = Modifier.weight(1f),
                )
                if (captain) {
                    AppButton(
                        "Создать проект",
                        onClick = onCreateProject,
                        icon = Icons.Filled.Add,
                        modifier = Modifier.padding(bottom = 32.dp),
                    )
                }
            }
            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                ColumnsPager(state, onSelectColumn, onOpenTask, onTaskMenu, onOpenHistory)
            }
        }
    }
}

/**
 * Вкладки колонок (прокручиваемые, без обрезания названий) + пейджер: свайп по доске
 * переключает колонки, выбор вкладки листает пейджер. Сверху — прогресс проекта.
 */
@Composable
private fun ColumnsPager(
    state: BoardUiState,
    onSelectColumn: (ColumnId) -> Unit,
    onOpenTask: (TaskId) -> Unit,
    onTaskMenu: (TaskCardUi) -> Unit,
    onOpenHistory: () -> Unit,
) {
    val columns = state.columns
    if (columns.isEmpty()) return
    val pager = rememberPagerState(initialPage = state.selectedIndex) { columns.size }
    val scope = rememberCoroutineScope()

    // Свайп → выбранная колонка во ViewModel (переживает смену вкладок приложения).
    LaunchedEffect(pager, columns) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { page ->
            columns.getOrNull(page)?.let { onSelectColumn(it.column.id) }
        }
    }

    if (state.totalTasks > 0) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Caption("Выполнено ${state.doneTasks} из ${state.totalTasks}")
            LinearProgressIndicator(
                progress = { state.doneTasks.toFloat() / state.totalTasks },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
            )
        }
    }

    PrimaryScrollableTabRow(
        selectedTabIndex = pager.currentPage.coerceIn(0, columns.lastIndex),
        edgePadding = 16.dp,
        containerColor = Color.Transparent,
    ) {
        columns.forEachIndexed { index, column ->
            val selected = pager.currentPage == index
            Tab(
                selected = selected,
                onClick = { scope.launch { pager.animateScrollToPage(index) } },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ColorDot(Color(column.column.color))
                        Spacer(Modifier.width(8.dp))
                        Text(column.column.title, maxLines = 1)
                        Spacer(Modifier.width(8.dp))
                        Badge(
                            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        ) { Text("${column.cards.size}") }
                    }
                },
            )
        }
    }

    HorizontalPager(state = pager, key = { columns.getOrNull(it)?.column?.id?.value ?: it }, modifier = Modifier.fillMaxSize()) { page ->
        val column = columns[page]
        if (column.cards.isEmpty()) {
            EmptyState(icon = Icons.Outlined.Inbox, title = "В колонке «${column.column.title}» пусто")
        } else {
            val isDone = column.column.kind == ColumnKind.DONE
            val visible = if (isDone) column.cards.take(DONE_LIMIT) else column.cards
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = if (state.canCreate) 96.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(visible, key = { it.task.id.value }) { card ->
                    TaskCard(card, onClick = { onOpenTask(card.task.id) }, onLongClick = { onTaskMenu(card) })
                }
                if (isDone && column.cards.size > DONE_LIMIT) {
                    item {
                        TextButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) {
                            Text("Ещё ${column.cards.size - DONE_LIMIT} — в истории")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectSwitcher(
    project: Project?,
    projects: List<Project>,
    myId: UserId?,
    onSelect: (ProjectId) -> Unit,
    onAllProjects: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clickable(enabled = projects.isNotEmpty()) { expanded = true }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                project?.name ?: "Доска",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (projects.isNotEmpty()) Icon(Icons.Filled.ArrowDropDown, contentDescription = "Сменить проект")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val (mine, others) = projects.partition { myId != null && it.isMember(myId) }
            mine.forEach { item -> ProjectMenuItem(item, item.id == project?.id) { expanded = false; onSelect(item.id) } }
            if (mine.isNotEmpty() && others.isNotEmpty()) HorizontalDivider()
            others.forEach { item -> ProjectMenuItem(item, item.id == project?.id) { expanded = false; onSelect(item.id) } }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("Все проекты") }, onClick = { expanded = false; onAllProjects() })
        }
    }
}

@Composable
private fun ProjectMenuItem(project: Project, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(project.name) },
        onClick = onClick,
        trailingIcon = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = "Активный") }
        } else null,
    )
}

/** Меню по долгому нажатию на карточку: открыть, взять в работу, переместить. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskActionsSheet(
    card: TaskCardUi,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onTake: () -> Unit,
    onMove: (com.kanbanboard.mobile.core.policy.MoveOption) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                card.task.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            ListItem(
                headlineContent = { Text("Открыть задачу") },
                leadingContent = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpen),
            )
            card.take?.let { take ->
                ListItem(
                    headlineContent = { Text("Взять в работу") },
                    supportingContent = { Text("Перейдёт в «${take.target.title}»") },
                    leadingContent = { Icon(Icons.Outlined.PlayArrow, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onTake),
                )
            }
            card.moves.forEach { option ->
                ListItem(
                    headlineContent = { Text("Переместить в «${option.target.title}»") },
                    leadingContent = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null) },
                    modifier = Modifier.clickable { onMove(option) },
                )
            }
        }
    }
}
