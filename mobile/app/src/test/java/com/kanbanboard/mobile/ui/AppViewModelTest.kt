package com.kanbanboard.mobile.ui

import com.kanbanboard.mobile.model.TaskStatus
import com.kanbanboard.mobile.model.UserRole
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
}
