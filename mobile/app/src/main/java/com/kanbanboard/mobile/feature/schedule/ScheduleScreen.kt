package com.kanbanboard.mobile.feature.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kanbanboard.mobile.core.designsystem.AppTopBar
import com.kanbanboard.mobile.core.designsystem.Caption
import com.kanbanboard.mobile.core.designsystem.EmptyState
import com.kanbanboard.mobile.core.designsystem.Hairline
import com.kanbanboard.mobile.core.designsystem.SpecRow
import com.kanbanboard.mobile.core.designsystem.UserAvatar
import com.kanbanboard.mobile.core.designsystem.color
import com.kanbanboard.mobile.core.designsystem.ext
import com.kanbanboard.mobile.core.model.EventId
import com.kanbanboard.mobile.core.model.Lesson
import com.kanbanboard.mobile.core.model.ReminderPresets
import com.kanbanboard.mobile.core.model.TaskId
import com.kanbanboard.mobile.core.notifications.rememberNotificationPermissionRequest
import com.kanbanboard.mobile.core.time.Formats
import com.kanbanboard.mobile.di.LocalAppContainer
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.launch

private const val PAGES = 2001
private const val CENTER = 1000
private val ru = Locale.forLanguageTag("ru")

@Composable
fun ScheduleRouteScreen(
    onOpenEvent: (EventId) -> Unit,
    onCreateEvent: (LocalDate) -> Unit,
    onOpenTask: (TaskId) -> Unit,
) {
    val c = LocalAppContainer.current
    val vm: ScheduleViewModel = viewModel {
        ScheduleViewModel(c.sessionRepository, c.teamRepository, c.scheduleRepository, c.boardRepository)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    val askPermission = rememberNotificationPermissionRequest()

    ScheduleScreen(
        state = state,
        snackbar = snackbar,
        onSelectDate = vm::selectDate,
        onSelectView = vm::selectView,
        onToggleCategory = vm::toggleCategory,
        onSaveReminders = { key, minutes ->
            if (minutes.isNotEmpty()) askPermission()
            vm.setReminders(key, minutes)
        },
        onOpenEvent = onOpenEvent,
        onCreateEvent = onCreateEvent,
        onOpenTask = onOpenTask,
    )
}

@Composable
private fun ScheduleScreen(
    state: ScheduleUiState,
    snackbar: SnackbarHostState,
    onSelectDate: (LocalDate) -> Unit,
    onSelectView: (ScheduleView) -> Unit,
    onToggleCategory: (AgendaCategory) -> Unit,
    onSaveReminders: (String, Set<Int>) -> Unit,
    onOpenEvent: (EventId) -> Unit,
    onCreateEvent: (LocalDate) -> Unit,
    onOpenTask: (TaskId) -> Unit,
) {
    val base = remember { LocalDate.now() }
    val pager = rememberPagerState(
        initialPage = CENTER + ChronoUnit.DAYS.between(base, state.selectedDate).toInt().coerceIn(-CENTER, CENTER),
    ) { PAGES }
    val scope = rememberCoroutineScope()
    fun dateOf(page: Int): LocalDate = base.plusDays((page - CENTER).toLong())
    fun goTo(date: LocalDate) {
        val page = (CENTER + ChronoUnit.DAYS.between(base, date)).toInt().coerceIn(0, PAGES - 1)
        scope.launch { pager.animateScrollToPage(page) }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { onSelectDate(dateOf(it)) }
    }

    var lessonDialog by remember { mutableStateOf<AgendaItem?>(null) }
    var reminderItem by remember { mutableStateOf<AgendaItem?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            AppTopBar(
                title = "Расписание",
                actions = { TextButton(onClick = { goTo(LocalDate.now()) }) { Text("Сегодня") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onCreateEvent(state.selectedDate) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Событие") },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ViewPicker(state, onSelectView, Modifier.weight(1f))
                CategoryFilter(state.categories, enabled = !state.lessonsOnly, onToggle = onToggleCategory)
            }
            WeekStrip(state, onPick = ::goTo)
            state.lessonsWarning?.let { OfflineBanner(it) }
            HorizontalPager(
                state = pager,
                key = { it },
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                DayPage(
                    date = dateOf(page),
                    state = state,
                    onItemClick = { item ->
                        when {
                            item.lesson != null -> lessonDialog = item
                            item.event != null -> onOpenEvent(item.event.id)
                            item.taskId != null -> onOpenTask(item.taskId)
                        }
                    },
                    onBell = { item -> reminderItem = item },
                )
            }
        }
    }

    lessonDialog?.let { item ->
        val lesson = item.lesson
        if (lesson != null) {
            LessonDialog(
                lesson = lesson,
                reminders = item.reminders,
                onDismiss = { lessonDialog = null },
                onReminders = { lessonDialog = null; reminderItem = item },
            )
        }
    }
    reminderItem?.let { item ->
        ReminderDialog(
            title = item.title,
            initial = item.reminders,
            onDismiss = { reminderItem = null },
            onSave = { minutes ->
                reminderItem = null
                onSaveReminders(item.key, minutes)
            },
        )
    }
}

/**
 * Выпадающее меню расписания:
 *  - «Моё расписание» — полное своё расписание (пары своей группы, события, дедлайны);
 *  - «Группа 203» / «Группа 204» / «Обе группы» — только пары групп, фильтр категорий заблокирован;
 *  - участники — расписание человека с учётом его приватности.
 * Высота меню ограничена: при большой команде участники прокручиваются внутри, меню не закрывает экран.
 */
@Composable
private fun ViewPicker(
    state: ScheduleUiState,
    onSelect: (ScheduleView) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val person = state.person
    val me = state.me?.user
    val view = state.view
    val label = when (view) {
        ScheduleView.Mine -> "Моё расписание"
        is ScheduleView.Groups -> view.filter.title
        is ScheduleView.Person -> person?.fullName.orEmpty()
    }
    Box(modifier) {
        FilterChip(
            selected = true,
            onClick = { open = true },
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = {
                if (view is ScheduleView.Groups) {
                    Icon(Icons.Outlined.Groups, contentDescription = null, modifier = Modifier.size(20.dp))
                } else {
                    UserAvatar(person?.fullName ?: "?", size = 24.dp, photo = person?.photo)
                }
            },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.heightIn(max = MENU_MAX_HEIGHT),
        ) {
            DropdownMenuItem(
                text = { Text("Моё расписание") },
                leadingIcon = { UserAvatar(me?.fullName ?: "?", size = 24.dp, photo = me?.photo) },
                trailingIcon = if (view == ScheduleView.Mine) {
                    { Icon(Icons.Filled.Check, contentDescription = "Выбрано") }
                } else null,
                onClick = { open = false; onSelect(ScheduleView.Mine) },
            )
            HorizontalDivider()
            MenuHeader("Пары группы")
            GroupFilter.entries.forEach { filter ->
                DropdownMenuItem(
                    text = { Text(filter.title) },
                    leadingIcon = { CheckSlot(view == ScheduleView.Groups(filter)) },
                    onClick = { open = false; onSelect(ScheduleView.Groups(filter)) },
                )
            }
            val others = state.members.filter { it.id != me?.id }
            if (others.isNotEmpty()) {
                HorizontalDivider()
                MenuHeader("Участники")
                others.forEach { user ->
                    DropdownMenuItem(
                        text = { Text(user.fullName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { UserAvatar(user.fullName, size = 24.dp, photo = user.photo) },
                        trailingIcon = if (view is ScheduleView.Person && person?.id == user.id) {
                            { Icon(Icons.Filled.Check, contentDescription = "Выбрано") }
                        } else null,
                        onClick = { open = false; onSelect(ScheduleView.Person(user.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Максимальная высота меню расписания: остальное прокручивается внутри. */
private val MENU_MAX_HEIGHT = 420.dp

@Composable
private fun CheckSlot(checked: Boolean) {
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        if (checked) Icon(Icons.Filled.Check, contentDescription = "Выбрано")
    }
}

/** Компактный фильтр категорий: кнопка с меню и галочками. */
@Composable
private fun CategoryFilter(selected: Set<AgendaCategory>, enabled: Boolean, onToggle: (AgendaCategory) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val all = selected.size == AgendaCategory.entries.size
    Box {
        // В режиме «только пары группы» фильтр заблокирован: показываются только пары.
        FilterChip(
            selected = enabled && !all,
            enabled = enabled,
            onClick = { open = true },
            label = {
                Text(
                    when {
                        !enabled -> "Пары"
                        all -> "Всё"
                        else -> "${selected.size} из ${AgendaCategory.entries.size}"
                    },
                )
            },
            leadingIcon = { Icon(Icons.Outlined.FilterList, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = open && enabled, onDismissRequest = { open = false }) {
            AgendaCategory.entries.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.title) },
                    leadingIcon = { CheckSlot(category in selected) },
                    // Меню не закрывается: можно отметить несколько пунктов подряд.
                    onClick = { onToggle(category) },
                )
            }
        }
    }
}

@Composable
private fun WeekStrip(state: ScheduleUiState, onPick: (LocalDate) -> Unit) {
    val monday = state.weekStart()
    val today = LocalDate.now()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onPick(state.selectedDate.minusWeeks(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Прошлая неделя")
        }
        Row(Modifier.weight(1f).height(IntrinsicSize.Min)) {
            (0L..6L).forEach { offset ->
                val date = monday.plusDays(offset)
                val selected = date == state.selectedDate
                val count = state.days[date].orEmpty().size
                val textColor = when {
                    selected -> MaterialTheme.colorScheme.onPrimary
                    date == today -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(MaterialTheme.shapes.large)
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { onPick(date) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.SHORT, ru).replaceFirstChar { it.titlecase(ru) },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("${date.dayOfMonth}", color = textColor, style = MaterialTheme.typography.titleMedium)
                    // Есть ли что-то в этот день: одна-три точки.
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(6.dp)) {
                        repeat(count.coerceAtMost(3)) {
                            Box(
                                Modifier.size(4.dp).clip(CircleShape).background(
                                    if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                    }
                }
            }
        }
        IconButton(onClick = { onPick(state.selectedDate.plusWeeks(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Следующая неделя")
        }
    }
}

/** Показывается только при сбое ИСУ: пары взяты из кэша или шаблона. */
@Composable
private fun OfflineBanner(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun DayPage(
    date: LocalDate,
    state: ScheduleUiState,
    onItemClick: (AgendaItem) -> Unit,
    onBell: (AgendaItem) -> Unit,
) {
    if (state.loading || !state.isLoaded(date)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val items = state.days[date].orEmpty()
    if (items.isEmpty()) {
        EmptyState(icon = Icons.Outlined.EventAvailable, title = "Свободный день")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.key }) { item ->
            AgendaCard(item, onClick = { onItemClick(item) }, onBell = { onBell(item) })
        }
    }
}

/** Карточка пары, события или дедлайна: время слева, цветная полоса вида, название и детали. */
@Composable
fun AgendaCard(item: AgendaItem, onClick: () -> Unit, onBell: (() -> Unit)?) {
    val lesson = item.lesson
    val type = item.type
    val accent = when (item.kind) {
        AgendaKind.LESSON -> lesson?.kind?.color() ?: MaterialTheme.colorScheme.onSurfaceVariant
        AgendaKind.EVENT -> type?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primary
        AgendaKind.BUSY -> MaterialTheme.colorScheme.outline
        AgendaKind.DEADLINE -> MaterialTheme.ext.danger
    }
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.height(IntrinsicSize.Min).padding(start = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.width(52.dp)) {
                Text(Formats.time(item.start), style = MaterialTheme.typography.titleSmall)
                item.end?.let { Caption(it, maxLines = 2) }
            }
            Box(
                Modifier
                    .padding(horizontal = 8.dp)
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(accent),
            )
            Column(Modifier.weight(1f).padding(end = if (onBell == null) 12.dp else 0.dp)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (item.kind == AgendaKind.BUSY) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Caption(item.meta, maxLines = 2)
            }
            if (onBell != null && item.kind != AgendaKind.DEADLINE) {
                IconButton(onClick = onBell) {
                    val has = item.reminders.isNotEmpty()
                    Icon(
                        if (has) Icons.Outlined.NotificationsActive else Icons.Outlined.Notifications,
                        contentDescription = if (has) "Напоминания: ${item.reminders.size}" else "Добавить напоминание",
                        tint = if (has) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LessonDialog(lesson: Lesson, reminders: Set<Int>, onDismiss: () -> Unit, onReminders: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(lesson.title) },
        text = {
            Column {
                Text(lesson.kind.title, color = lesson.kind.color(), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                SpecRow("Время", "${Formats.date(lesson.date)}, ${Formats.time(lesson.start)}–${Formats.time(lesson.end)}")
                Hairline()
                SpecRow("Аудитория", lesson.room.ifBlank { "—" })
                Hairline()
                SpecRow("Преподаватель", lesson.teacher.ifBlank { "—" })
                Hairline()
                SpecRow("Группа", lesson.group.code)
                Hairline()
                SpecRow(
                    "Напомнить",
                    if (reminders.isEmpty()) "Не напоминать" else ReminderPresets.list(reminders),
                )
            }
        },
        confirmButton = { TextButton(onClick = onReminders) { Text("Напоминания") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}
