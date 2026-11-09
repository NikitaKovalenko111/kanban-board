package com.kanbanboard.mobile.feature.schedule

import com.kanbanboard.mobile.core.features.Features

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kanbanboard.mobile.core.designsystem.AppButton
import com.kanbanboard.mobile.core.designsystem.AppButtonKind
import com.kanbanboard.mobile.core.designsystem.AppPalette
import com.kanbanboard.mobile.core.designsystem.Caption
import com.kanbanboard.mobile.core.designsystem.Tag
import com.kanbanboard.mobile.core.designsystem.ext
import com.kanbanboard.mobile.core.model.EventType
import com.kanbanboard.mobile.core.model.EventTypeDraft
import com.kanbanboard.mobile.core.model.ReminderPresets

/**
 * Создание и редактирование своего типа события: название, цвет из палитры на 24 цвета
 * и напоминания по умолчанию (они подставляются в новое событие этого типа).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventTypeDialog(
    initial: EventType?,
    onDismiss: () -> Unit,
    onSave: (EventTypeDraft, onError: (String) -> Unit) -> Unit,
    /** Удаление своего типа; null — кнопки нет (новый или базовый тип). */
    onDelete: (() -> Unit)? = null,
) {
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var color by rememberSaveable { mutableLongStateOf(initial?.color ?: AppPalette.first()) }
    var reminders by rememberSaveable { mutableStateOf(initial?.defaultReminders?.toList().orEmpty()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var sending by rememberSaveable { mutableStateOf(false) }
    var editReminders by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новый тип события" else "Изменить тип") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(40); error = null },
                    label = { Text("Название") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                Caption("Цвет")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppPalette.forEach { option ->
                        val selected = option == color
                        val swatch = Color(option)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(swatch)
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                                    shape = MaterialTheme.shapes.small,
                                )
                                .semantics {
                                    contentDescription = "Цвет ${AppPalette.indexOf(option) + 1}"
                                    this.selected = selected
                                }
                                .clickable { color = option },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = if (swatch.luminance() > 0.5f) Color.Black else Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Caption("Превью")
                    Spacer(Modifier.width(10.dp))
                    Tag(title.ifBlank { "Тип" }, color = Color(color), dot = true)
                }
                Caption("Напоминания по умолчанию")
                Caption(
                    if (reminders.isEmpty()) "Без напоминаний" else ReminderPresets.list(reminders),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                )
                AppButton("Изменить напоминания", onClick = { editReminders = true }, kind = AppButtonKind.SECONDARY, modifier = Modifier.fillMaxWidth())
                error?.let { Caption("! $it", color = MaterialTheme.ext.danger, maxLines = 3) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && !sending,
                onClick = {
                    sending = true
                    onSave(EventTypeDraft(title, color, reminders.toSet())) { message ->
                        error = message
                        sending = false
                    }
                },
            ) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    // Удаление типов есть только в мобилке — заблокировано до решения команды.
                    TextButton(onClick = onDelete, enabled = Features.DELETE_EVENT_TYPE && !sending) {
                        Text(
                            "Удалить",
                            color = if (Features.DELETE_EVENT_TYPE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        )
                    }
                }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )

    if (editReminders) {
        ReminderDialog(
            title = "По умолчанию для типа",
            initial = reminders.toSet(),
            onDismiss = { editReminders = false },
            onSave = { reminders = it.toList(); editReminders = false },
        )
    }
}
