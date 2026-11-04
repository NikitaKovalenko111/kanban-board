package com.kanbanboard.mobile.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import com.kanbanboard.mobile.core.designsystem.SectionHeader
import com.kanbanboard.mobile.core.designsystem.Tag
import com.kanbanboard.mobile.core.designsystem.ext
import com.kanbanboard.mobile.core.model.EventId
import com.kanbanboard.mobile.core.model.EventType
import com.kanbanboard.mobile.core.model.EventVisibility
import com.kanbanboard.mobile.core.model.ReminderPresets
import com.kanbanboard.mobile.core.model.StudyGroup
import com.kanbanboard.mobile.core.notifications.rememberNotificationPermissionRequest
import com.kanbanboard.mobile.core.time.Formats
import com.kanbanboard.mobile.di.LocalAppContainer
import java.time.LocalDate

/** Сколько типов показывать до «Показать все» — чтобы 20–30 типов не превращались в бесконечную ленту. */
private const val COLLAPSED_TYPES = 6

@Composable
fun EventEditorRouteScreen(eventId: EventId?, initialDate: LocalDate, onClose: () -> Unit) {
    val c = LocalAppContainer.current
    val vm: EventEditorViewModel = viewModel {
        EventEditorViewModel(eventId, initialDate, c.sessionRepository, c.scheduleRepository)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(vm) { vm.saved.collect { currentOnClose() } }
    val askPermission = rememberNotificationPermissionRequest()
    EventEditorScreen(
        state = state,
        vm = vm,
        onClose = onClose,
        onSave = {
            if (state.reminders.isNotEmpty()) askPermission()
            vm.save()
        },
    )
}

private enum class EditorDialog { START_DATE, START_TIME, END_DATE, END_TIME, REMINDERS, NEW_TYPE, EDIT_TYPE }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventEditorScreen(
    state: EventEditorUiState,
    vm: EventEditorViewModel,
    onClose: () -> Unit,
    onSave: () -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf<EditorDialog?>(null) }
    var showAllTypes by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (state.isEdit) "Изменить событие" else "Новое событие",
                navigation = { BackButton(onClose) },
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
                onValueChange = vm::setTitle,
                label = { Text("Название") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            // MOB-02: описание растёт по высоте вместе с текстом.
            OutlinedTextField(
                value = state.description,
                onValueChange = vm::setDescription,
                label = { Text("Описание") },
                minLines = 2,
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            )
            // MOB-06: локация — отдельное поле, не часть описания.
            OutlinedTextField(
                value = state.location,
                onValueChange = vm::setLocation,
                label = { Text("Где: аудитория, корпус или ссылка") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionHeader("Когда")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppButton(
                    Formats.date(state.startDate),
                    onClick = { dialog = EditorDialog.START_DATE },
                    kind = AppButtonKind.SECONDARY,
                    icon = Icons.Outlined.Event,
                    modifier = Modifier.weight(1f),
                )
                AppButton(
                    Formats.time(state.startTime),
                    onClick = { dialog = EditorDialog.START_TIME },
                    kind = AppButtonKind.SECONDARY,
                    icon = Icons.Outlined.Schedule,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Указать окончание", style = MaterialTheme.typography.bodyLarge)
                    Caption("Необязательно — например, для дедлайна", maxLines = 2)
                }
                Switch(
                    checked = state.hasEnd,
                    onCheckedChange = vm::setHasEnd,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
            if (state.hasEnd) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton(
                        Formats.date(state.endDate),
                        onClick = { dialog = EditorDialog.END_DATE },
                        kind = AppButtonKind.SECONDARY,
                        icon = Icons.Outlined.Event,
                        modifier = Modifier.weight(1f),
                    )
                    AppButton(
                        Formats.time(state.endTime),
                        onClick = { dialog = EditorDialog.END_TIME },
                        kind = AppButtonKind.SECONDARY,
                        icon = Icons.Outlined.Schedule,
                    )
                }
            }

            SectionHeader("Для кого")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EventVisibility.entries.forEach { visibility ->
                    AppChip(
                        text = when (visibility) {
                            EventVisibility.TEAM -> "вся команда"
                            EventVisibility.GROUP -> "группа"
                            EventVisibility.PERSONAL -> "лично"
                        },
                        selected = state.visibility == visibility,
                        onClick = { vm.setVisibility(visibility) },
                    )
                }
            }
            if (state.visibility == EventVisibility.GROUP) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudyGroup.entries.filter { state.canUseGroup(it) }.forEach { group ->
                        AppChip(
                            text = "гр. ${group.code}",
                            selected = state.group == group,
                            onClick = { vm.setGroup(group) },
                        )
                    }
                }
            }
            if (state.visibility == EventVisibility.PERSONAL) {
                Caption("Что увидит команда, задаётся в настройках аккаунта", maxLines = 2)
            }

            SectionHeader(
                "тип",
                count = state.types.size,
                action = {
                    AppButton("+ свой", onClick = { dialog = EditorDialog.NEW_TYPE }, kind = AppButtonKind.GHOST)
                },
            )
            TypeGrid(
                types = state.types,
                selected = state.typeId,
                expanded = showAllTypes,
                onSelect = vm::selectType,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.types.size > COLLAPSED_TYPES) {
                    AppButton(
                        if (showAllTypes) "Свернуть" else "Показать все (${state.types.size})",
                        onClick = { showAllTypes = !showAllTypes },
                        kind = AppButtonKind.GHOST,
                    )
                }
                val selectedType = state.selectedType
                if (selectedType != null && state.canEditType(selectedType)) {
                    AppButton("Изменить тип", onClick = { dialog = EditorDialog.EDIT_TYPE }, kind = AppButtonKind.GHOST)
                }
            }

            SectionHeader("Мои напоминания", count = state.reminders.size)
            if (state.reminders.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.reminders.sorted().forEach { Tag(ReminderPresets.label(it), color = MaterialTheme.colorScheme.primary) }
                }
            }
            AppButton(
                if (state.reminders.isEmpty()) "Добавить напоминание" else "Изменить напоминания",
                onClick = { dialog = EditorDialog.REMINDERS },
                kind = AppButtonKind.SECONDARY,
                icon = Icons.Outlined.NotificationsActive,
                modifier = Modifier.fillMaxWidth(),
            )

            state.error?.let { Caption("! $it", color = MaterialTheme.ext.danger, maxLines = 3) }

            AppButton(
                if (state.isEdit) "Сохранить" else "Создать событие",
                onClick = onSave,
                enabled = state.canSave,
                loading = state.saving,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }

    when (dialog) {
        EditorDialog.START_DATE -> AppDatePickerDialog(
            initial = state.startDate,
            onDismiss = { dialog = null },
            onConfirm = { dialog = null; vm.setStartDate(it) },
        )
        EditorDialog.START_TIME -> AppTimePickerDialog(
            initial = state.startTime,
            title = "Время начала",
            onDismiss = { dialog = null },
            onConfirm = { dialog = null; vm.setStartTime(it) },
        )
        EditorDialog.END_DATE -> AppDatePickerDialog(
            initial = state.endDate,
            onDismiss = { dialog = null },
            onConfirm = { dialog = null; vm.setEndDate(it) },
        )
        EditorDialog.END_TIME -> AppTimePickerDialog(
            initial = state.endTime,
            title = "Время окончания",
            onDismiss = { dialog = null },
            onConfirm = { dialog = null; vm.setEndTime(it) },
        )
        EditorDialog.REMINDERS -> ReminderDialog(
            title = "Мои напоминания",
            initial = state.reminders,
            onDismiss = { dialog = null },
            onSave = { dialog = null; vm.setReminders(it) },
        )
        EditorDialog.NEW_TYPE -> EventTypeDialog(
            initial = null,
            onDismiss = { dialog = null },
            onSave = { draft, onError -> vm.saveType(null, draft, onError) { dialog = null } },
        )
        EditorDialog.EDIT_TYPE -> {
            val type = state.selectedType
            if (type == null) {
                dialog = null
            } else {
                EventTypeDialog(
                    initial = type,
                    onDismiss = { dialog = null },
                    onSave = { draft, onError -> vm.saveType(type.id, draft, onError) { dialog = null } },
                    onDelete = { vm.deleteType(type.id) { dialog = null } },
                )
            }
        }
        null -> Unit
    }
}

/** Сетка типов: свёрнута до нескольких штук, выбранный тип всегда виден. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeGrid(
    types: List<EventType>,
    selected: com.kanbanboard.mobile.core.model.EventTypeId?,
    expanded: Boolean,
    onSelect: (EventType) -> Unit,
) {
    val visible = if (expanded || types.size <= COLLAPSED_TYPES) {
        types
    } else {
        val head = types.take(COLLAPSED_TYPES)
        val chosen = types.firstOrNull { it.id == selected }
        if (chosen != null && chosen !in head) head.dropLast(1) + chosen else head
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        visible.forEach { type ->
            AppChip(
                text = type.title,
                selected = type.id == selected,
                onClick = { onSelect(type) },
                dotColor = Color(type.color),
            )
        }
    }
}
