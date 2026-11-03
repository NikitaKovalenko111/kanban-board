package com.kanbanboard.mobile.core.data

import com.kanbanboard.mobile.core.data.demo.DemoSeed
import com.kanbanboard.mobile.core.model.AppError
import com.kanbanboard.mobile.core.model.ColumnKind
import com.kanbanboard.mobile.core.model.NewAccount
import com.kanbanboard.mobile.core.model.StudyGroup
import com.kanbanboard.mobile.core.model.TeamRole
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Управление аккаунтами: создание, блокировка, сброс и смена пароля, роли, удаление. */
class DemoAccountsTest {

    @Test
    fun `only captain creates accounts and logins are unique`() = runTest {
        val team = TestTeam.create()
        val account = NewAccount("Илья", "ilya", "ilya12345", StudyGroup.G203, TeamRole.MEMBER)
        expectError(AppError.Forbidden::class.java) { team.backend.createAccount(team.samir, account) }
        expectError(AppError.Validation::class.java) { team.backend.createAccount(team.lead, account.copy(login = "maria")) }
        expectError(AppError.Validation::class.java) { team.backend.createAccount(team.lead, account.copy(temporaryPassword = "123")) }
        team.backend.createAccount(team.lead, account)
        assertEquals("ilya", team.backend.login("ilya", "ilya12345").user.login)
    }

    @Test
    fun `blocked member cannot log in until restored`() = runTest {
        val team = TestTeam.create()
        team.backend.setMemberActive(team.lead, team.maria.user.id, false)
        expectError(AppError.Forbidden::class.java) { team.backend.login("maria", "maria12345") }
        assertNull(team.backend.state.value.sessionFor(team.maria.user.id))
        team.backend.setMemberActive(team.lead, team.maria.user.id, true)
        team.backend.login("maria", "maria12345")
        expectError(AppError.Validation::class.java) { team.backend.setMemberActive(team.lead, team.lead.user.id, false) }
    }

    @Test
    fun `reset password gives temporary one, member changes it`() = runTest {
        val team = TestTeam.create()
        val temporary = team.backend.resetPassword(team.lead, team.samir.user.id)
        expectError(AppError.InvalidCredentials::class.java) { team.backend.login(DemoSeed.MEMBER_LOGIN, DemoSeed.MEMBER_PASSWORD) }
        val session = team.backend.login(DemoSeed.MEMBER_LOGIN, temporary)
        expectError(AppError.Validation::class.java) { team.backend.changePassword(session, "wrong", "newpass123") }
        expectError(AppError.Validation::class.java) { team.backend.changePassword(session, temporary, "short") }
        team.backend.changePassword(session, temporary, "newpass123")
        team.backend.login(DemoSeed.MEMBER_LOGIN, "newpass123")
    }

    @Test
    fun `captain promotes member but cannot change own role`() = runTest {
        val team = TestTeam.create()
        expectError(AppError.Forbidden::class.java) { team.backend.setMemberRole(team.samir, team.maria.user.id, TeamRole.CAPTAIN) }
        team.backend.setMemberRole(team.lead, team.maria.user.id, TeamRole.CAPTAIN)
        assertTrue(team.backend.state.value.sessionFor(team.maria.user.id)!!.isCaptain)
        expectError(AppError.Validation::class.java) { team.backend.setMemberRole(team.lead, team.lead.user.id, TeamRole.MEMBER) }
    }

    @Test
    fun `account with open tasks cannot be deleted, after closing it can`() = runTest {
        val team = TestTeam.create()
        val task = team.newTask(assignee = team.maria)
        expectError(AppError.Forbidden::class.java) { team.backend.deleteAccount(team.samir, team.maria.user.id) }
        expectError(AppError.Validation::class.java) { team.backend.deleteAccount(team.lead, team.lead.user.id) }
        expectError(AppError.Validation::class.java) { team.backend.deleteAccount(team.lead, team.maria.user.id) }

        team.backend.takeTask(team.maria, task.id)
        team.backend.moveTask(team.maria, task.id, team.column(ColumnKind.REVIEW).id, null)
        team.backend.moveTask(team.lead, task.id, team.column(ColumnKind.DONE).id, null)
        team.backend.deleteAccount(team.lead, team.maria.user.id)

        val state = team.backend.state.value
        assertNull(state.user(team.maria.user.id))
        assertTrue(team.maria.user.id !in state.project(team.project.id).members)
        // Выполненная задача и её журнал остаются.
        assertEquals(team.maria.user.id, state.task(task.id).assigneeId)
        expectError(AppError.InvalidCredentials::class.java) { team.backend.login("maria", "maria12345") }
    }
}
