package com.kanbanboard.mobile.feature.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kanbanboard.mobile.core.data.ScheduleRepository
import com.kanbanboard.mobile.core.data.SessionRepository
import com.kanbanboard.mobile.core.model.EventDraft
import com.kanbanboard.mobile.core.model.EventId
import com.kanbanboard.mobile.core.model.EventType
import com.kanbanboard.mobile.core.model.EventTypeDraft
import com.kanbanboard.mobile.core.model.EventTypeId
import com.kanbanboard.mobile.core.model.EventVisibility
import com.kanbanboard.mobile.core.model.ReminderKeys
import com.kanbanboard.mobile.core.model.Session
import com.kanbanboard.mobile.core.model.StudyGroup
import com.kanbanboard.mobile.core.policy.EventPolicy
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EventEditorUiState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val me: Session? = null,
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val startDate: LocalDate = LocalDate.now(),
    val startTime: LocalTime = LocalTime.of(18, 0),
    val hasEnd: Boolean = false,
    val endDate: LocalDate = LocalDate.now(),
    val endTime: LocalTime = LocalTime.of(19, 0),
    val visibility: EventVisibility = EventVisibility.PERSONAL,
    val group: StudyGroup = StudyGroup.G203,
    val typeId: EventTypeId? = null,
    val types: List<EventType> = emptyList(),
    val reminders: Set<Int> = emptySet(),
    /** Пользователь менял напоминания вручную — тогда выбор типа их не перезаписывает. */
    val remindersTouched: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean get() = !loading && !saving && title.isNotBlank() && typeId != null
    val selectedType: EventType? get() = types.firstOrNull { it.id == typeId }
    fun canUseGroup(group: StudyGroup): Boolean = me?.let { EventPolicy.canCreate(it, EventVisibility.GROUP, group) } == true
    fun canEditType(type: EventType): Boolean = me?.let { EventPolicy.canEditType(it, type) } == true
}

