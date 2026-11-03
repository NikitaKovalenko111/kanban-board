package com.kanbanboard.mobile.feature.task

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kanbanboard.mobile.core.designsystem.AppDatePickerDialog
import com.kanbanboard.mobile.core.designsystem.AppTimePickerDialog
import com.kanbanboard.mobile.core.designsystem.BackButton
import com.kanbanboard.mobile.core.designsystem.AppButton
import com.kanbanboard.mobile.core.designsystem.AppButtonKind
import com.kanbanboard.mobile.core.designsystem.AppChip
import com.kanbanboard.mobile.core.designsystem.AppTopBar
import com.kanbanboard.mobile.core.designsystem.Caption
import com.kanbanboard.mobile.core.designsystem.Panel
import com.kanbanboard.mobile.core.designsystem.SectionHeader
import com.kanbanboard.mobile.core.designsystem.UserAvatar
import com.kanbanboard.mobile.core.designsystem.color
import com.kanbanboard.mobile.core.designsystem.ext
import com.kanbanboard.mobile.core.model.ProjectId
import com.kanbanboard.mobile.core.model.TaskId
import com.kanbanboard.mobile.core.model.TaskPriority
import com.kanbanboard.mobile.core.model.UserId
import com.kanbanboard.mobile.core.time.Formats
import com.kanbanboard.mobile.di.LocalAppContainer

@Composable
fun TaskEditorRouteScreen(projectId: ProjectId, taskId: TaskId?, onClose: () -> Unit) {
    val c = LocalAppContainer.current
    val vm: TaskEditorViewModel = viewModel {
        TaskEditorViewModel(projectId, taskId, c.teamRepository, c.projectRepository, c.boardRepository)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(vm) { vm.saved.collect { currentOnClose() } }

    TaskEditorScreen(
        state = state,
        onClose = onClose,
        onTitle = vm::setTitle,
        onDescription = vm::setDescription,
        onPriority = vm::setPriority,
        onAssignee = vm::setAssignee,
        onColumn = vm::setColumn,
        onDueDate = vm::setDueDate,
        onDueTime = vm::setDueTime,
        onSave = vm::save,
    )
}

private enum class EditorDialog { ASSIGNEE, DATE, TIME }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TaskEditorScreen(
    state: TaskEditorUiState,
    onClose: () -> Unit,
    onTitle: (String) -> Unit,
    onDescription: (String) -> Unit,
    onPriority: (TaskPriority) -> Unit,
    onAssignee: (UserId?) -> Unit,
    onColumn: (com.kanbanboard.mobile.core.model.ColumnId) -> Unit,
    onDueDate: (java.time.LocalDate?) -> Unit,
    onDueTime: (java.time.LocalTime) -> Unit,
    onSave: () -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<EditorDialog?>(null) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (state.isEdit) "Редактирование" else "Новая задача",
                navigation = { BackButton(onClose) },
                actions = {
                    AppButton(
                        if (state.saving) "…" else "Сохранить",
                        onClick = onSave,
                        kind = AppButtonKind.GHOST,
                        enabled = state.canSave,
                    )
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = onTitle,
                label = { Text("Название") },
                singleLine = true,
                isError = state.error != null,
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            // MOB-02: многострочное поле растёт вместе с текстом.
            OutlinedTextField(
                value = state.description,
                onValueChange = onDescription,
                label = { Text("Описание / ТЗ") },
                minLines = 3,
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
            )

            SectionHeader("Приоритет", modifier = Modifier.padding(top = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskPriority.entries.forEach { priority ->
                    AppChip(
                        text = priority.title,
                        selected = state.priority == priority,
                        onClick = { onPriority(priority) },
                        dotColor = priority.color(),
                    )
                }
            }

            SectionHeader("Исполнитель", modifier = Modifier.padding(top = 4.dp))
            Panel(onClick = { dialog = EditorDialog.ASSIGNEE }, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val assigned = state.assigneeId != null
                    UserAvatar(if (assigned) state.assigneeName() else "?", size = 32.dp, highlighted = !assigned)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(state.assigneeName(), style = MaterialTheme.typography.bodyLarge)
                        Caption(
                            if (assigned) "нажмите, чтобы сменить" else "свободная — возьмёт любой участник проекта",
                            maxLines = 2,
                        )
                    }
                }
            }

            if (!state.isEdit && state.columns.isNotEmpty()) {
                SectionHeader("Колонка", modifier = Modifier.padding(top = 4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.columns.forEach { column ->
                        AppChip(
                            text = column.title,
                            selected = state.columnId == column.id,
                            onClick = { onColumn(column.id) },
                            dotColor = Color(column.color),
                        )
                    }
                }
            }

            // MOB-11: срок с точным временем, а не «23:59 по умолчанию».
            SectionHeader("Срок", modifier = Modifier.padding(top = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppButton(
                    state.dueDate?.let { Formats.date(it) } ?: "Без срока",
                    onClick = { dialog = EditorDialog.DATE },
                    kind = AppButtonKind.SECONDARY,
                    icon = Icons.Outlined.Event,
                    modifier = Modifier.weight(1f),
                )
                if (state.dueDate != null) {
                    AppButton(
                        Formats.time(state.dueTime),
                        onClick = { dialog = EditorDialog.TIME },
                        kind = AppButtonKind.SECONDARY,
                        icon = Icons.Outlined.Schedule,
                    )
                    IconButton(onClick = { onDueDate(null) }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Убрать срок")
                    }
                }
            }

            state.error?.let { Caption("! $it", color = MaterialTheme.ext.danger, maxLines = 3) }

            AppButton(
                if (state.isEdit) "Сохранить изменения" else "Создать задачу",
                onClick = onSave,
                enabled = state.canSave,
                loading = state.saving,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }

    when (dialog) {
        EditorDialog.ASSIGNEE -> AssigneeDialog(
            state = state,
            onDismiss = { dialog = null },
            onSelect = { id -> dialog = null; onAssignee(id) },
        )
        EditorDialog.DATE -> AppDatePickerDialog(
            initial = state.dueDate,
            onDismiss = { dialog = null },
            onConfirm = { date -> dialog = null; onDueDate(date) },
        )
        EditorDialog.TIME -> AppTimePickerDialog(
            initial = state.dueTime,
            title = "Время срока",
            onDismiss = { dialog = null },
            onConfirm = { time -> dialog = null; onDueTime(time) },
        )
        null -> Unit
    }
}

@Composable
private fun AssigneeDialog(state: TaskEditorUiState, onDismiss: () -> Unit, onSelect: (UserId?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Исполнитель") },
        text = {
            LazyColumn {
                item { AssigneeRow("Без исполнителя (свободная)", null, state.assigneeId == null) { onSelect(null) } }
                items(state.assignees, key = { it.user.id.value }) { option ->
                    AssigneeRow(
                        option.user.fullName,
                        option.projectRole.ifBlank { null },
                        state.assigneeId == option.user.id,
                    ) { onSelect(option.user.id) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

@Composable
private fun AssigneeRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
