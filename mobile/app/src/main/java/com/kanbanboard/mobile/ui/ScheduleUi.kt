package com.kanbanboard.mobile.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kanbanboard.mobile.model.AppTheme
import com.kanbanboard.mobile.model.EventVisibility
import com.kanbanboard.mobile.model.EventType
import com.kanbanboard.mobile.model.Task
import com.kanbanboard.mobile.model.TaskStatus
import com.kanbanboard.mobile.model.TeamEvent
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class AgendaItem(
    val key: String,
    val date: LocalDate,
    val time: String,
    val title: String,
    val detail: String,
    val category: String,
    val color: Long,
    val eventId: Long? = null,
)

private val dateLabel = DateTimeFormatter.ofPattern("d MMMM, EEEE", Locale.forLanguageTag("ru"))
private val eventColors = listOf(0xFF6576DF, 0xFFDA8A37, 0xFF38A585, 0xFFC65F68, 0xFF8B6AC8,
    0xFF3B82F6, 0xFF14B8A6, 0xFFF97316, 0xFFEAB308, 0xFFEC4899, 0xFF6366F1, 0xFF84CC16)
private val reminderOptions = listOf(10080 to "За неделю", 2880 to "За 2 дня", 1440 to "За день",
    360 to "За 6 часов", 120 to "За 2 часа", 60 to "За час", 30 to "За 30 минут", 10 to "За 10 минут")

@Composable
fun ScheduleTimelineScreen(
    viewModel: AppViewModel,
    tasks: List<Task>,
    onCreateEvent: () -> Unit,
    onEventClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by rememberSaveable { mutableStateOf<EventVisibility?>(null) }
    val group = viewModel.uiState.studentGroup
    val types = viewModel.uiState.eventTypes.associateBy { it.id }
    val lessons = viewModel.lessons(group).mapNotNull { lesson ->
        runCatching {
            AgendaItem(
                "lesson-${lesson.date}-${lesson.time}", LocalDate.parse(lesson.date),
                lesson.time.substringBefore("–"), lesson.title,
                "${lesson.kind.title} · группа $group · ${lesson.room} · ${lesson.teacher}",
                "Пара", lesson.kind.color,
            )
        }.getOrNull()
    }
    val events = viewModel.visibleEvents().filter { filter == null || it.visibility == filter }.mapNotNull { event ->
        runCatching {
            val type = types[event.typeId]
            AgendaItem(
                "event-${event.id}", LocalDate.parse(event.date), "${event.time}–${event.endTime}", event.title,
                listOfNotNull(type?.title, event.visibility.title, event.group?.let { "группа $it" }).joinToString(" · "),
                "Событие", type?.color ?: 0xFF6576DF, event.id,
            )
        }.getOrNull()
    }
    val taskItems = tasks.filter { it.status != TaskStatus.DONE }.mapNotNull { task ->
        val date = runCatching { LocalDate.parse(task.deadline) }.getOrNull()
            ?: runCatching { LocalDate.parse(task.deadline, DateTimeFormatter.ofPattern("dd.MM.yyyy")) }.getOrNull()
            ?: return@mapNotNull null
        val deadlineTime = task.dueAt?.let { runCatching { java.time.OffsetDateTime.parse(it).toLocalTime().toString() }.getOrNull() } ?: "23:59"
        AgendaItem("task-${task.id}", date, deadlineTime, task.title, "Задача · ${task.assignee}", "Дедлайн", 0xFFC65F68)
    }
    val visibleLessons = if (filter == null || filter == EventVisibility.GROUP) lessons else emptyList()
    val visibleTasks = if (filter == null || filter == EventVisibility.TEAM) taskItems else emptyList()
    val agenda = (events + visibleLessons + visibleTasks)
        .sortedWith(compareBy<AgendaItem> { it.date }.thenBy { it.time })

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Все события · группа $group", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Button(onClick = onCreateEvent) { Text("+ Событие") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(filter == null, onClick = { filter = null }, label = { Text("Все") })
            EventVisibility.entries.forEach { visibility ->
                FilterChip(filter == visibility, onClick = { filter = visibility }, label = { Text(visibility.title) })
            }
        }
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (agenda.isEmpty()) item { Text("На выбранный период событий нет", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            var previous: LocalDate? = null
            agenda.forEach { entry ->
                if (entry.date != previous) {
                    item("date-${entry.date}") {
                        Text(entry.date.format(dateLabel).replaceFirstChar { it.uppercase() }, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                    }
                    previous = entry.date
                }
                item(entry.key) { AgendaCard(entry, onEventClick) }
            }
        }
    }
}