class EventEditorViewModel(
    private val eventId: EventId?,
    initialDate: LocalDate,
    private val sessions: SessionRepository,
    private val schedule: ScheduleRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        EventEditorUiState(isEdit = eventId != null, startDate = initialDate, endDate = initialDate),
    )
    val state: StateFlow<EventEditorUiState> = _state.asStateFlow()

    private val _saved = Channel<Unit>(Channel.CONFLATED)
    val saved: Flow<Unit> = _saved.receiveAsFlow()

    init {
        viewModelScope.launch { load() }
        // Список типов живой: созданный или изменённый тип сразу появляется в форме.
        viewModelScope.launch { schedule.eventTypes.collect { types -> _state.update { it.copy(types = types) } } }
    }

    private suspend fun load() {
        val me = sessions.session.value
        val types = schedule.eventTypes.first()
        if (me == null) {
            _state.update { it.copy(loading = false, error = "Войдите снова") }
            return
        }
        if (eventId == null) {
            val default = types.firstOrNull()
            _state.update {
                it.copy(
                    loading = false,
                    me = me,
                    types = types,
                    group = me.user.studyGroup,
                    typeId = default?.id,
                    reminders = default?.defaultReminders.orEmpty(),
                )
            }
            return
        }
        val event = schedule.event(eventId).first()
        if (event == null || event.masked) {
            _state.update { it.copy(loading = false, me = me, error = "Событие недоступно для редактирования") }
            return
        }
        val reminders = schedule.myReminders.first()[ReminderKeys.event(eventId)].orEmpty()
        val zone = ZoneId.systemDefault()
        val start = event.startsAt.atZone(zone)
        val end = event.endsAt?.atZone(zone)
        _state.update {
            it.copy(
                loading = false,
                me = me,
                types = types,
                title = event.title,
                description = event.description,
                location = event.location,
                startDate = start.toLocalDate(),
                startTime = start.toLocalTime().withSecond(0).withNano(0),
                hasEnd = end != null,
                endDate = end?.toLocalDate() ?: start.toLocalDate(),
                endTime = end?.toLocalTime()?.withSecond(0)?.withNano(0) ?: start.toLocalTime().plusHours(1),
                visibility = event.visibility,
                group = event.group ?: me.user.studyGroup,
                typeId = event.typeId,
                reminders = reminders,
                remindersTouched = true,
            )
        }
    }

    fun setTitle(value: String) = _state.update { it.copy(title = value.take(200), error = null) }
    fun setDescription(value: String) = _state.update { it.copy(description = value.take(5000)) }
    fun setLocation(value: String) = _state.update { it.copy(location = value.take(300)) }
    fun setVisibility(value: EventVisibility) = _state.update { it.copy(visibility = value, error = null) }
    fun setGroup(value: StudyGroup) = _state.update { it.copy(group = value, error = null) }

    fun setStartDate(value: LocalDate) = _state.update { s ->
        // Окончание сдвигается вместе с началом, чтобы не получить «конец раньше начала».
        val shift = java.time.temporal.ChronoUnit.DAYS.between(s.startDate, value)
        s.copy(startDate = value, endDate = s.endDate.plusDays(shift), error = null)
    }

    fun setStartTime(value: LocalTime) = _state.update { it.copy(startTime = value, error = null) }

    fun setHasEnd(value: Boolean) = _state.update { s ->
        if (value && !s.hasEnd) {
            val end = LocalDateTime.of(s.startDate, s.startTime).plusHours(1)
            s.copy(hasEnd = true, endDate = end.toLocalDate(), endTime = end.toLocalTime(), error = null)
        } else {
            s.copy(hasEnd = value, error = null)
        }
    }

    fun setEndDate(value: LocalDate) = _state.update { it.copy(endDate = value, error = null) }
    fun setEndTime(value: LocalTime) = _state.update { it.copy(endTime = value, error = null) }

    fun selectType(type: EventType) = _state.update { s ->
        s.copy(typeId = type.id, reminders = if (s.remindersTouched) s.reminders else type.defaultReminders)
    }

    fun setReminders(value: Set<Int>) = _state.update { it.copy(reminders = value, remindersTouched = true) }

    /** Создать (id == null) или изменить тип; при успехе выбрать его. onDone — закрыть диалог. */
    fun saveType(id: EventTypeId?, draft: EventTypeDraft, onError: (String) -> Unit, onDone: () -> Unit) {
        viewModelScope.launch {
            schedule.saveEventType(id, draft)
                .onSuccess { type ->
                    _state.update { s ->
                        val types = if (s.types.any { it.id == type.id }) s.types.map { if (it.id == type.id) type else it } else s.types + type
                        s.copy(types = types)
                    }
                    selectType(type)
                    onDone()
                }
                .onFailure { onError(it.message ?: "Не удалось сохранить тип") }
        }
    }

    /** Удаляет свой тип; выбранным становится первый оставшийся. */
    fun deleteType(id: EventTypeId, onDone: () -> Unit) {
        viewModelScope.launch {
            schedule.deleteEventType(id)
                .onSuccess {
                    _state.update { s ->
                        val types = s.types.filterNot { it.id == id }
                        s.copy(types = types, typeId = if (s.typeId == id) types.firstOrNull()?.id else s.typeId)
                    }
                    onDone()
                }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Не удалось удалить тип") } }
        }
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        val typeId = s.typeId ?: return
        val zone = ZoneId.systemDefault()
        val startsAt = LocalDateTime.of(s.startDate, s.startTime).atZone(zone).toInstant()
        val endsAt = if (s.hasEnd) LocalDateTime.of(s.endDate, s.endTime).atZone(zone).toInstant() else null
        val draft = EventDraft(
            title = s.title,
            description = s.description,
            location = s.location,
            startsAt = startsAt,
            endsAt = endsAt,
            visibility = s.visibility,
            group = if (s.visibility == EventVisibility.GROUP) s.group else null,
            typeId = typeId,
            myReminders = s.reminders,
        )
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val result = if (eventId == null) {
                schedule.createEvent(draft).map { }
            } else {
                schedule.updateEvent(eventId, draft).also { update ->
                    if (update.isSuccess) schedule.setMyReminders(ReminderKeys.event(eventId), s.reminders)
                }
            }
            result
                .onSuccess { _saved.trySend(Unit) }
                .onFailure { e -> _state.update { it.copy(saving = false, error = e.message) } }
        }
    }
}
