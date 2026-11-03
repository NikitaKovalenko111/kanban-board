package com.kanbanboard.mobile.feature.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kanbanboard.mobile.core.data.ScheduleRepository
import com.kanbanboard.mobile.core.data.SessionRepository
import com.kanbanboard.mobile.core.data.TeamRepository
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
import com.kanbanboard.mobile.core.designsystem.ext
import com.kanbanboard.mobile.core.model.EventId
import com.kanbanboard.mobile.core.model.EventType
import com.kanbanboard.mobile.core.model.EventVisibility
import com.kanbanboard.mobile.core.model.ReminderKeys
import com.kanbanboard.mobile.core.model.ReminderPresets
import com.kanbanboard.mobile.core.model.ScheduleEvent
import com.kanbanboard.mobile.core.model.User
import com.kanbanboard.mobile.core.notifications.rememberNotificationPermissionRequest
import com.kanbanboard.mobile.core.policy.EventPolicy
import com.kanbanboard.mobile.core.time.Formats
import com.kanbanboard.mobile.di.LocalAppContainer
import java.time.ZoneId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EventDetailUiState(
    val loading: Boolean = true,
    val event: ScheduleEvent? = null,
    val type: EventType? = null,
    val owner: User? = null,
    val canEdit: Boolean = false,
    val reminders: Set<Int> = emptySet(),
)

sealed interface EventDetailEvent {
    data class Message(val text: String) : EventDetailEvent
    data object Deleted : EventDetailEvent
}

class EventDetailViewModel(
    private val eventId: EventId,
    sessions: SessionRepository,
    team: TeamRepository,
    private val schedule: ScheduleRepository,
) : ViewModel() {
    private val _events = Channel<EventDetailEvent>(Channel.BUFFERED)
    val events: Flow<EventDetailEvent> = _events.receiveAsFlow()

    val state: StateFlow<EventDetailUiState> = combine(
        schedule.event(eventId),
        sessions.session,
        team.members,
        schedule.eventTypes,
        schedule.myReminders,
    ) { event, session, members, types, reminders ->
        EventDetailUiState(
            loading = false,
            event = event,
            type = types.firstOrNull { it.id == event?.typeId },
            owner = members.firstOrNull { it.user.id == event?.ownerId }?.user,
            canEdit = event != null && session != null && EventPolicy.canEdit(session, event),
            reminders = reminders[ReminderKeys.event(eventId)].orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EventDetailUiState())

    fun setReminders(minutes: Set<Int>) {
        viewModelScope.launch {
            schedule.setMyReminders(ReminderKeys.event(eventId), minutes)
                .onSuccess { _events.trySend(EventDetailEvent.Message(if (minutes.isEmpty()) "Напоминания убраны" else "Напоминания сохранены")) }
                .onFailure { _events.trySend(EventDetailEvent.Message(it.message ?: "Ошибка")) }
        }
    }

    fun delete() {
        viewModelScope.launch {
            schedule.deleteEvent(eventId)
                .onSuccess { _events.trySend(EventDetailEvent.Deleted) }
                .onFailure { _events.trySend(EventDetailEvent.Message(it.message ?: "Не удалось удалить")) }
        }
    }
}

@Composable
fun EventDetailRouteScreen(eventId: EventId, onBack: () -> Unit, onEdit: (EventId) -> Unit) {
    val c = LocalAppContainer.current
    val vm: EventDetailViewModel = viewModel {
        EventDetailViewModel(eventId, c.sessionRepository, c.teamRepository, c.scheduleRepository)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val currentOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is EventDetailEvent.Message -> snackbar.showSnackbar(event.text)
                EventDetailEvent.Deleted -> currentOnBack()
            }
        }
    }
    val askPermission = rememberNotificationPermissionRequest()
    EventDetailScreen(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onEdit = { onEdit(eventId) },
        onDelete = vm::delete,
        onSaveReminders = { minutes ->
            if (minutes.isNotEmpty()) askPermission()
            vm.setReminders(minutes)
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventDetailScreen(
    state: EventDetailUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSaveReminders: (Set<Int>) -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var editReminders by rememberSaveable { mutableStateOf(false) }
    val event = state.event

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            AppTopBar(
                title = if (event?.masked == true) "Занятость" else "Событие",
                navigation = { BackButton(onBack) },
                actions = {
                    if (state.canEdit) {
                        IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "Редактировать") }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Удалить", tint = MaterialTheme.ext.danger)
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
            event == null -> EmptyState(
                icon = Icons.Outlined.EventBusy,
                title = "Событие не найдено",
                text = "Возможно, его удалили.",
                modifier = Modifier.padding(padding),
            )
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Panel(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                    val accent = state.type?.let { Color(it.color) } ?: MaterialTheme.colorScheme.onSurfaceVariant
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Tag(state.type?.title ?: if (event.masked) "Личное" else "Событие", color = accent, dot = true)
                        Tag(
                            when (event.visibility) {
                                EventVisibility.TEAM -> "Вся команда"
                                EventVisibility.GROUP -> "Группа ${event.group?.code}"
                                EventVisibility.PERSONAL -> "Личное"
                            },
                        )
                    }
                    SelectionContainer {
                        Column {
                            Text(
                                if (event.masked) "${state.owner?.fullName ?: "Участник"} занят" else event.title,
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                            if (event.masked) {
                                Caption(
                                    "Личное событие: подробности скрыты настройками приватности владельца",
                                    maxLines = 2,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            } else if (event.partial && event.description.isBlank()) {
                                Caption(
                                    "Владелец показывает только название",
                                    maxLines = 2,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            } else if (event.description.isNotBlank()) {
                                Text(
                                    event.description,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Hairline()
                    SpecRow("Начало", Formats.dateTime(event.startsAt))
                    Hairline()
                    SpecRow("Окончание", event.endsAt?.let { endLabel(event) } ?: "Не указано")
                    if (!event.masked && event.location.isNotBlank()) {
                        Hairline()
                        SpecRow("Место", event.location)
                    }
                    Hairline()
                    SpecRow("Автор", state.owner?.let { "${it.fullName} (@${it.login})" } ?: "—")
                }

                SectionHeader("Мои напоминания", count = state.reminders.size)
                Panel {
                    if (state.reminders.isEmpty()) {
                        Caption("Без напоминаний")
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.reminders.sorted().forEach { Tag(ReminderPresets.label(it), color = MaterialTheme.colorScheme.primary) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    AppButton(
                        if (state.reminders.isEmpty()) "Напомнить" else "Изменить",
                        onClick = { editReminders = true },
                        kind = AppButtonKind.SECONDARY,
                        icon = Icons.Outlined.NotificationsActive,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Caption(
                    "Напоминания личные — их видите только вы",
                    maxLines = 2,
                )
            }
        }
    }

    if (confirmDelete && event != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить событие?") },
            text = { Text("«${event.title}» пропадёт из расписания у всех, кому оно было видно.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Удалить", color = MaterialTheme.ext.danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
    if (editReminders && event != null) {
        ReminderDialog(
            title = if (event.masked) "Напомнить о занятости" else event.title,
            initial = state.reminders,
            onDismiss = { editReminders = false },
            onSave = { minutes ->
                editReminders = false
                onSaveReminders(minutes)
            },
        )
    }
}

private fun endLabel(event: ScheduleEvent): String {
    val end = event.endsAt ?: return "не указано"
    val zone = ZoneId.systemDefault()
    return if (end.atZone(zone).toLocalDate() == event.startsAt.atZone(zone).toLocalDate()) {
        Formats.time(end.atZone(zone).toLocalTime())
    } else {
        Formats.dateTime(end)
    }
}
