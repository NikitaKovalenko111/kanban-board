package com.kanbanboard.mobile.core.data.demo

import com.kanbanboard.mobile.core.model.AppError
import com.kanbanboard.mobile.core.model.EventDraft
import com.kanbanboard.mobile.core.model.EventId
import com.kanbanboard.mobile.core.model.EventType
import com.kanbanboard.mobile.core.model.EventTypeDraft
import com.kanbanboard.mobile.core.model.EventTypeId
import com.kanbanboard.mobile.core.model.EventVisibility
import com.kanbanboard.mobile.core.model.ScheduleEvent
import com.kanbanboard.mobile.core.model.SchedulePrivacy
import com.kanbanboard.mobile.core.model.Session
import com.kanbanboard.mobile.core.model.StudyGroup
import com.kanbanboard.mobile.core.model.UserId
import com.kanbanboard.mobile.core.policy.EventPolicy
import java.time.Duration

/** Справочник базовых типов событий (как начальные записи таблицы event_types на сервере). */
object DemoSchedule {
    val builtInTypes: List<EventType> = listOf(
        EventType(EventTypeId("t-meeting"), "Созвон команды", 0xFF6576DF, null, setOf(60, 15)),
        EventType(EventTypeId("t-deadline"), "Дедлайн", 0xFFD9534F, null, setOf(1440, 120)),
        EventType(EventTypeId("t-study"), "Учёба", 0xFF3A9BDC, null, setOf(30)),
        EventType(EventTypeId("t-personal"), "Личное", 0xFF9C6ADE, null, emptySet()),
    )
}

// ---------- Операции «сервера» над расписанием ----------

private fun reminderKey(userId: UserId, itemKey: String) = "${userId.value}|$itemKey"

private fun validate(session: Session, draft: EventDraft, types: List<EventType>) {
    if (draft.title.isBlank()) throw AppError.Validation("Введите название события")
    if (draft.title.length > 200) throw AppError.Validation("Название длиннее 200 символов")
    if (draft.location.length > 300) throw AppError.Validation("Слишком длинная локация")
    val end = draft.endsAt
    if (end != null && !end.isAfter(draft.startsAt)) throw AppError.Validation("Окончание должно быть позже начала")
    if (end != null && Duration.between(draft.startsAt, end) > Duration.ofDays(14)) {
        throw AppError.Validation("Событие не может длиться больше двух недель")
    }
    if (draft.visibility == EventVisibility.GROUP && draft.group == null) throw AppError.Validation("Выберите группу")
    if (!EventPolicy.canCreate(session, draft.visibility, draft.group)) {
        throw AppError.Forbidden("Групповое событие можно создать только для своей группы")
    }
    if (types.none { it.id == draft.typeId }) throw AppError.Validation("Выберите тип события")
}

internal suspend fun DemoBackend.createEvent(actor: Session, draft: EventDraft): ScheduleEvent = mutate { s ->
    validate(actor, draft, s.eventTypes)
    val event = ScheduleEvent(
        id = EventId(newId()),
        ownerId = actor.user.id,
        title = draft.title.trim(),
        description = draft.description.trim(),
        location = draft.location.trim(),
        startsAt = draft.startsAt,
        endsAt = draft.endsAt,
        visibility = draft.visibility,
        group = if (draft.visibility == EventVisibility.GROUP) draft.group else null,
        typeId = draft.typeId,
        masked = false,
    )
    val reminders = EventPolicy.normalizeReminders(draft.myReminders)
    val key = reminderKey(actor.user.id, com.kanbanboard.mobile.core.model.ReminderKeys.event(event.id))
    s.copy(
        events = s.events + event,
        reminders = if (reminders.isEmpty()) s.reminders else s.reminders + (key to reminders),
    ) to event
}

internal suspend fun DemoBackend.updateEvent(actor: Session, id: EventId, draft: EventDraft) = mutate { s ->
    val current = s.events.firstOrNull { it.id == id } ?: throw AppError.NotFound("Событие не найдено")
    if (!EventPolicy.canEdit(actor, current)) throw AppError.Forbidden("Изменять событие может автор или капитан")
    validate(actor, draft, s.eventTypes)
    val updated = current.copy(
        title = draft.title.trim(),
        description = draft.description.trim(),
        location = draft.location.trim(),
        startsAt = draft.startsAt,
        endsAt = draft.endsAt,
        visibility = draft.visibility,
        group = if (draft.visibility == EventVisibility.GROUP) draft.group else null,
        typeId = draft.typeId,
    )
    s.copy(events = s.events.map { if (it.id == id) updated else it }) to Unit
}

