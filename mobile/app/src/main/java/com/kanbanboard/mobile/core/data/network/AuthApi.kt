package com.kanbanboard.mobile.core.data.network

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** HTTP-часть авторизации по backend/docs/openapi.yaml из ветки backend-handlers. */
class AuthApi(
    private val baseUrl: String,
    private val http: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private var tokens: TokenPair? = null

    suspend fun login(login: String, password: String): LoginResponse {
        val response = post("auth/login", LoginRequest(login, password), authorized = false)
        return json.decodeFromString<LoginResponse>(response).also {
            tokens = TokenPair(it.accessToken, it.refreshToken)
        }
    }

    suspend fun refresh(): TokenResponse {
        val refreshToken = tokens?.refreshToken ?: throw IOException("Нет refresh-токена")
        val response = post("auth/refresh", RefreshRequest(refreshToken), authorized = false)
        return json.decodeFromString<TokenResponse>(response).also {
            tokens = TokenPair(it.accessToken, it.refreshToken)
        }
    }

    suspend fun me(): UserDto = withContext(Dispatchers.IO) {
        val accessToken = tokens?.accessToken ?: throw IOException("Требуется вход")
        val request = Request.Builder().url(url("me"))
            .header("Authorization", "Bearer $accessToken").get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Профиль: HTTP ${response.code}")
            json.decodeFromString<UserDto>(response.body?.string() ?: throw IOException("Пустой ответ"))
        }
    }

    suspend fun logout() {
        val refreshToken = tokens?.refreshToken
        try {
            if (refreshToken != null) post("auth/logout", RefreshRequest(refreshToken))
        } finally {
            tokens = null
        }
    }

    private suspend inline fun <reified T> post(path: String, body: T, authorized: Boolean = true): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url(path))
                .post(json.encodeToString(body).toRequestBody("application/json".toMediaType()))
            if (authorized) tokens?.accessToken?.let { request.header("Authorization", "Bearer $it") }
            http.newCall(request.build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("$path: HTTP ${response.code}")
                response.body?.string().orEmpty()
            }
        }

    private fun url(path: String) = "${baseUrl.trimEnd('/')}/api/v1/$path"

    private data class TokenPair(val accessToken: String, val refreshToken: String)
}

@Serializable data class LoginRequest(val login: String, val password: String)
@Serializable data class RefreshRequest(val refreshToken: String)
@Serializable data class TokenResponse(val accessToken: String, val refreshToken: String, val expiresIn: Long)
@Serializable data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val user: UserDto,
)
@Serializable data class UserDto(
    val id: String,
    val fullName: String,
    val username: String,
    val studentGroup: String,
    val role: String,
    val schedulePrivacy: String,
    val joinedAt: String,
    val active: Boolean,
    val imageUrl: String? = null,
    val contact: String? = null,
)
