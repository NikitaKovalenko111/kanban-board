package com.kanbanboard.mobile.feature.task

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kanbanboard.mobile.core.designsystem.BackButton
import com.kanbanboard.mobile.core.designsystem.AppButton
import com.kanbanboard.mobile.core.designsystem.AppButtonKind
import com.kanbanboard.mobile.core.designsystem.AppTopBar
import com.kanbanboard.mobile.core.designsystem.EmptyState
import com.kanbanboard.mobile.core.designsystem.Hairline
import com.kanbanboard.mobile.core.designsystem.Caption
import com.kanbanboard.mobile.core.designsystem.Panel
import com.kanbanboard.mobile.core.designsystem.SectionHeader
import com.kanbanboard.mobile.core.designsystem.SpecRow
import com.kanbanboard.mobile.core.designsystem.Tag
import com.kanbanboard.mobile.core.designsystem.UserAvatar
import com.kanbanboard.mobile.core.designsystem.color
import com.kanbanboard.mobile.core.designsystem.ext
import com.kanbanboard.mobile.core.features.Features
import com.kanbanboard.mobile.core.model.Attachment
import com.kanbanboard.mobile.core.model.AttachmentKind
import com.kanbanboard.mobile.core.model.ColumnId
import com.kanbanboard.mobile.core.model.ColumnKind
import com.kanbanboard.mobile.core.model.ProjectId
import com.kanbanboard.mobile.core.model.Task
import com.kanbanboard.mobile.core.model.TaskAction
import com.kanbanboard.mobile.core.model.TaskHistoryEntry
import com.kanbanboard.mobile.core.model.TaskId
import com.kanbanboard.mobile.core.policy.MoveOption
import com.kanbanboard.mobile.core.policy.QuickMove
import com.kanbanboard.mobile.core.time.Formats
import com.kanbanboard.mobile.di.LocalAppContainer
import java.time.Instant
import kotlinx.coroutines.launch

@Composable
fun TaskDetailRouteScreen(
    taskId: TaskId,
    onBack: () -> Unit,
    onEdit: (ProjectId, TaskId) -> Unit,
    onOpenFile: (fileRef: String, name: String, projectName: String) -> Unit,
) {
    val c = LocalAppContainer.current
    val vm: TaskDetailViewModel = viewModel {
        TaskDetailViewModel(taskId, c.sessionRepository, c.teamRepository, c.boardRepository, c.projectRepository)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val currentOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is TaskDetailEvent.Message -> snackbar.showSnackbar(event.text)
                TaskDetailEvent.Deleted -> currentOnBack()
            }
        }
    }

    val scope = rememberCoroutineScope()
    var pendingKind by rememberSaveable { mutableStateOf<AttachmentKind?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val kind = pendingKind
        pendingKind = null
        if (uri != null && kind != null) {
            // Файл копируется в хранилище приложения, чтобы его можно было открыть позже.
            scope.launch {
                runCatching { c.fileStore.import(uri) }
                    .onSuccess { vm.addAttachment(it, kind) }
                    .onFailure { vm.report(it.message ?: "Не удалось прикрепить файл") }
            }
        }
    }

    TaskDetailScreen(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onEdit = { task -> onEdit(task.projectId, task.id) },
        onDelete = vm::delete,
        onPrimary = vm::primaryAction,
        onMove = vm::move,
        onComment = vm::addComment,
        onAttach = { kind ->
            pendingKind = kind
            picker.launch(arrayOf("*/*"))
        },
        onOpenFile = { file ->
            file.fileRef?.let { onOpenFile(it, file.name, state.projectName) }
        },
        onRemoveFile = { vm.removeAttachment(it.id) },
        onAssignToSelf = vm::assignToSelf,
    )
}

private enum class MoveDialogMode { ALL, RETURN }

