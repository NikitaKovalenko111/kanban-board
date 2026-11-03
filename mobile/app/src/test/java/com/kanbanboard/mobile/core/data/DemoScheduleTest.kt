package com.kanbanboard.mobile.core.data

import com.kanbanboard.mobile.core.data.demo.createEvent
import com.kanbanboard.mobile.core.data.demo.deleteEventType
import com.kanbanboard.mobile.core.data.demo.eventsFor
import com.kanbanboard.mobile.core.data.demo.remindersOf
import com.kanbanboard.mobile.core.data.demo.saveEventType
import com.kanbanboard.mobile.core.data.demo.setReminders
import com.kanbanboard.mobile.core.model.AppError
import com.kanbanboard.mobile.core.model.EventDraft
import com.kanbanboard.mobile.core.model.EventTypeDraft
import com.kanbanboard.mobile.core.model.EventTypeId
import com.kanbanboard.mobile.core.model.EventVisibility
import com.kanbanboard.mobile.core.model.ReminderPresets
import com.kanbanboard.mobile.core.model.ScheduleEvent
import com.kanbanboard.mobile.core.model.SchedulePrivacy
import com.kanbanboard.mobile.core.model.Session
import com.kanbanboard.mobile.core.model.StudyGroup
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoScheduleTest {
    private val all = StudyGroup.entries.toSet()
    private val meeting = EventTypeId("t-meeting")
    private val start: Instant = Instant.now().plus(Duration.ofDays(1))

    private fun draft(
        title: String,
        visibility: EventVisibility,
        group: StudyGroup? = null,
        end: Instant? = null,
    ) = EventDraft(title, "Описание", "Спортзал", start, end, visibility, group, meeting, emptySet())

    private suspend fun TestTeam.personal(owner: Session): ScheduleEvent =
        backend.createEvent(owner, draft("Тренировка", EventVisibility.PERSONAL))

    @Test
    fun `personal event follows owner privacy`() = runTest {
        val team = TestTeam.create()
        val event = team.personal(team.samir)
        fun seenByMaria() = team.backend.state.value
            .eventsFor(viewer = team.maria.user.id, personId = team.samir.user.id, groups = all)
            .firstOrNull { it.id == event.id }

        // По умолчанию — только «Занят».
        val busy = seenByMaria()!!
        assertTrue(busy.masked)
        assertEquals("Занят", busy.title)
        assertEquals("", busy.location)

        team.backend.updateSchedulePrivacy(team.samir, SchedulePrivacy.NOTHING)
        assertNull(seenByMaria())

        team.backend.updateSchedulePrivacy(team.samir, SchedulePrivacy.TITLE)
        val titled = seenByMaria()!!
        assertFalse(titled.masked)
        assertTrue(titled.partial)
        assertEquals("Тренировка", titled.title)
        assertEquals("", titled.description)

        team.backend.updateSchedulePrivacy(team.samir, SchedulePrivacy.TITLE_AND_DESCRIPTION)
        assertEquals("Описание", seenByMaria()!!.description)

        // Владелец видит всё.
        val own = team.backend.state.value.eventsFor(team.samir.user.id, team.samir.user.id, all).first { it.id == event.id }
        assertFalse(own.masked || own.partial)
    }

    @Test
    fun `group events follow selected groups`() = runTest {
        val team = TestTeam.create()
        team.backend.createEvent(team.lead, draft("Для 203", EventVisibility.GROUP, StudyGroup.G203))
        team.backend.createEvent(team.lead, draft("Для 204", EventVisibility.GROUP, StudyGroup.G204))
        val only204 = team.backend.state.value.eventsFor(team.maria.user.id, team.maria.user.id, setOf(StudyGroup.G204))
        assertEquals(listOf("Для 204"), only204.map { it.title })
    }

    @Test
    fun `member cannot create group event for another group`() = runTest {
        val team = TestTeam.create() // Мария — группа 204
        expectError(AppError.Forbidden::class.java) {
            team.backend.createEvent(team.maria, draft("Созвон", EventVisibility.GROUP, StudyGroup.G203))
        }
    }

    @Test
    fun `reminders are personal`() = runTest {
        val team = TestTeam.create()
        val event = team.backend.createEvent(team.lead, draft("Демо", EventVisibility.TEAM))
        val key = "event:${event.id.value}"
        team.backend.setReminders(team.samir, key, setOf(30, 0, -5))
        assertEquals(setOf(30), team.backend.state.value.remindersOf(team.samir.user.id)[key])
        assertTrue(team.backend.state.value.remindersOf(team.maria.user.id)[key].isNullOrEmpty())
    }

    @Test
    fun `end time is optional but must follow start`() = runTest {
        val team = TestTeam.create()
        assertNull(team.backend.createEvent(team.samir, draft("Без окончания", EventVisibility.TEAM)).endsAt)
        expectError(AppError.Validation::class.java) {
            team.backend.createEvent(team.samir, draft("Кривое", EventVisibility.TEAM, end = start.minusSeconds(60)))
        }
    }

    @Test
    fun `own event type can be deleted, events keep without type`() = runTest {
        val team = TestTeam.create()
        val type = team.backend.saveEventType(team.samir, null, EventTypeDraft("Спорт", 0xFF4FD1C5, setOf(30)))
        val event = team.backend.createEvent(team.samir, draft("Зал", EventVisibility.PERSONAL).copy(typeId = type.id))
        expectError(AppError.Forbidden::class.java) { team.backend.deleteEventType(team.maria, type.id) }
        expectError(AppError.Forbidden::class.java) { team.backend.deleteEventType(team.lead, meeting) }
        team.backend.deleteEventType(team.samir, type.id)
        assertNull(team.backend.state.value.events.first { it.id == event.id }.typeId)
    }

    @Test
    fun `reminder labels are readable`() {
        assertEquals("за 1 ч 30 мин", ReminderPresets.label(90))
        assertEquals("за 1 нед.", ReminderPresets.label(10080))
        assertEquals("за 2 д", ReminderPresets.label(2880))
    }
}
