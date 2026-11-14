package com.kanbanboard.mobile.core.data.network

import kotlinx.serialization.json.Json
import com.kanbanboard.mobile.core.model.StudyGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceResponseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun serverGroupCodeAndOldSavedGroupRemainReadable() {
        val user = json.decodeFromString<UserDto>(
            """{"id":"u1","fullName":"Участник","username":"member","studentGroup":"ТОП-203Б","role":"MEMBER","schedulePrivacy":"BUSY","joinedAt":"2026-11-09T10:00:00Z","active":true}""",
        )

        assertEquals("ТОП-203Б", NetworkModelMapper.user(user).studyGroup.code)
        assertEquals(StudyGroup.G203, json.decodeFromString<StudyGroup>("\"G203\""))
    }

    @Test fun projectsWithoutOptionalFieldsRemainReadable() {
        val restricted = json.decodeFromString<ProjectDto>(
            """{"id":"p1","name":"Закрытый","color":"#123456","canViewBoard":false}""",
        )
        val accessible = json.decodeFromString<ProjectDto>(
            """{"id":"p2","name":"Мой","description":"Описание","canViewBoard":true,"members":[]}""",
        )

        assertFalse(NetworkModelMapper.project(restricted).canViewBoard)
        assertTrue(NetworkModelMapper.project(restricted).members.isEmpty())
        assertEquals("", NetworkModelMapper.project(restricted).description)
        assertTrue(NetworkModelMapper.project(accessible).canViewBoard)
        assertTrue(NetworkModelMapper.project(accessible).sources.isEmpty())
    }

    @Test fun taskSupportsFileAndLinkAttachmentsTogether() {
        val dto = json.decodeFromString<TaskDto>(
            """{"id":"t1","projectId":"p1","columnId":"c1","position":0,"title":"Задача","description":"","priority":"NORMAL","createdBy":"u1","createdAt":"2026-11-09T10:00:00Z","attachments":[{"id":"a1","kind":"SOURCE","contentKind":"LINK","title":"ТЗ","url":"https://example.org/spec","uploadedBy":"u1","uploadedAt":"2026-11-09T10:00:00Z"},{"id":"a2","kind":"RESULT","uploadedBy":"u1","uploadedAt":"2026-11-09T10:00:00Z","file":{"id":"f1","name":"result.pdf","sizeBytes":123,"mimeType":"application/pdf","downloadUrl":"/files/f1"}}]}""",
        )

        val task = NetworkModelMapper.task(dto)
        assertEquals("ТЗ", task.attachments[0].name)
        assertEquals("https://example.org/spec", task.attachments[0].url)
        assertNull(task.attachments[0].fileRef)
        assertEquals("result.pdf", task.attachments[1].name)
        assertEquals("/files/f1", task.attachments[1].fileRef)
        assertNull(task.attachments[1].url)
    }
}
