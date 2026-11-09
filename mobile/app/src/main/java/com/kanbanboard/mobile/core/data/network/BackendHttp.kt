package com.kanbanboard.mobile.core.data.network

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File

/** Общий транспорт защищённых запросов /api/v1; один повтор после ротации JWT при 401. */
class BackendHttp(
    baseUrl: String,
    private val auth: AuthApi,
    private val http: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val apiRoot = "${baseUrl.trimEnd('/')}/api/v1/".toHttpUrl()
    private val refreshLock = Mutex()

    suspend fun get(path: String, query: Map<String, String> = emptyMap()): JsonElement =
        request("GET", path, query)

    suspend fun post(path: String, body: JsonElement): JsonElement = request("POST", path, body = body)
    suspend fun patch(path: String, body: JsonElement): JsonElement = request("PATCH", path, body = body)
    suspend fun put(path: String, body: JsonElement): JsonElement = request("PUT", path, body = body)
    suspend fun delete(path: String): JsonElement = request("DELETE", path)

    suspend fun upload(path: String, file: File, fileName: String, mimeType: String, fields: Map<String, String> = emptyMap(), method: String = "POST"): JsonElement {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            addFormDataPart("file", fileName, file.asRequestBody(mimeType.toMediaType()))
            fields.forEach { (key, value) -> addFormDataPart(key, value) }
        }.build()
        return parse(authorized(path, method, body))
    }

    suspend fun download(path: String): ByteArray {
        val reply = authorized(path, "GET", null)
        if (reply.code !in 200..299) throw IOException("Backend: HTTP ${reply.code}")
        return reply.bytes
    }

    private suspend fun request(
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        body: JsonElement? = null,
    ): JsonElement {
        val firstToken = auth.accessToken() ?: throw IOException("Требуется вход")
        val first = execute(method, path, query, body, firstToken)
        if (first.code != 401) return parse(first)
        refreshLock.withLock {
            if (auth.accessToken() == firstToken) auth.refresh()
        }
        val nextToken = auth.accessToken() ?: throw IOException("Сессия истекла")
        return parse(execute(method, path, query, body, nextToken))
    }

    private suspend fun authorized(path: String, method: String, body: RequestBody?): Reply {
        val firstToken = auth.accessToken() ?: throw IOException("Требуется вход")
        val first = executeRaw(path, method, body, firstToken)
        if (first.code != 401) return first
        refreshLock.withLock {
            if (auth.accessToken() == firstToken) auth.refresh()
        }
        return executeRaw(path, method, body, auth.accessToken() ?: throw IOException("Сессия истекла"))
    }

    private suspend fun executeRaw(path: String, method: String, body: RequestBody?, token: String): Reply =
        withContext(Dispatchers.IO) {
            require(!path.startsWith('/') && ".." !in path) { "Некорректный путь API" }
            val request = Request.Builder().url(apiRoot.newBuilder().addPathSegments(path).build())
                .header("Authorization", "Bearer $token").method(method, body).build()
            http.newCall(request).execute().use { Reply(it.code, it.body?.bytes() ?: byteArrayOf()) }
        }

    private suspend fun execute(
        method: String,
        path: String,
        query: Map<String, String>,
        body: JsonElement?,
        token: String,
    ): Reply = withContext(Dispatchers.IO) {
        require(!path.startsWith('/') && ".." !in path) { "Некорректный путь API" }
        val url = apiRoot.newBuilder().addPathSegments(path).apply {
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val requestBody = body?.let { json.encodeToString(JsonElement.serializer(), it).toRequestBody("application/json".toMediaType()) }
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token")
            .method(method, requestBody).build()
        http.newCall(request).execute().use { Reply(it.code, it.body?.bytes() ?: byteArrayOf()) }
    }

    private fun parse(reply: Reply): JsonElement {
        if (reply.code !in 200..299) throw IOException("Backend: HTTP ${reply.code}")
        val body = reply.bytes.decodeToString()
        return if (body.isBlank()) kotlinx.serialization.json.JsonNull else json.parseToJsonElement(body)
    }

    private data class Reply(val code: Int, val bytes: ByteArray)
}
