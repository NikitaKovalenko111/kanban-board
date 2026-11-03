package com.kanbanboard.mobile.core.data

import com.kanbanboard.mobile.core.data.demo.DemoBackend
import com.kanbanboard.mobile.core.data.demo.DemoSeed
import com.kanbanboard.mobile.core.model.AppError
import com.kanbanboard.mobile.core.model.AttachmentKind
import com.kanbanboard.mobile.core.model.ColumnDraft
import com.kanbanboard.mobile.core.model.ColumnKind
import com.kanbanboard.mobile.core.model.PickedFile
import com.kanbanboard.mobile.core.model.TaskAction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoBackendTest {

    @Test
    fun `seed has only team lead and member, no mock data`() {
        val state = DemoBackend(latencyMs = 0).state.value
        assertEquals(listOf(DemoSeed.LEAD_LOGIN, DemoSeed.MEMBER_LOGIN), state.accounts.map { it.member.user.login })
        assertTrue(state.projects.isEmpty() && state.tasks.isEmpty() && state.events.isEmpty())
    }

    @Test
    fun `wrong password is rejected`() = runTest {
        expectError(AppError.InvalidCredentials::class.java) { DemoBackend(latencyMs = 0).login("samir", "nope") }
    }

    @Test
    fun `member takes free task and history records it`() = runTest {
        val team = TestTeam.create()
        val task = team.newTask()
        team.backend.takeTask(team.samir, task.id)
        val updated = team.task(task)
        assertEquals(team.samir.user.id, updated.assigneeId)
        assertEquals(team.column(ColumnKind.IN_PROGRESS).id, updated.columnId)
        assertEquals(TaskAction.TAKEN, updated.history.last().action)
    }

    @Test
    fun `QA-01 member cannot take or move task of another member`() = runTest {
        val team = TestTeam.create()
        val task = team.newTask(assignee = team.samir)
        expectError(AppError.Forbidden::class.java) { team.backend.takeTask(team.maria, task.id) }
        team.backend.takeTask(team.samir, task.id)
        expectError(AppError.Forbidden::class.java) {
            team.backend.moveTask(team.maria, task.id, team.column(ColumnKind.REVIEW).id, null)
        }
    }

    @Test
    fun `full review cycle with return requires comment`() = runTest {
        val team = TestTeam.create()
        val task = team.newTask(assignee = team.samir)
        team.backend.takeTask(team.samir, task.id)
        team.backend.moveTask(team.samir, task.id, team.column(ColumnKind.REVIEW).id, null)
        expectError(AppError.Validation::class.java) {
            team.backend.moveTask(team.lead, task.id, team.column(ColumnKind.IN_PROGRESS).id, " ")
        }
        team.backend.moveTask(team.lead, task.id, team.column(ColumnKind.IN_PROGRESS).id, "Поправить отступы")
        assertEquals(TaskAction.RETURNED, team.task(task).history.last().action)
        assertEquals("Поправить отступы", team.task(task).history.last().comment)

        team.backend.moveTask(team.samir, task.id, team.column(ColumnKind.REVIEW).id, null)
        team.backend.moveTask(team.lead, task.id, team.column(ColumnKind.DONE).id, null)
        assertNotNull(team.task(task).completedAt)
    }

    @Test
    fun `BUG-08 captain submits only after assigning task to himself`() = runTest {
        val team = TestTeam.create()
        val task = team.newTask(assignee = team.samir)
        team.backend.takeTask(team.samir, task.id)
        val file = PickedFile("result.zip", 10, "x/result.zip")
        expectError(AppError.Forbidden::class.java) { team.backend.addAttachment(team.lead, task.id, file, AttachmentKind.RESULT) }
        expectError(AppError.Forbidden::class.java) {
            team.backend.moveTask(team.lead, task.id, team.column(ColumnKind.REVIEW).id, null)
        }
        team.backend.assignToSelf(team.lead, task.id)
        team.backend.addAttachment(team.lead, task.id, file, AttachmentKind.RESULT)
        team.backend.moveTask(team.lead, task.id, team.column(ColumnKind.REVIEW).id, null)
        assertEquals(team.lead.user.id, team.task(task).assigneeId)
    }

    @Test
    fun `attachment is removed by its author or captain`() = runTest {
        val team = TestTeam.create()
        val task = team.newTask(assignee = team.samir)
        team.backend.takeTask(team.samir, task.id)
        team.backend.addAttachment(team.samir, task.id, PickedFile("a.txt", 1, "x/a.txt"), AttachmentKind.RESULT)
        val attachment = team.task(task).attachments.single()
        expectError(AppError.Forbidden::class.java) { team.backend.removeAttachment(team.maria, task.id, attachment.id) }
        assertEquals("x/a.txt", team.backend.removeAttachment(team.samir, task.id, attachment.id))
        assertTrue(team.task(task).attachments.isEmpty())
    }

    @Test
    fun `board keeps at least four columns and blocks removing column with tasks`() = runTest {
        val team = TestTeam.create()
        val columns = team.columns()
        expectError(AppError.Validation::class.java) {
            team.backend.saveColumns(team.lead, team.project.id, columns.take(2).map { ColumnDraft(it.id, it.title, it.color) })
        }
        // Свой этап перед проверкой становится рабочим.
        val drafts = columns.map { ColumnDraft(it.id, it.title, it.color) }.toMutableList()
        drafts.add(2, ColumnDraft(null, "Pull request", 0xFF6576DF))
        team.backend.saveColumns(team.lead, team.project.id, drafts)
        assertEquals(ColumnKind.IN_PROGRESS, team.columns().first { it.title == "Pull request" }.kind)

        team.newTask()
        expectError(AppError.Validation::class.java) {
            team.backend.saveColumns(team.lead, team.project.id, team.columns().drop(1).map { ColumnDraft(it.id, it.title, it.color) })
        }
    }

    @Test
    fun `comment notifies assignee`() = runTest {
        val team = TestTeam.create()
        val task = team.newTask(assignee = team.samir)
        team.backend.addComment(team.lead, task.id, "Посмотри макет")
        assertEquals(1, team.backend.state.value.notifications[team.samir.user.id].orEmpty().size)
    }
}
