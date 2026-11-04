package com.kanbanboard.mobile.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kanbanboard.mobile.core.designsystem.AppButton
import com.kanbanboard.mobile.core.designsystem.AppButtonKind
import com.kanbanboard.mobile.core.designsystem.AppChip
import com.kanbanboard.mobile.core.designsystem.Caption
import com.kanbanboard.mobile.core.designsystem.ext
import com.kanbanboard.mobile.core.model.ReminderPresets

private enum class ReminderUnit(val title: String, val minutes: Int) {
    MINUTES("мин", 1),
    HOURS("ч", 60),
    DAYS("дн", 1440),
    WEEKS("нед", 10080),
}

/**
 * Выбор моих напоминаний: готовые интервалы и свой (число + единица).
 * Пустой набор — напоминаний нет.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderDialog(
    title: String,
    initial: Set<Int>,
    onDismiss: () -> Unit,
    onSave: (Set<Int>) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(initial.toList()) }
    var amount by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf(ReminderUnit.HOURS) }
    val custom = amount.toIntOrNull()?.let { it * unit.minutes }
    val customValid = custom != null && custom in 1..ReminderPresets.MAX_MINUTES

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Caption("Напоминания личные — их видите только вы", maxLines = 2)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    (ReminderPresets.all + selected.filter { it !in ReminderPresets.all }).distinct().sorted().forEach { minutes ->
                        val on = minutes in selected
                        AppChip(
                            text = ReminderPresets.label(minutes).removePrefix("за "),
                            selected = on,
                            onClick = { selected = if (on) selected - minutes else selected + minutes },
                        )
                    }
                }
                Caption("свой интервал", modifier = Modifier.padding(top = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { value -> amount = value.filter { it.isDigit() }.take(4) },
                        placeholder = { Text("90") },
                        singleLine = true,
                        isError = amount.isNotEmpty() && !customValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.width(88.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ReminderUnit.entries.forEach { u ->
                            AppChip(text = u.title, selected = unit == u, onClick = { unit = u })
                        }
                    }
                }
                if (amount.isNotEmpty() && !customValid) {
                    Caption("! от 1 минуты до 4 недель", color = MaterialTheme.ext.danger)
                }
                AppButton(
                    "Добавить интервал",
                    onClick = {
                        if (custom != null && customValid && custom !in selected) selected = selected + custom
                        amount = ""
                    },
                    kind = AppButtonKind.SECONDARY,
                    enabled = customValid,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selected.toSet()) }) { Text("Сохранить") } },
        dismissButton = {
            Row {
                if (selected.isNotEmpty()) {
                    TextButton(onClick = { selected = emptyList() }) { Text("Очистить", color = MaterialTheme.ext.danger) }
                }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}