@Composable
private fun AgendaCard(item: AgendaItem, onEventClick: (Long) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().then(item.eventId?.let { id -> Modifier.clickable { onEventClick(id) } } ?: Modifier), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(68.dp).clip(RoundedCornerShape(4.dp)).background(Color(item.color)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("${item.time} · ${item.category}", color = Color(item.color), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(item.title, fontWeight = FontWeight.SemiBold)
                Text(item.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { SimpleTopBar("Настройки", onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                Text("Тема оформления", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(viewModel.uiState.theme == AppTheme.LIGHT, onClick = { viewModel.setTheme(AppTheme.LIGHT) }, label = { Text("Светлая") })
                    FilterChip(viewModel.uiState.theme == AppTheme.DARK, onClick = { viewModel.setTheme(AppTheme.DARK) }, label = { Text("Тёмная") })
                    FilterChip(viewModel.uiState.theme == AppTheme.AMOLED, onClick = { viewModel.setTheme(AppTheme.AMOLED) }, label = { Text("AMOLED") })
                }
            }
            item {
                Text("Учебная группа", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("Определяет пары и групповые события в расписании", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("203", "204").forEach { group ->
                        FilterChip(viewModel.uiState.studentGroup == group, onClick = { viewModel.setStudentGroup(group) }, label = { Text("Группа $group") })
                    }
                }
            }
        }
    }
}

@Composable
fun CreateEventScreen(viewModel: AppViewModel, onBack: () -> Unit, onCreated: (TeamEvent) -> Unit) {
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(1).toString()) }
    var time by rememberSaveable { mutableStateOf("18:00") }
    var endTime by rememberSaveable { mutableStateOf("19:00") }
    var description by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var visibility by rememberSaveable { mutableStateOf(EventVisibility.PERSONAL) }
    var selectedType by rememberSaveable { mutableStateOf("meeting") }
    var showCreateType by rememberSaveable { mutableStateOf(false) }
    var showEditType by rememberSaveable { mutableStateOf(false) }
    var showAllTypes by rememberSaveable { mutableStateOf(false) }
    var showReminderInput by rememberSaveable { mutableStateOf(false) }
    var manualTimeField by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerOpen by remember { mutableStateOf(false) }
    var reminders by remember { mutableStateOf(setOf(60, 30)) }
    var error by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }

    val chooseDate = {
        if (!pickerOpen) {
            pickerOpen = true
            val initial = LocalDate.parse(date)
            DatePickerDialog(context, { _, year, month, day -> date = LocalDate.of(year, month + 1, day).toString() },
                initial.year, initial.monthValue - 1, initial.dayOfMonth).apply { setOnDismissListener { pickerOpen = false } }.show()
        }
    }
    val chooseTime = {
        if (!pickerOpen) {
            pickerOpen = true
            val initial = LocalTime.parse(time)
            TimePickerDialog(context, { _, hour, minute -> time = "%02d:%02d".format(hour, minute) }, initial.hour, initial.minute, true)
                .apply { setOnDismissListener { pickerOpen = false } }.show()
        }
    }
    val chooseEndTime = {
        if (!pickerOpen) {
            pickerOpen = true
            val initial = LocalTime.parse(endTime)
            TimePickerDialog(context, { _, hour, minute -> endTime = "%02d:%02d".format(hour, minute) }, initial.hour, initial.minute, true)
                .apply { setOnDismissListener { pickerOpen = false } }.show()
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
            TextButton(onClick = onBack) { Text("← Назад") }
            Text("Новое событие", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp))
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { OutlinedTextField(title, { title = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item { OutlinedTextField(description, { description = it }, label = { Text("Описание события") }, modifier = Modifier.fillMaxWidth(), minLines = 3) }
            item { OutlinedTextField(location, { location = it }, label = { Text("Местоположение / ссылка") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
            item {
                OutlinedButton(onClick = chooseDate, modifier = Modifier.fillMaxWidth()) { Text("Дата: ${formatRuDate(date)}") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = chooseTime, modifier = Modifier.weight(1f)) { Text("С $time") }
                    OutlinedButton(onClick = chooseEndTime, modifier = Modifier.weight(1f)) { Text("До $endTime") }
                }
                TextButton(onClick = { manualTimeField = "start" }) { Text("Ввести время начала вручную") }
                TextButton(onClick = { manualTimeField = "end" }) { Text("Ввести время окончания вручную") }
            }
            item {
                Text("Для кого", fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EventVisibility.entries.forEach { item ->
                        FilterChip(visibility == item, onClick = { visibility = item }, label = { Text(item.title) })
                    }
                }
                if (visibility == EventVisibility.GROUP) Text("Группа ${viewModel.uiState.studentGroup}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            item {
                Text("Тип события", fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (if (showAllTypes) viewModel.uiState.eventTypes else viewModel.uiState.eventTypes.take(4)).forEach { type ->
                        FilterChip(selectedType == type.id, onClick = {
                            selectedType = type.id
                            reminders = type.defaultReminders
                        }, label = { Text(type.title) })
                    }
                }
                if (viewModel.uiState.eventTypes.size > 4) TextButton(onClick = { showAllTypes = !showAllTypes }) {
                    Text(if (showAllTypes) "Свернуть" else "Показать все (${viewModel.uiState.eventTypes.size})")
                }
            }
            item { OutlinedButton(onClick = { showCreateType = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Создать свой тип") } }
            if (viewModel.uiState.eventTypes.any { it.id == selectedType && it.custom }) item {
                TextButton(onClick = { showEditType = true }) { Text("Редактировать выбранный тип") }
            }
            item {
                Text("Напомнить", fontWeight = FontWeight.Bold)
                Text("Можно выбрать несколько интервалов", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    reminderOptions.forEach { (minutes, label) ->
                        FilterChip(minutes in reminders, onClick = {
                            reminders = if (minutes in reminders) reminders - minutes else reminders + minutes
                        }, label = { Text(label) })
                    }
                    reminders.filter { value -> reminderOptions.none { it.first == value } }.sorted().forEach { value ->
                        FilterChip(true, onClick = { reminders = reminders - value }, label = { Text("За $value мин ×") })
                    }
                }
                TextButton(onClick = { showReminderInput = true }) { Text("+ Свой интервал") }
            }
            if (error) item { Text("Проверьте название, дату и время: окончание должно быть позже начала", color = MaterialTheme.colorScheme.error) }
            item {
                Button(onClick = {
                    if (submitting) return@Button
                    submitting = true
                    viewModel.submitEvent(title, date, time, visibility, selectedType, reminders, endTime, description, location) { event ->
                        if (event == null) { error = true; submitting = false } else onCreated(event)
                    }
                }, modifier = Modifier.fillMaxWidth(), enabled = title.isNotBlank() && !submitting) {
                    Text(if (submitting) "Сохраняем…" else "Создать событие")
                }
            }
        }
    }
    if (showCreateType) {
        CreateTypeDialog(
            onDismiss = { showCreateType = false },
            onCreate = viewModel::submitEventType,
            onCreated = { type ->
                selectedType = type.id
                reminders = type.defaultReminders
                showCreateType = false
            },
        )
    }
    if (showEditType) {
        val selected = viewModel.uiState.eventTypes.firstOrNull { it.id == selectedType && it.custom }
        if (selected != null) EditTypeDialog(selected, onDismiss = { showEditType = false }, onSave = { name, color, onResult ->
            viewModel.submitUpdateEventType(selected.id, name, color) { saved ->
                if (saved) showEditType = false
                onResult(saved)
            }
        })
    }
    if (showReminderInput) ReminderInputDialog(onDismiss = { showReminderInput = false }, onAdd = {
        reminders = reminders + it
        showReminderInput = false
    })
    manualTimeField?.let { field -> ManualTimeDialog(if (field == "start") time else endTime,
        onDismiss = { manualTimeField = null }, onSave = {
            if (field == "start") time = it else endTime = it
            manualTimeField = null
        }) }
}

@Composable
private fun CreateTypeDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Long, Set<Int>, (EventType?) -> Unit) -> Unit,
    onCreated: (EventType) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableLongStateOf(eventColors.first()) }
    var reminders by remember { mutableStateOf(setOf<Int>()) }
    var duplicate by remember { mutableStateOf(false) }
    var showReminderInput by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый тип события") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it; duplicate = false }, label = { Text("Название типа") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                if (duplicate) Text("Такой тип уже существует", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                Text("Цвет", fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    eventColors.forEach { option ->
                        FilterChip(color == option, onClick = { color = option }, label = { Text("●", color = Color(option)) })
                    }
                }
                Text("Напоминания по умолчанию", fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    reminderOptions.forEach { (minutes, label) ->
                        FilterChip(minutes in reminders, onClick = {
                            reminders = if (minutes in reminders) reminders - minutes else reminders + minutes
                        }, label = { Text(label) })
                    }
                    reminders.filter { value -> reminderOptions.none { it.first == value } }.sorted().forEach { value ->
                        FilterChip(true, onClick = { reminders = reminders - value }, label = { Text("За $value мин ×") })
                    }
                }
                TextButton(onClick = { showReminderInput = true }) { Text("+ Свой интервал") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (creating) return@TextButton
                creating = true
                onCreate(name, color, reminders) { created ->
                    if (created == null) { duplicate = true; creating = false } else onCreated(created)
                }
            }, enabled = name.isNotBlank() && !creating) { Text(if (creating) "Создаём…" else "Создать тип") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
    if (showReminderInput) ReminderInputDialog(onDismiss = { showReminderInput = false }, onAdd = {
        reminders = reminders + it
        showReminderInput = false
    })
}

private fun formatRuDate(value: String): String = runCatching {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
}.getOrDefault(value)

@Composable
private fun ManualTimeDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var hours by rememberSaveable(initial) { mutableStateOf(initial.substringBefore(':')) }
    var minutes by rememberSaveable(initial) { mutableStateOf(initial.substringAfter(':')) }
    val hour = hours.toIntOrNull()
    val minute = minutes.toIntOrNull()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("ВВЕДИТЕ ВРЕМЯ") }, text = {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(hours, { hours = it.filter(Char::isDigit).take(2) }, label = { Text("Часы") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(2) }, label = { Text("Минуты") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
        }
    }, confirmButton = { TextButton(onClick = { onSave("%02d:%02d".format(hour, minute)) },
        enabled = hour != null && hour in 0..23 && minute != null && minute in 0..59) { Text("Готово") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

@Composable
private fun ReminderInputDialog(onDismiss: () -> Unit, onAdd: (Int) -> Unit) {
    var amount by rememberSaveable { mutableStateOf("") }
    var multiplier by rememberSaveable { mutableIntStateOf(1) }
    val minutes = amount.toIntOrNull()?.let { if (it > 0 && it <= 10080 / multiplier) it * multiplier else null }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Свой интервал напоминания") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(amount, { amount = it.filter(Char::isDigit).take(5) }, label = { Text("Количество") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to "Минуты", 60 to "Часы", 1440 to "Дни").forEach { (value, label) ->
                    FilterChip(multiplier == value, onClick = { multiplier = value }, label = { Text(label) })
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { minutes?.let(onAdd) }, enabled = minutes != null) { Text("Добавить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

@Composable
private fun EditTypeDialog(type: EventType, onDismiss: () -> Unit, onSave: (String, Long, (Boolean) -> Unit) -> Unit) {
    var name by rememberSaveable(type.id) { mutableStateOf(type.title) }
    var color by rememberSaveable(type.id) { mutableLongStateOf(type.color) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Редактировать тип") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth())
            Text("Цвет маркировки")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                eventColors.forEach { option ->
                    FilterChip(color == option, onClick = { color = option }, label = { Text("●", color = Color(option)) })
                }
            }
            if (error) Text("Не удалось сохранить тип", color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(onClick = { onSave(name, color) { error = !it } }, enabled = name.isNotBlank()) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

@Composable
fun EventDetailScreen(event: TeamEvent?, type: EventType?, onBack: () -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        topBar = { SimpleTopBar("Событие", onBack) }) { padding ->
        if (event == null) {
            Text("Событие не найдено", modifier = Modifier.padding(padding).padding(20.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(type?.title ?: "Событие", color = Color(type?.color ?: 0xFF6576DF), fontWeight = FontWeight.Bold)
                            Text(event.title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text("${formatRuDate(event.date)} · ${event.time}–${event.endTime}")
                            Text("Для кого: ${event.visibility.title}${event.group?.let { " $it" } ?: ""}")
                            if (event.location.isNotBlank()) Text("Место: ${event.location}")
                            Text(event.description.ifBlank { "Описание не добавлено" },
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
