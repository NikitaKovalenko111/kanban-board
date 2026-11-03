package com.kanbanboard.mobile.core.policy

import com.kanbanboard.mobile.core.model.EventType
import com.kanbanboard.mobile.core.model.EventVisibility
import com.kanbanboard.mobile.core.model.ReminderPresets
import com.kanbanboard.mobile.core.model.ScheduleEvent
import com.kanbanboard.mobile.core.model.SchedulePrivacy
import com.kanbanboard.mobile.core.model.Session
import com.kanbanboard.mobile.core.model.StudyGroup
import com.kanbanboard.mobile.core.model.UserId

/**
 * Правила расписания.
 * - Команда видит все командные события; групповые — своей группы (или обеих при выборе «Обе»).
 * - Личные события видны владельцу полностью, остальным — по настройке приватности владельца:
 *   ничего / «Занят» / название / название и описание.
 * - Создавать события может любой участник; групповые — только для своей группы (капитан — для любой).
 * - Менять и удалять событие может владелец или капитан (если событие видно ему целиком).
 * - Напоминания личные: каждый добавляет и убирает только свои, на любом видимом элементе.
 */
object EventPolicy {

    fun canCreate(session: Session, visibility: EventVisibility, group: StudyGroup?): Boolean = when (visibility) {
        EventVisibility.TEAM, EventVisibility.PERSONAL -> true
        EventVisibility.GROUP -> group != null && (session.isCaptain || group == session.user.studyGroup)
    }

    fun canEdit(session: Session, event: ScheduleEvent): Boolean =
        !event.masked && !event.partial && (event.ownerId == session.user.id || session.isCaptain)

    fun canEditType(session: Session, type: EventType): Boolean =
        !type.isBuiltIn && (type.ownerId == session.user.id || session.isCaptain)

    /** Попадает ли событие в расписание человека [personId]; [groups] — какие группы показывать. */
    fun belongsTo(
        visibility: EventVisibility,
        ownerId: UserId,
        group: StudyGroup?,
        personId: UserId,
        groups: Set<StudyGroup>,
    ): Boolean = when (visibility) {
        EventVisibility.TEAM -> true
        EventVisibility.GROUP -> group != null && group in groups
        EventVisibility.PERSONAL -> ownerId == personId
    }

    /**
     * Как показать событие зрителю [viewer]. null — событие скрыто полностью.
     * [ownerPrivacy] — настройка приватности владельца.
     */
    fun present(viewer: UserId, event: ScheduleEvent, ownerPrivacy: SchedulePrivacy): ScheduleEvent? {
        if (event.visibility != EventVisibility.PERSONAL || event.ownerId == viewer) return event
        return when (ownerPrivacy) {
            SchedulePrivacy.NOTHING -> null
            SchedulePrivacy.BUSY ->
                event.copy(title = "Занят", description = "", location = "", typeId = null, masked = true, partial = false)
            SchedulePrivacy.TITLE ->
                event.copy(description = "", location = "", masked = false, partial = true)
            SchedulePrivacy.TITLE_AND_DESCRIPTION ->
                event.copy(location = "", masked = false, partial = true)
        }
    }

    fun normalizeReminders(minutes: Collection<Int>): Set<Int> =
        minutes.filter { it in 1..ReminderPresets.MAX_MINUTES }.toSortedSet()
}
