package com.kanbanboard.mobile.ui

import com.kanbanboard.mobile.model.TaskStatus
import com.kanbanboard.mobile.model.UserRole
import com.kanbanboard.mobile.model.AppTheme
import com.kanbanboard.mobile.model.EventVisibility
import com.kanbanboard.mobile.data.MemoryAppStorage
import org.junit.Assert.assertEquals
import org.junit.Test

class AppViewModelTest {
    @Test
    fun `member cannot change another assignee task`() {
        val viewModel = AppViewModel()
        viewModel.login("maria", "member123")

        viewModel.moveTask(101, TaskStatus.CHECKING)

        assertEquals(TaskStatus.WORKING, viewModel.uiState.tasks.first { it.id == 101L }.status)
    }

    @Test
    fun `member can start own task`() {
        val viewModel = AppViewModel()
        viewModel.login("maria", "member123")

        viewModel.moveTask(102, TaskStatus.WORKING)

        assertEquals(TaskStatus.WORKING, viewModel.uiState.tasks.first { it.id == 102L }.status)
    }

    @Test
    fun `leaving selected team switches to another membership`() {
        val viewModel = AppViewModel()
        viewModel.login("maria", "member123")
        viewModel.selectTeam(2)

        viewModel.leaveTeam(2)

        assertEquals(1L, viewModel.uiState.selectedTeamId)
    }

    @Test
    fun `created member can login with temporary password`() {
        val viewModel = AppViewModel()
        viewModel.login("alexey", "lead123")
        viewModel.addMember("Новый Участник", "new.user", "temp123", "Аналитик", UserRole.MEMBER)
        viewModel.logout()

        viewModel.login("new.user", "temp123")

        assertEquals("new.user", viewModel.uiState.user?.login)
    }

    @Test
    fun `joining team makes it selected`() {
        val viewModel = AppViewModel()
        viewModel.login("maria", "member123")

        viewModel.joinTeam(3)

        assertEquals(3L, viewModel.uiState.selectedTeamId)
    }

    @Test
    fun `group event follows selected student group`() {
        val viewModel = AppViewModel()
        viewModel.login("maria", "member123")
        val event = viewModel.createEvent("Семинар", "2030-01-02", "14:00", EventVisibility.GROUP, "study", setOf(60))!!

        assertEquals(true, viewModel.visibleEvents().any { it.id == event.id })
        viewModel.setStudentGroup("204")
        assertEquals(false, viewModel.visibleEvents().any { it.id == event.id })
    }

    @Test
    fun `settings custom type and event survive view model recreation`() {
        val storage = MemoryAppStorage()
        val first = AppViewModel(storage = storage)
        first.login("maria", "member123")
        first.setTheme(AppTheme.DARK)
        first.setStudentGroup("204")
        val type = first.addEventType("Хакатон", 0xFF38A585, setOf(1440))!!
        first.createEvent("Финал", "2030-01-02", "10:00", EventVisibility.PERSONAL, type.id, type.defaultReminders)

        val restored = AppViewModel(storage = storage)
        restored.login("maria", "member123")
        assertEquals(AppTheme.DARK, restored.uiState.theme)
        assertEquals("204", restored.uiState.studentGroup)
        assertEquals(setOf(1440), restored.uiState.eventTypes.first { it.id == type.id }.defaultReminders)
        assertEquals(true, restored.visibleEvents().any { it.title == "Финал" })
    }

    @Test
    fun `personal event is private while team event follows selected team`() {
        val viewModel = AppViewModel()
        viewModel.login("maria", "member123")
        val personal = viewModel.createEvent("Личное дело", "2030-01-02", "10:00", EventVisibility.PERSONAL, "personal", emptySet())!!
        val team = viewModel.createEvent("Созвон", "2030-01-02", "11:00", EventVisibility.TEAM, "meeting", emptySet())!!

        viewModel.selectTeam(2)
        assertEquals(true, viewModel.visibleEvents().any { it.id == personal.id })
        assertEquals(false, viewModel.visibleEvents().any { it.id == team.id })

        viewModel.logout()
        viewModel.login("alexey", "lead123")
        assertEquals(false, viewModel.visibleEvents().any { it.id == personal.id })
    }
}
