package com.kanbanboard.mobile.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BearerTokenInterceptorTest {
    @Test fun `JWT is added only while session is active`() {
        MockWebServer().use { server ->
            repeat(3) { server.enqueue(MockResponse().setBody("ok")) }
            val session = JwtSession()
            val http = OkHttpClient.Builder().addInterceptor(BearerTokenInterceptor(session)).build()
            val request = Request.Builder().url(server.url("/projects")).build()
            http.newCall(request).execute().close()
            assertNull(server.takeRequest().getHeader("Authorization"))
            session.set("header.payload.signature")
            http.newCall(request).execute().close()
            assertEquals("Bearer header.payload.signature", server.takeRequest().getHeader("Authorization"))
            session.clear()
            http.newCall(request).execute().close()
            assertNull(server.takeRequest().getHeader("Authorization"))
        }
    }
}