@Composable
private fun TaskDetailScreen(
    state: TaskDetailUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onEdit: (Task) -> Unit,
    onDelete: () -> Unit,
    onPrimary: () -> Unit,
    onMove: (ColumnId, String?) -> Unit,
    onComment: (String, () -> Unit) -> Unit,
    onAttach: (AttachmentKind) -> Unit,
    onOpenFile: (Attachment) -> Unit,
    onRemoveFile: (Attachment) -> Unit,
    onAssignToSelf: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var moveDialog by rememberSaveable { mutableStateOf<MoveDialogMode?>(null) }
    val task = state.task
    val column = state.column

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            AppTopBar(
                title = "Задача",
                navigation = { BackButton(onBack) },
                actions = {
                    if (task != null && state.canEdit) {
                        IconButton(onClick = { onEdit(task) }) { Icon(Icons.Outlined.Edit, contentDescription = "Редактировать") }
                    }
                    if (task != null && state.canDelete) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Удалить") }
                    }
                },
            )
        },
        bottomBar = { if (task != null) CommentInput(enabled = !state.busy, onSend = onComment) },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            task == null || column == null -> EmptyState(
                icon = Icons.Outlined.SearchOff,
                title = "Задача не найдена",
                text = "Возможно, её удалили.",
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { HeaderPanel(state, task) }
                item {
                    ActionsBlock(
                        state = state,
                        onPrimary = onPrimary,
                        onOpenMove = { moveDialog = MoveDialogMode.ALL },
                        onOpenReturn = { moveDialog = MoveDialogMode.RETURN },
                        onAssignToSelf = onAssignToSelf,
                    )
                }
                item {
                    FilesPanel(
                        title = "Исходники",
                        files = task.attachments.filter { it.kind == AttachmentKind.SOURCE },
                        state = state,
                        canAdd = state.canUploadSource,
                        onAdd = { onAttach(AttachmentKind.SOURCE) },
                        onOpen = onOpenFile,
                        onRemove = onRemoveFile,
                    )
                }
                item {
                    FilesPanel(
                        title = "Результат",
                        files = task.attachments.filter { it.kind == AttachmentKind.RESULT },
                        state = state,
                        canAdd = state.canUploadResult,
                        onAdd = { onAttach(AttachmentKind.RESULT) },
                        onOpen = onOpenFile,
                        onRemove = onRemoveFile,
                    )
                }
                item { SectionHeader("Журнал", count = task.history.size, modifier = Modifier.padding(top = 4.dp)) }
                val history = task.history.sortedBy { it.at }
                itemsIndexed(history, key = { _, entry -> entry.id }) { index, entry ->
                    TimelineRow(entry, state, isLast = index == history.lastIndex)
                }
                item { SectionHeader("Комментарии", count = task.comments.size, modifier = Modifier.padding(top = 4.dp)) }
                if (task.comments.isEmpty()) {
                    item { Caption("Комментариев пока нет") }
                }
                itemsIndexed(task.comments.sortedBy { it.createdAt }, key = { _, comment -> comment.id }) { _, comment ->
                    Panel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(state.name(comment.authorId), size = 24.dp, photo = state.members[comment.authorId]?.photo)
                            Spacer(Modifier.width(8.dp))
                            Caption(
                                state.name(comment.authorId),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Caption(Formats.dateTime(comment.createdAt))
                        }
                        Text(comment.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }

    if (confirmDelete && task != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить задачу?") },
            text = { Text("«${task.title}» будет удалена вместе с файлами и журналом.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Удалить", color = MaterialTheme.ext.danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }

    val mode = moveDialog
    if (mode != null && task != null) {
        MoveDialog(
            options = if (mode == MoveDialogMode.RETURN) state.returnOptions else state.moveOptions,
            title = if (mode == MoveDialogMode.RETURN) "Вернуть на доработку" else "Переместить задачу",
            onDismiss = { moveDialog = null },
            onConfirm = { columnId, comment ->
                moveDialog = null
                onMove(columnId, comment)
            },
        )
    }
}

@Composable
private fun HeaderPanel(state: TaskDetailUiState, task: Task) {
    val column = state.column ?: return
    Panel(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag(column.title, color = Color(column.color), dot = true)
            Tag(task.priority.title, color = task.priority.color())
        }
        SelectionContainer {
            Column {
                Text(
                    task.title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    task.description.ifBlank { "Описание не добавлено" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Hairline()
        val assignee = task.assigneeId?.let { state.members[it] }
        SpecRow("Исполнитель", assignee?.let { "${it.fullName} (@${it.login})" } ?: task.assigneeId?.let { state.name(it) } ?: "Свободная задача",
            valueColor = if (task.assigneeId == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        Hairline()
        val due = task.dueAt
        val overdue = due != null && due.isBefore(Instant.now()) && task.completedAt == null
        SpecRow("Срок", due?.let { Formats.dateTime(it) + if (overdue) " · просрочено" else "" } ?: "Без срока",
            valueColor = if (overdue) MaterialTheme.ext.danger else MaterialTheme.colorScheme.onSurface)
        Hairline()
        SpecRow("Создал", "${state.name(task.createdBy)}, ${Formats.dateTime(task.createdAt)}")
        task.completedAt?.let {
            Hairline()
            SpecRow("Выполнена", Formats.dateTime(it), valueColor = MaterialTheme.ext.success)
        }
    }
}

@Composable
private fun ActionsBlock(
    state: TaskDetailUiState,
    onPrimary: () -> Unit,
    onOpenMove: () -> Unit,
    onOpenReturn: () -> Unit,
    onAssignToSelf: () -> Unit,
) {
    val primary = state.primary
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.canAssignToSelf && primary == null) {
            AppButton(
                "Назначить на себя",
                onClick = onAssignToSelf,
                kind = AppButtonKind.SECONDARY,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (primary != null) {
            AppButton(primaryLabel(primary), onClick = onPrimary, loading = state.busy, modifier = Modifier.fillMaxWidth())
        }
        if (state.returnOptions.isNotEmpty()) {
            AppButton(
                "Вернуть на доработку",
                onClick = onOpenReturn,
                kind = AppButtonKind.SECONDARY,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.moveOptions.size > 1 || (state.moveOptions.isNotEmpty() && primary == null)) {
            AppButton(
                "Переместить…",
                onClick = onOpenMove,
                kind = AppButtonKind.GHOST,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (primary == null && state.moveOptions.isEmpty()) {
            val task = state.task
            Caption(
                if (task?.assigneeId != null && task.assigneeId != state.session?.user?.id) {
                    "Задача назначена другому участнику"
                } else {
                    "Статус меняет исполнитель или капитан"
                },
                maxLines = 2,
            )
        }
    }
}

private fun primaryLabel(move: QuickMove): String = when (move) {
    is QuickMove.Take -> "Взять в работу"
    is QuickMove.Move -> when (move.target.kind) {
        ColumnKind.REVIEW -> "Сдать на проверку"
        ColumnKind.DONE -> "Принять задачу"
        else -> "В «${move.target.title}»"
    }
}

@Composable
private fun FilesPanel(
    title: String,
    files: List<Attachment>,
    state: TaskDetailUiState,
    canAdd: Boolean,
    onAdd: () -> Unit,
    onOpen: (Attachment) -> Unit,
    onRemove: (Attachment) -> Unit,
) {
    var toRemove by remember { mutableStateOf<Attachment?>(null) }
    toRemove?.let { file ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text("Удалить файл?") },
            text = { Text("«${file.name}» пропадёт из задачи.") },
            confirmButton = {
                TextButton(onClick = { toRemove = null; onRemove(file) }) { Text("Удалить", color = MaterialTheme.ext.danger) }
            },
            dismissButton = { TextButton(onClick = { toRemove = null }) { Text("Отмена") } },
        )
    }
    Panel {
        SectionHeader(
            title,
            count = files.size,
            action = if (canAdd) {
                { AppButton("Добавить", onClick = onAdd, kind = AppButtonKind.GHOST, enabled = !state.busy, icon = Icons.Outlined.AttachFile) }
            } else null,
        )
        if (files.isEmpty()) Caption("Файлов нет", modifier = Modifier.padding(top = 4.dp))
        files.forEach { file ->
            Hairline(Modifier.padding(vertical = 8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable(enabled = file.fileRef != null) { onOpen(file) }
                    .padding(vertical = 4.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.InsertDriveFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(file.name, style = MaterialTheme.typography.bodyMedium)
                    Caption("${Formats.fileSize(file.sizeBytes)} · ${state.name(file.uploadedBy)} · ${Formats.dateTime(file.uploadedAt)}")
                }
                if (state.canRemove(file)) {
                    // Удаление файлов есть только в мобилке — заблокировано до решения команды.
                    IconButton(onClick = { toRemove = file }, enabled = Features.DELETE_ATTACHMENT && !state.busy) {
                        Icon(Icons.Outlined.Close, contentDescription = "Удалить файл", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Журнал как лента событий: время слева, вертикальная линия, событие справа. */
@Composable
private fun TimelineRow(entry: TaskHistoryEntry, state: TaskDetailUiState, isLast: Boolean) {
    val accent = when (entry.action) {
        TaskAction.RETURNED -> MaterialTheme.ext.warning
        TaskAction.ACCEPTED -> MaterialTheme.ext.success
        TaskAction.TAKEN, TaskAction.SUBMITTED -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(52.dp), horizontalAlignment = Alignment.End) {
            Caption(Formats.date(entry.at).substring(0, 5))
            Caption(Formats.dateTime(entry.at).takeLast(5), color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.padding(top = 4.dp).size(8.dp).clip(CircleShape).background(accent))
            Box(
                Modifier
                    .width(1.dp)
                    .weight(1f)
                    .background(if (isLast) Color.Transparent else MaterialTheme.colorScheme.outlineVariant),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(bottom = 14.dp)) {
            val titleColor = if (accent == MaterialTheme.colorScheme.onSurfaceVariant) MaterialTheme.colorScheme.onSurface else accent
            Text(entry.action.title, style = MaterialTheme.typography.bodyMedium, color = titleColor)
            val path = when {
                entry.fromColumn != null && entry.toColumn != null -> "${entry.fromColumn} → ${entry.toColumn}"
                entry.toColumn != null -> "→ ${entry.toColumn}"
                else -> null
            }
            if (path != null) Caption(path)
            Caption(state.name(entry.actorId))
            entry.comment?.let { note ->
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .background(accent.copy(alpha = 0.12f), MaterialTheme.shapes.extraSmall)
                        .padding(8.dp),
                ) {
                    Text(note, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun CommentInput(enabled: Boolean, onSend: (String, () -> Unit) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Hairline()
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Комментарий") },
                maxLines = 4,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { onSend(text) { text = "" } },
                enabled = enabled && text.isNotBlank(),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.outlineVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
            }
        }
    }
}

@Composable
private fun MoveDialog(
    options: List<MoveOption>,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (ColumnId, String?) -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf(options.firstOrNull()?.target?.id?.value) }
    var comment by rememberSaveable { mutableStateOf("") }
    val selected = options.firstOrNull { it.target.id.value == selectedId }
    val needsComment = selected?.requiresComment == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option.target.id.value == selectedId,
                                onClick = { selectedId = option.target.id.value },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option.target.id.value == selectedId, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(option.target.title, modifier = Modifier.weight(1f))
                        if (option.requiresComment) Caption("С замечаниями")
                    }
                }
                if (needsComment) {
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = { Text("Что нужно исправить") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selected?.let { onConfirm(it.target.id, comment.takeIf(String::isNotBlank)) } },
                enabled = selected != null && (!needsComment || comment.isNotBlank()),
            ) { Text("Готово") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
