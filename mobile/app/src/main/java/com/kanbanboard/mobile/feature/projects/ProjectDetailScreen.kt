package com.kanbanboard.mobile.feature.projects

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.ViewKanban
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kanbanboard.mobile.core.data.ProjectRepository
import com.kanbanboard.mobile.core.data.SessionRepository
import com.kanbanboard.mobile.core.data.TeamRepository
import com.kanbanboard.mobile.core.data.settings.SettingsRepository
import com.kanbanboard.mobile.core.files.FileStore
import com.kanbanboard.mobile.core.designsystem.BackButton
import com.kanbanboard.mobile.core.designsystem.AppButton
import com.kanbanboard.mobile.core.designsystem.AppButtonKind
import com.kanbanboard.mobile.core.designsystem.AppTopBar
import com.kanbanboard.mobile.core.designsystem.EmptyState
import com.kanbanboard.mobile.core.designsystem.Hairline
import com.kanbanboard.mobile.core.designsystem.Caption
import com.kanbanboard.mobile.core.designsystem.Panel
import com.kanbanboard.mobile.core.designsystem.SectionHeader
import com.kanbanboard.mobile.core.designsystem.Tag
import com.kanbanboard.mobile.core.designsystem.UserAvatar
import com.kanbanboard.mobile.core.designsystem.ext
import com.kanbanboard.mobile.core.model.Project
import com.kanbanboard.mobile.core.model.ProjectId
import com.kanbanboard.mobile.core.model.ProjectSource
import com.kanbanboard.mobile.core.model.Session
import com.kanbanboard.mobile.core.model.SourceKind
import com.kanbanboard.mobile.core.model.User
import com.kanbanboard.mobile.core.policy.ProjectPolicy
import com.kanbanboard.mobile.core.time.Formats
import com.kanbanboard.mobile.di.LocalAppContainer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProjectMemberUi(val user: User, val role: String)

data class ProjectDetailUiState(
    val loading: Boolean = true,
    val session: Session? = null,
    val project: Project? = null,
    val members: List<ProjectMemberUi> = emptyList(),
    val users: Map<com.kanbanboard.mobile.core.model.UserId, User> = emptyMap(),
    val isActive: Boolean = false,
) {
    val canManage: Boolean get() = session?.let { ProjectPolicy.canManage(it) } == true
    val canAddSource: Boolean get() = session != null && project != null && ProjectPolicy.canAddSource(session, project)
    fun canRemove(source: ProjectSource): Boolean = session?.let { ProjectPolicy.canRemoveSource(it, source) } == true
}