internal suspend fun DemoBackend.deleteEvent(actor: Session, id: EventId) = mutate { s ->
    val current = s.events.firstOrNull { it.id == id } ?: throw AppError.NotFound("Событие не найдено")
    if (!EventPolicy.canEdit(actor, current)) throw AppError.Forbidden("Удалять событие может автор или капитан")
    val itemKey = com.kanbanboard.mobile.core.model.ReminderKeys.event(id)
    s.copy(
        events = s.events.filterNot { it.id == id },
        reminders = s.reminders.filterKeys { !it.endsWith("|$itemKey") },
    ) to Unit
}

internal suspend fun DemoBackend.saveEventType(actor: Session, id: EventTypeId?, draft: EventTypeDraft): EventType = mutate { s ->
    val title = draft.title.trim()
    if (title.isEmpty()) throw AppError.Validation("Введите название типа")
    if (title.length > 40) throw AppError.Validation("Название типа длиннее 40 символов")
    if (s.eventTypes.any { it.id != id && it.title.equals(title, ignoreCase = true) }) {
        throw AppError.Validation("Тип «$title» уже есть")
    }
    val reminders = EventPolicy.normalizeReminders(draft.defaultReminders)
    if (id == null) {
        val type = EventType(EventTypeId(newId()), title, draft.color, actor.user.id, reminders)
        s.copy(eventTypes = s.eventTypes + type) to type
    } else {
        val current = s.eventTypes.firstOrNull { it.id == id } ?: throw AppError.NotFound("Тип не найден")
        if (!EventPolicy.canEditType(actor, current)) {
            throw AppError.Forbidden("Базовые типы не меняются, свои — меняет автор или капитан")
        }
        val updated = current.copy(title = title, color = draft.color, defaultReminders = reminders)
        s.copy(eventTypes = s.eventTypes.map { if (it.id == id) updated else it }) to updated
    }
}

/** Удаляет свой тип (автор или капитан). События этого типа остаются, но без типа. */
internal suspend fun DemoBackend.deleteEventType(actor: Session, id: EventTypeId) = mutate { s ->
    val current = s.eventTypes.firstOrNull { it.id == id } ?: throw AppError.NotFound("Тип не найден")
    if (!EventPolicy.canEditType(actor, current)) {
        throw AppError.Forbidden("Базовые типы не удаляются, свои — удаляет автор или капитан")
    }
    s.copy(
        eventTypes = s.eventTypes.filterNot { it.id == id },
        events = s.events.map { if (it.typeId == id) it.copy(typeId = null) else it },
    ) to Unit
}

internal suspend fun DemoBackend.setReminders(actor: Session, itemKey: String, minutes: Set<Int>) = mutate { s ->
    val key = reminderKey(actor.user.id, itemKey)
    val normalized = EventPolicy.normalizeReminders(minutes)
    s.copy(reminders = if (normalized.isEmpty()) s.reminders - key else s.reminders + (key to normalized)) to Unit
}

/** Выдача событий зрителю: фильтр по расписанию человека и приватность чужих личных событий. */
internal fun DemoState.eventsFor(
    viewer: UserId,
    personId: UserId,
    groups: Set<StudyGroup>,
): List<ScheduleEvent> = events
    .filter { EventPolicy.belongsTo(it.visibility, it.ownerId, it.group, personId, groups) }
    .mapNotNull { presentFor(viewer, it) }

internal fun DemoState.presentFor(viewer: UserId, event: ScheduleEvent): ScheduleEvent? =
    EventPolicy.present(viewer, event, user(event.ownerId)?.schedulePrivacy ?: SchedulePrivacy.BUSY)

internal fun DemoState.remindersOf(userId: UserId): Map<String, Set<Int>> {
    val prefix = "${userId.value}|"
    return reminders.filterKeys { it.startsWith(prefix) }.mapKeys { it.key.removePrefix(prefix) }
}
