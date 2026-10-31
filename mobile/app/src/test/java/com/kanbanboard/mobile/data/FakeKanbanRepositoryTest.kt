package com.kanbanboard.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import com.kanbanboard.mobile.model.LoginCredentials

class FakeKanbanRepositoryTest {
    private val repository = FakeKanbanRepository()

    @Test
    fun `login rejects invalid credentials`() {
        assertNull(repository.login(LoginCredentials("alexey", "wrong")))
    }

    @Test
    fun `login returns lead for valid credentials`() {
        val user = repository.login(LoginCredentials("alexey", "lead123"))

        assertNotNull(user)
        assertEquals("alexey", user?.login)
        assertEquals("Тимлид", user?.position)
    }

    @Test
    fun `both student groups have cached lessons`() {
        assertEquals(3, repository.lessons("203").size)
        assertEquals(3, repository.lessons("204").size)
    }
}
