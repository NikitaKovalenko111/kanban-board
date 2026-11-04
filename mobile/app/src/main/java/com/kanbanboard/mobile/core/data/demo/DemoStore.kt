package com.kanbanboard.mobile.core.data.demo

import com.kanbanboard.mobile.core.model.UserId
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json

/**
 * Сохранение демо-сервера на устройстве: всё, что создали пользователи (аккаунты, проекты,
 * задачи, события), переживает перезапуск приложения. Состояние пишется в JSON в filesDir/demo.
 * Сбросить данные — «Очистить хранилище» приложения в настройках Android.
 */
class DemoStore(dir: File) {
    private val root = File(dir, "demo").apply { mkdirs() }
    private val stateFile = File(root, "state.json")
    private val sessionFile = File(root, "session.txt")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        allowStructuredMapKeys = true
    }

    /** Сохранённое состояние или null (первый запуск или файл повреждён — тогда стартуем с [DemoSeed]). */
    fun loadState(): DemoState? = try {
        stateFile.takeIf { it.isFile }?.readText()?.let { json.decodeFromString(DemoState.serializer(), it) }
    } catch (e: Exception) {
        null
    }

    fun loadSession(): UserId? =
        sessionFile.takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }?.let(::UserId)

    fun saveSession(userId: UserId?) {
        if (userId == null) sessionFile.delete() else sessionFile.writeText(userId.value)
    }

    @OptIn(FlowPreview::class)
    fun autosave(backend: DemoBackend, scope: CoroutineScope) {
        backend.state
            .drop(1)
            .debounce(SAVE_DELAY_MS)
            .onEach { state -> runCatching { write(state) } }
            .flowOn(Dispatchers.IO)
            .launchIn(scope)
    }

    /** Немедленная запись (используется автосохранением и тестами). */
    internal fun write(state: DemoState) {
        val temp = File(root, "state.json.tmp")
        temp.writeText(json.encodeToString(DemoState.serializer(), state))
        if (!temp.renameTo(stateFile)) {
            stateFile.delete()
            temp.renameTo(stateFile)
        }
    }

    private companion object {
        const val SAVE_DELAY_MS = 300L
    }
}
