package com.kanbanboard.mobile.core.data

import com.kanbanboard.mobile.core.data.demo.DemoStore
import com.kanbanboard.mobile.core.data.demo.createEvent
import com.kanbanboard.mobile.core.model.EventDraft
import com.kanbanboard.mobile.core.model.EventTypeId
import com.kanbanboard.mobile.core.model.EventVisibility
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Данные демо-сервера и вход сохраняются на устройстве и восстанавливаются без потерь. */
class DemoStoreTest {

    @Test
    fun `state survives save and load`() = runTest {
        val team = TestTeam.create()
        val task = team.newTask("Сохраниться", assignee = team.samir)
        team.backend.addComment(team.lead, task.id, "Комментарий")
        team.backend.createEvent(
            team.samir,
            EventDraft("Личное", "", "", Instant.parse("2026-11-05T10:00:00Z"), null, EventVisibility.PERSONAL, null, EventTypeId("t-personal"), setOf(15)),
        )
        val dir = Files.createTempDirectory("demo-store").toFile()
        val store = DemoStore(dir)
        val state = team.backend.state.value
        store.write(state)

        assertEquals(state, DemoStore(dir).loadState())
        dir.deleteRecursively()
    }

    @Test
    fun `session is remembered until logout`() {
        val dir = Files.createTempDirectory("demo-session").toFile()
        val store = DemoStore(dir)
        assertNull(store.loadSession())
        store.saveSession(com.kanbanboard.mobile.core.model.UserId("u-samir"))
        assertEquals("u-samir", DemoStore(dir).loadSession()?.value)
        store.saveSession(null)
        assertNull(store.loadSession())
        dir.deleteRecursively()
    }

    @Test
    fun `broken file falls back to seed`() {
        val dir = Files.createTempDirectory("demo-broken").toFile()
        java.io.File(dir, "demo").mkdirs()
        java.io.File(dir, "demo/state.json").writeText("{not json")
        assertNull(DemoStore(dir).loadState())
        dir.deleteRecursively()
    }
}
