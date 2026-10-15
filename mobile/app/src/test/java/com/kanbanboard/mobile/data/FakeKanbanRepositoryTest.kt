package com.kanbanboard.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FakeKanbanRepositoryTest {
    private val repository = FakeKanbanRepository()

    @Test
    fun `login rejects blank id`() {
        assertNull(repository.login("  "))
    }

    @Test
    fun `login returns Samir profile for non blank id`() {
        val user = repository.login("samir-203")

        assertNotNull(user)
        assertEquals("samir-203", user?.id)
        assertEquals("203", user?.group)
    }

    @Test
    fun `both student groups have cached lessons`() {
        assertEquals(3, repository.lessons("203").size)
        assertEquals(3, repository.lessons("204").size)
    }
}
