package com.kanbanboard.mobile.feature.projects

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kanbanboard.mobile.core.data.ProjectRepository
import com.kanbanboard.mobile.core.data.SessionRepository
import com.kanbanboard.mobile.core.data.settings.SettingsRepository
import com.kanbanboard.mobile.core.designsystem.AppTopBar
import com.kanbanboard.mobile.core.designsystem.Caption
import com.kanbanboard.mobile.core.designsystem.EmptyState
import com.kanbanboard.mobile.core.designsystem.SectionHeader
import com.kanbanboard.mobile.core.designsystem.Tag
import com.kanbanboard.mobile.core.model.Project
import com.kanbanboard.mobile.core.model.ProjectId
import com.kanbanboard.mobile.core.policy.ProjectPolicy
import com.kanbanboard.mobile.di.LocalAppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProjectItemUi(val project: Project, val myRole: String?, val active: Boolean)

data class ProjectsUiState(
    val mine: List<ProjectItemUi> = emptyList(),
    val others: List<ProjectItemUi> = emptyList(),
    val canCreate: Boolean = false,
)

class ProjectsViewModel(
    sessions: SessionRepository,
    projects: ProjectRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<ProjectsUiState> = combine(projects.projects, sessions.session, settings.settings) { list, session, prefs ->
        val activeId = prefs.selectedProjectId?.takeIf { id -> list.any { it.id == id && it.canViewBoard } }
            ?: list.firstOrNull { it.canViewBoard && session != null && it.isMember(session.user.id) }?.id
            ?: list.firstOrNull { it.canViewBoard }?.id
        val items = list.map { project ->
            ProjectItemUi(project, session?.let { project.members[it.user.id] }, project.id == activeId)
        }
        val (mine, others) = items.partition { session != null && it.project.isMember(session.user.id) }
        ProjectsUiState(mine, others, canCreate = session?.let { ProjectPolicy.canManage(it) } == true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectsUiState())

    /** Двойное нажатие на проект — сделать его активным на доске. */
    fun makeActive(id: ProjectId) {
        if (state.value.mine.none { it.project.id == id && it.project.canViewBoard } &&
            state.value.others.none { it.project.id == id && it.project.canViewBoard }) return
        viewModelScope.launch { settings.setSelectedProject(id) }
    }
}

@Composable
fun ProjectsRouteScreen(
    onOpenProject: (ProjectId) -> Unit,
    onCreateProject: () -> Unit,
    onOpenBoard: () -> Unit,
) {
    val c = LocalAppContainer.current
    val vm: ProjectsViewModel = viewModel {
        ProjectsViewModel(c.sessionRepository, c.projectRepository, c.settingsRepository)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    ProjectsScreen(
        state = state,
        snackbar = snackbar,
        onOpen = onOpenProject,
        onMakeActive = { project ->
            vm.makeActive(project.id)
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val result = snackbar.showSnackbar(
                    message = "«${project.name}» теперь на доске",
                    actionLabel = "Открыть",
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) onOpenBoard()
            }
        },
        onCreate = onCreateProject,
    )
}

@Composable
private fun ProjectsScreen(
    state: ProjectsUiState,
    snackbar: SnackbarHostState,
    onOpen: (ProjectId) -> Unit,
    onMakeActive: (Project) -> Unit,
    onCreate: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { AppTopBar(title = "Проекты") },
        floatingActionButton = {
            if (state.canCreate) {
                ExtendedFloatingActionButton(
                    onClick = onCreate,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Проект") },
                )
            }
        },
    ) { padding ->
        if (state.mine.isEmpty() && state.others.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.FolderOpen,
                title = "Проектов пока нет",
                text = if (state.canCreate) {
                    "Нажмите «Проект», чтобы создать первый: у него сразу будет доска."
                } else {
                    "Проекты создаёт капитан команды."
                },
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = if (state.canCreate) 96.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.mine.isNotEmpty()) {
                item { SectionHeader("Мои проекты", count = state.mine.size) }
                items(state.mine, key = { "mine-" + it.project.id.value }) { ProjectCard(it, onOpen, onMakeActive) }
            }
            if (state.others.isNotEmpty()) {
                item { SectionHeader("Остальные", count = state.others.size, modifier = Modifier.padding(top = 8.dp)) }
                items(state.others, key = { "other-" + it.project.id.value }) { ProjectCard(it, onOpen, onMakeActive) }
            }
        }
    }
}

/** Нажатие — карточка проекта, двойное нажатие — сделать проект активным на доске. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectCard(item: ProjectItemUi, onOpen: (ProjectId) -> Unit, onMakeActive: (Project) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = item.project.canViewBoard,
                onClick = { onOpen(item.project.id) },
                onDoubleClick = { onMakeActive(item.project) },
            ),
        colors = if (item.active) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        },
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.project.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (item.active) Tag("На доске", filled = true)
                if (!item.project.canViewBoard) Tag("Нет доступа к доске")
            }
            if (item.project.description.isNotBlank()) {
                Text(
                    item.project.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Caption(
                listOfNotNull(
                    item.myRole?.takeIf { it.isNotBlank() },
                    if (item.project.canViewBoard) "${item.project.members.size} участн." else null,
                ).joinToString(" · "),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