class ProjectDetailViewModel(
    private val projectId: ProjectId,
    sessions: SessionRepository,
    team: TeamRepository,
    private val projects: ProjectRepository,
    private val settings: SettingsRepository,
    private val files: FileStore,
) : ViewModel() {
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    val state: StateFlow<ProjectDetailUiState> = combine(
        projects.project(projectId),
        sessions.session,
        team.members,
        settings.settings,
    ) { project, session, members, prefs ->
        val users = members.associate { it.user.id to it.user }
        ProjectDetailUiState(
            loading = false,
            session = session,
            project = project,
            members = project?.members.orEmpty().mapNotNull { (id, role) -> users[id]?.let { ProjectMemberUi(it, role) } }
                .sortedBy { it.user.fullName },
            users = users,
            isActive = prefs.selectedProjectId == projectId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectDetailUiState())

    fun openOnBoard(then: () -> Unit) {
        viewModelScope.launch {
            settings.setSelectedProject(projectId)
            then()
        }
    }

    fun addLink(title: String, url: String, onError: (String) -> Unit, onDone: () -> Unit) {
        viewModelScope.launch {
            projects.addLink(projectId, title, url)
                .onSuccess { onDone(); _messages.trySend("Ссылка добавлена") }
                .onFailure { onError(it.message ?: "Не удалось добавить ссылку") }
        }
    }

    /** Файл копируется в хранилище приложения, затем добавляется в источники проекта. */
    fun addFile(uri: Uri) {
        viewModelScope.launch {
            runCatching { files.import(uri) }
                .mapCatching { projects.addFile(projectId, it).getOrThrow() }
                .onSuccess { _messages.trySend("Файл добавлен") }
                .onFailure { _messages.trySend(it.message ?: "Не удалось добавить файл") }
        }
    }

    fun deleteProject(onDone: () -> Unit) {
        viewModelScope.launch {
            projects.deleteProject(projectId)
                // Если проект был активным, доска сама переключится на другой доступный проект.
                .onSuccess { onDone() }
                .onFailure { _messages.trySend(it.message ?: "Не удалось удалить проект") }
        }
    }

    fun removeSource(source: ProjectSource) {
        viewModelScope.launch {
            projects.removeSource(projectId, source.id)
                .onSuccess { _messages.trySend("«${source.title}» удалён") }
                .onFailure { _messages.trySend(it.message ?: "Не удалось удалить") }
        }
    }
}

@Composable
fun ProjectDetailRouteScreen(
    projectId: ProjectId,
    onBack: () -> Unit,
    onEdit: (ProjectId) -> Unit,
    onOpenFile: (fileRef: String, name: String, projectName: String) -> Unit,
    onOpenBoard: () -> Unit,
) {
    val c = LocalAppContainer.current
    val vm: ProjectDetailViewModel = viewModel {
        ProjectDetailViewModel(projectId, c.sessionRepository, c.teamRepository, c.projectRepository, c.settingsRepository, c.fileStore)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.addFile(uri)
    }
    ProjectDetailScreen(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onEdit = { onEdit(projectId) },
        onOpenBoard = { vm.openOnBoard(onOpenBoard) },
        onAddFile = { picker.launch(arrayOf("*/*")) },
        onAddLink = vm::addLink,
        onRemove = vm::removeSource,
        onDeleteProject = { vm.deleteProject(onBack) },
        onOpenFile = { source ->
            val ref = source.fileRef
            val name = state.project?.name.orEmpty()
            if (ref != null) onOpenFile(ref, source.title, name)
        },
    )
}

@Composable
private fun ProjectDetailScreen(
    state: ProjectDetailUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenBoard: () -> Unit,
    onAddFile: () -> Unit,
    onAddLink: (String, String, (String) -> Unit, () -> Unit) -> Unit,
    onRemove: (ProjectSource) -> Unit,
    onOpenFile: (ProjectSource) -> Unit,
    onDeleteProject: () -> Unit,
) {
    var showLinkDialog by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var toRemove by remember { mutableStateOf<ProjectSource?>(null) }
    val uriHandler = LocalUriHandler.current
    val project = state.project

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            AppTopBar(
                title = project?.name ?: "Проект",
                navigation = { BackButton(onBack) },
                actions = {
                    if (state.canManage && project != null) {
                        IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "Настройки проекта") }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Удалить проект", tint = MaterialTheme.ext.danger)
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            project == null -> EmptyState(
                icon = Icons.Outlined.FolderOff,
                title = "Проект не найден",
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Panel(contentPadding = PaddingValues(16.dp)) {
                        Text(
                            project.description.ifBlank { "Описание пока не добавлено" },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (project.description.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.size(12.dp))
                        if (state.isActive) {
                            Tag("Сейчас на доске", filled = true)
                        } else {
                            AppButton("Открыть доску", onClick = onOpenBoard, icon = Icons.Outlined.ViewKanban, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                item {
                    SectionHeader(
                        "Источники",
                        count = project.sources.size,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                item {
                    Panel(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        if (project.sources.isEmpty()) {
                            Caption("Ссылок и файлов пока нет", modifier = Modifier.padding(vertical = 10.dp))
                        }
                        project.sources.sortedByDescending { it.addedAt }.forEachIndexed { index, source ->
                            if (index > 0) Hairline()
                            SourceRow(
                                source = source,
                                author = state.users[source.addedBy]?.fullName ?: "Участник",
                                canRemove = state.canRemove(source),
                                onOpen = {
                                    val url = source.url
                                    if (url != null) runCatching { uriHandler.openUri(url) } else onOpenFile(source)
                                },
                                onRemove = { toRemove = source },
                            )
                        }
                    }
                }
                if (state.canAddSource) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppButton(
                                "Ссылка",
                                onClick = { showLinkDialog = true },
                                kind = AppButtonKind.SECONDARY,
                                icon = Icons.Outlined.Link,
                                modifier = Modifier.weight(1f),
                            )
                            AppButton(
                                "Файл",
                                onClick = onAddFile,
                                kind = AppButtonKind.SECONDARY,
                                icon = Icons.AutoMirrored.Outlined.InsertDriveFile,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                item { SectionHeader("Состав", count = state.members.size, modifier = Modifier.padding(top = 6.dp)) }
                item {
                    Panel(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        if (state.members.isEmpty()) {
                            Caption("В проекте пока никого", modifier = Modifier.padding(vertical = 10.dp))
                        }
                        state.members.forEachIndexed { index, member ->
                            if (index > 0) Hairline()
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                UserAvatar(member.user.fullName, size = 32.dp, highlighted = member.user.id == state.session?.user?.id, photo = member.user.photo)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(member.user.fullName, style = MaterialTheme.typography.bodyLarge)
                                    Caption("@${member.user.login}")
                                }
                                if (member.role.isNotBlank()) Tag(member.role, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLinkDialog) {
        LinkDialog(
            onDismiss = { showLinkDialog = false },
            onSubmit = { title, url, onError -> onAddLink(title, url, onError) { showLinkDialog = false } },
        )
    }
    if (confirmDelete && project != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить проект?") },
            text = { Text("«${project.name}» будет удалён вместе с доской, задачами, файлами и источниками. Это нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDeleteProject() }) { Text("Удалить", color = MaterialTheme.ext.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
    toRemove?.let { source ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text("Удалить источник?") },
            text = { Text("«${source.title}» пропадёт из проекта у всех участников.") },
            confirmButton = {
                TextButton(onClick = { toRemove = null; onRemove(source) }) { Text("Удалить", color = MaterialTheme.ext.danger) }
            },
            dismissButton = { TextButton(onClick = { toRemove = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun SourceRow(
    source: ProjectSource,
    author: String,
    canRemove: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val isLink = source.kind == SourceKind.LINK
    val openable = isLink || source.fileRef != null
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = openable, onClick = onOpen)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (isLink) Icons.Outlined.Link else Icons.AutoMirrored.Outlined.InsertDriveFile,
            contentDescription = if (isLink) "Ссылка" else "Файл",
            tint = if (isLink) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(source.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Caption(
                listOfNotNull(
                    if (isLink) source.url?.removePrefix("https://")?.removePrefix("http://") else source.sizeBytes?.let(Formats::fileSize),
                    author,
                    Formats.date(source.addedAt),
                ).joinToString(" · "),
                maxLines = 2,
            )
        }
        if (canRemove) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Close, contentDescription = "Удалить источник", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LinkDialog(onDismiss: () -> Unit, onSubmit: (String, String, (String) -> Unit) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("https://") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var sending by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая ссылка") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it.trim(); error = null },
                    label = { Text("Адрес") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = MaterialTheme.shapes.small,
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(120); error = null },
                    label = { Text("Название (необязательно)") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                )
                error?.let { Caption(it, color = MaterialTheme.ext.danger, maxLines = 3) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !sending && ProjectPolicy.isValidUrl(url),
                onClick = {
                    sending = true
                    onSubmit(title, url) { message -> error = message; sending = false }
                },
            ) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
