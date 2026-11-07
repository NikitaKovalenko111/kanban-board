package com.kanbanboard.mobile.di

import com.kanbanboard.mobile.core.data.demo.DemoBackend
import com.kanbanboard.mobile.core.data.demo.DemoStore
import com.kanbanboard.mobile.core.model.ColumnKind
import com.kanbanboard.mobile.core.model.StudyGroup
import java.nio.file.Files
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Мок-данные целостны: ссылки между сущностями сходятся, данные сохраняются и с ними работают права. */
class MockDataTest {
    private val state = MockData.create(Clock.systemDefaultZone())

    @Test
    fun `references are consistent`() {
        val users = state.accounts.map { it.member.user.id }.toSet()
        val columns = state.columns.associateBy { it.id }
        state.tasks.forEach { task ->
            val column = columns.getValue(task.columnId)
            assertEquals(task.projectId, column.projectId)
            task.assigneeId?.let { assertTrue("${task.id} assignee", state.project(task.projectId).isMember(it)) }
            assertEquals(column.kind == ColumnKind.DONE, task.completedAt != null)
        }
        state.projects.forEach { project -> assertTrue(project.members.keys.all { it in users }) }
        state.events.forEach { event ->
            assertTrue(event.ownerId in users)
            assertTrue(event.typeId == null || state.eventTypes.any { it.id == event.typeId })
        }
    }

    @Test
    fun `mock accounts log in and rules still apply`() = runTest {
        val backend = DemoBackend(latencyMs = 0, initial = state)
        val lead = backend.login("nikita", MockData.LEAD_PASSWORD)
        val maria = backend.login("maria", MockData.MEMBER_PASSWORD)
        assertTrue(lead.isCaptain)
        try {
            backend.takeTask(maria, com.kanbanboard.mobile.core.model.TaskId("t-2")) // назначена на Алмаза
            throw AssertionError("Ожидался запрет")
        } catch (e: com.kanbanboard.mobile.core.model.AppError.Forbidden) {
            // QA-01
        }
    }

    @Test
    fun `mock state survives save and load`() {
        val dir = Files.createTempDirectory("mock-store").toFile()
        DemoStore(dir).write(state)
        assertEquals(state, DemoStore(dir).loadState())
        dir.deleteRecursively()
    }

    @Test
    fun `example lessons exist for both groups`() {
        val monday = LocalDate.of(2026, 11, 2)
        assertTrue(MockData.lessons(StudyGroup.G203, monday, monday.plusDays(6)).isNotEmpty())
        assertTrue(MockData.lessons(StudyGroup.G204, monday, monday.plusDays(6)).isNotEmpty())
    }
}
