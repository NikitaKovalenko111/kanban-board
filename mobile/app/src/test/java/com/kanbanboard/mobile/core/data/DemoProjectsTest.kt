package com.kanbanboard.mobile.core.data

import com.kanbanboard.mobile.core.data.demo.addSource
import com.kanbanboard.mobile.core.data.demo.createProject
import com.kanbanboard.mobile.core.data.demo.deleteProject
import com.kanbanboard.mobile.core.data.demo.newSource
import com.kanbanboard.mobile.core.data.demo.removeSource
import com.kanbanboard.mobile.core.data.demo.updateProject
import com.kanbanboard.mobile.core.model.AppError
import com.kanbanboard.mobile.core.model.AttachmentKind
import com.kanbanboard.mobile.core.model.ColumnKind
import com.kanbanboard.mobile.core.model.PickedFile
import com.kanbanboard.mobile.core.model.ProjectDraft
import com.kanbanboard.mobile.core.model.SourceKind
import com.kanbanboard.mobile.core.policy.TaskPolicy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoProjectsTest {

    @Test
    fun `captain creates project with full board`() = runTest {
        val team = TestTeam.create()
        val kinds = team.columns().map { it.kind }
        assertTrue(TaskPolicy.missingKinds(kinds).isEmpty())
        assertEquals(ColumnKind.BACKLOG, kinds.first())
        expectError(AppError.Validation::class.java) {
            team.backend.createProject(team.lead, ProjectDraft("kanban board", "", emptyMap()))
        }
    }

    @Test
    fun `member cannot create, edit or delete projects`() = runTest {
        val team = TestTeam.create()
        expectError(AppError.Forbidden::class.java) { team.backend.createProject(team.samir, ProjectDraft("X", "", emptyMap())) }
        expectError(AppError.Forbidden::class.java) { team.backend.updateProject(team.samir, team.project.id, ProjectDraft("X", "", emptyMap())) }
        expectError(AppError.Forbidden::class.java) { team.backend.deleteProject(team.samir, team.project.id) }
    }

    @Test
    fun `cannot remove member with open tasks`() = runTest {
        val team = TestTeam.create()
        team.newTask(assignee = team.maria)
        val current = team.backend.state.value.project(team.project.id)
        expectError(AppError.Validation::class.java) {
            team.backend.updateProject(
                team.lead, team.project.id,
                ProjectDraft(current.name, current.description, current.members - team.maria.user.id),
            )
        }
    }

    @Test
    fun `links are validated and sources removed only by author or captain`() = runTest {
        val team = TestTeam.create()
        val id = team.project.id
        expectError(AppError.Validation::class.java) {
            team.backend.addSource(team.samir, id, team.backend.newSource(team.samir, SourceKind.LINK, "Плохая", "ftp://x", null, null))
        }
        val link = team.backend.newSource(team.samir, SourceKind.LINK, "Макеты", "https://figma.com/file/demo", null, null)
        team.backend.addSource(team.samir, id, link)
        expectError(AppError.Forbidden::class.java) { team.backend.removeSource(team.maria, id, link.id) }
        team.backend.removeSource(team.samir, id, link.id)
        assertTrue(team.backend.state.value.project(id).sources.none { it.id == link.id })
    }

    @Test
    fun `deleting project removes board and tasks and returns files to clean`() = runTest {
        val team = TestTeam.create()
        val task = team.newTask(assignee = team.samir)
        team.backend.takeTask(team.samir, task.id)
        team.backend.addAttachment(team.samir, task.id, PickedFile("r.zip", 1, "u/r.zip"), AttachmentKind.RESULT)
        team.backend.addSource(team.lead, team.project.id, team.backend.newSource(team.lead, SourceKind.FILE, "ТЗ.pdf", null, 1, "u/tz.pdf"))

        val files = team.backend.deleteProject(team.lead, team.project.id)
        assertEquals(setOf("u/r.zip", "u/tz.pdf"), files.toSet())
        val state = team.backend.state.value
        assertTrue(state.projects.isEmpty() && state.columns.isEmpty() && state.tasks.isEmpty())
    }
}
