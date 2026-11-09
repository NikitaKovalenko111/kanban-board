package com.kanbanboard.mobile.core.data.network

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertArrayEquals
import java.io.File
import org.junit.Test

class AuthApiTest {
    @Test fun loginBearerRefreshAndLogoutFollowSwagger() = runBlocking {
        MockWebServer().use { server ->
            val user = """{"id":"u1","fullName":"Самир","username":"samir","studentGroup":"203","role":"MEMBER","schedulePrivacy":"BUSY","joinedAt":"2026-11-05T10:00:00Z","active":true}"""
            server.enqueue(MockResponse().setBody("""{"accessToken":"a1","refreshToken":"r1","expiresIn":900,"user":$user}"""))
            server.enqueue(MockResponse().setBody(user))
            server.enqueue(MockResponse().setBody("""{"accessToken":"a2","refreshToken":"r2","expiresIn":900}"""))
            server.enqueue(MockResponse().setResponseCode(204))
            val api = AuthApi(server.url("/").toString())

            assertEquals("samir", api.login("samir", "secret").user.username)
            val login = server.takeRequest()
            assertEquals("/api/v1/auth/login", login.path)
            assertNull(login.getHeader("Authorization"))
            assertEquals("a1", api.me().let { server.takeRequest().getHeader("Authorization")?.removePrefix("Bearer ") })
            assertEquals("a2", api.refresh().accessToken)
            val refresh = server.takeRequest()
            assertEquals("/api/v1/auth/refresh", refresh.path)
            assertEquals(true, refresh.body.readUtf8().contains("\"refreshToken\":\"r1\""))
            api.logout()
            val logout = server.takeRequest()
            assertEquals("Bearer a2", logout.getHeader("Authorization"))
        }
    }

