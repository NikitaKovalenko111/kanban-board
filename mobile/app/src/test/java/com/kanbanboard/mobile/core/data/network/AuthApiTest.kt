package com.kanbanboard.mobile.core.data.network

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