    @Test fun protectedRequestRefreshesTokenAndEncodesQuery() = runBlocking {
        MockWebServer().use { server ->
            val user = """{"id":"u1","fullName":"Самир","username":"samir","studentGroup":"203","role":"MEMBER","schedulePrivacy":"BUSY","joinedAt":"2026-11-05T10:00:00Z","active":true}"""
            server.enqueue(MockResponse().setBody("""{"accessToken":"old","refreshToken":"r1","expiresIn":900,"user":$user}"""))
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"code":"INVALID_ACCESS_TOKEN","message":"Expired"}}"""))
            server.enqueue(MockResponse().setBody("""{"accessToken":"new","refreshToken":"r2","expiresIn":900}"""))
            server.enqueue(MockResponse().setBody("""[{"id":"p1"}]"""))
            val auth = AuthApi(server.url("/").toString())
            auth.login("samir", "secret")
            server.takeRequest()
            val api = BackendHttp(server.url("/").toString(), auth)
            val projects = api.get("projects", mapOf("search" to "моя команда"))
            assertEquals("p1", projects.jsonArray[0].jsonObject["id"]?.jsonPrimitive?.content)
            val expired = server.takeRequest()
            assertEquals("Bearer old", expired.getHeader("Authorization"))
            assertEquals("моя команда", expired.requestUrl?.queryParameter("search"))
            assertEquals("/api/v1/auth/refresh", server.takeRequest().path)
            assertEquals("Bearer new", server.takeRequest().getHeader("Authorization"))
        }
    }

    @Test fun workspaceReadsProjectsAndBackendSchedule() = runBlocking {
        MockWebServer().use { server ->
            val user = """{"id":"u1","fullName":"Самир","username":"samir","studentGroup":"203","role":"MEMBER","schedulePrivacy":"BUSY","joinedAt":"2026-11-05T10:00:00Z","active":true}"""
            server.enqueue(MockResponse().setBody("""{"accessToken":"jwt","refreshToken":"refresh","expiresIn":900,"user":$user}"""))
            server.enqueue(MockResponse().setBody("""[{"id":"p1","name":"Хакатон","description":"","members":[{"userId":"u1","role":"Backend"}],"sources":[]}]"""))
            server.enqueue(MockResponse().setBody("""{"lessons":[],"source":"CACHED","updatedAt":"2026-11-05T10:00:00Z"}"""))
            val auth = AuthApi(server.url("/").toString())
            auth.login("samir", "secret")
            server.takeRequest()
            val api = WorkspaceApi(BackendHttp(server.url("/").toString(), auth))
            assertEquals("Хакатон", api.projects().single().name)
            assertEquals("/api/v1/projects", server.takeRequest().path)
            assertEquals("CACHED", api.schedule("203", "2026-11-05", "2026-11-12").source)
            assertEquals("203", server.takeRequest().requestUrl?.queryParameter("group"))
        }
    }

    @Test fun workspaceWritesTaskEventAndRemindersWithJwt() = runBlocking {
        MockWebServer().use { server ->
            val user = """{"id":"u1","fullName":"Самир","username":"samir","studentGroup":"203","role":"MEMBER","schedulePrivacy":"BUSY","joinedAt":"2026-11-05T10:00:00Z","active":true}"""
            server.enqueue(MockResponse().setBody("""{"accessToken":"jwt","refreshToken":"refresh","expiresIn":900,"user":$user}"""))
            server.enqueue(MockResponse().setBody("""{"id":"t1","projectId":"p1","columnId":"c1","position":0,"title":"Задача","description":"","priority":"HIGH","createdBy":"u1","createdAt":"2026-11-05T10:00:00Z"}"""))
            server.enqueue(MockResponse().setBody("""{"id":"e1","ownerId":"u1","title":"Созвон","startsAt":"2026-11-06T10:00:00Z","visibility":"TEAM","masked":false,"partial":false}"""))
            server.enqueue(MockResponse().setResponseCode(204))
            val auth = AuthApi(server.url("/").toString())
            auth.login("samir", "secret")
            server.takeRequest()
            val api = WorkspaceApi(BackendHttp(server.url("/").toString(), auth))
            assertEquals("t1", api.createTask("p1", TaskRequestDto("Задача", "", "HIGH")).id)
            val taskRequest = server.takeRequest()
            assertEquals("/api/v1/projects/p1/tasks", taskRequest.path)
            assertEquals("Bearer jwt", taskRequest.getHeader("Authorization"))
            assertEquals("HIGH", taskRequest.body.readUtf8().let { kotlinx.serialization.json.Json.parseToJsonElement(it).jsonObject["priority"]?.jsonPrimitive?.content })
            assertEquals("e1", api.createEvent(EventRequestDto("Созвон", "", "", "2026-11-06T10:00:00Z", visibility = "TEAM", myReminders = setOf(30))).id)
            val eventRequest = server.takeRequest()
            assertEquals("/api/v1/events", eventRequest.path)
            assertEquals(30, eventRequest.body.readUtf8().let { kotlinx.serialization.json.Json.parseToJsonElement(it).jsonObject["myReminders"]?.jsonArray?.single()?.jsonPrimitive?.content?.toInt() })
            api.setReminders("event:e1", setOf(60))
            val remindersRequest = server.takeRequest()
            assertEquals("PUT", remindersRequest.method)
            assertEquals("/api/v1/me/reminders/event:e1", remindersRequest.path)
        }
    }

    @Test fun mapperPreservesScheduleSourceAndProjectFiles() {
        val schedule = NetworkModelMapper.schedule(ScheduleDto(emptyList(), "CACHED", "2026-11-05T10:00:00Z"))
        assertEquals(com.kanbanboard.mobile.core.model.LessonsOrigin.CACHE, schedule.source)
        val project = NetworkModelMapper.project(ProjectDto(
            "p1", "Хакатон", "", emptyList(), listOf(ProjectSourceDto(
                "s1", "FILE", "ТЗ", "2026-11-05T10:00:00Z",
                file = FileInfoDto("f1", "requirements.pdf", 1024, "application/pdf", "/api/v1/files/f1"),
            ))
        ))
        assertEquals(1024L, project.sources.single().sizeBytes)
        assertTrue(project.sources.single().fileRef!!.endsWith("/api/v1/files/f1"))
    }

    @Test fun uploadsMultipartAndDownloadsBinaryWithBearer() = runBlocking {
        MockWebServer().use { server ->
            val user = """{"id":"u1","fullName":"Самир","username":"samir","studentGroup":"203","role":"MEMBER","schedulePrivacy":"BUSY","joinedAt":"2026-11-05T10:00:00Z","active":true}"""
            server.enqueue(MockResponse().setBody("""{"accessToken":"jwt","refreshToken":"refresh","expiresIn":900,"user":$user}"""))
            server.enqueue(MockResponse().setBody("""{"id":"a1","kind":"RESULT","uploadedBy":"u1","uploadedAt":"2026-11-05T10:00:00Z","file":{"id":"f1","name":"data.bin","sizeBytes":4,"mimeType":"application/octet-stream","downloadUrl":"/api/v1/files/f1"}}"""))
            val payload = byteArrayOf(0, 1, -1, 42)
            server.enqueue(MockResponse().setBody(okio.Buffer().write(payload)))
            val auth = AuthApi(server.url("/").toString())
            auth.login("samir", "secret")
            server.takeRequest()
            val api = WorkspaceApi(BackendHttp(server.url("/").toString(), auth))
            val file = File.createTempFile("kanban-upload", ".bin")
            try {
                file.writeBytes(payload)
                assertEquals("a1", api.addAttachment("t1", file, "data.bin", "application/octet-stream", "RESULT").id)
                val upload = server.takeRequest()
                assertEquals("Bearer jwt", upload.getHeader("Authorization"))
                assertTrue(upload.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
                assertTrue(upload.body.readUtf8().contains("name=\"kind\""))
                assertArrayEquals(payload, api.downloadFile("f1"))
                assertEquals("/api/v1/files/f1", server.takeRequest().path)
            } finally {
                file.delete()
            }
        }
    }

    @Test fun taskPatchExplicitlyClearsAssigneeAndDeadline() {
        val json = kotlinx.serialization.json.Json.encodeToString(
            TaskPatchDto.serializer(), TaskPatchDto("Задача", "", "NORMAL", null, null),
        )
        assertTrue(json.contains("\"assigneeId\":null"))
        assertTrue(json.contains("\"dueAt\":null"))
    }
}
