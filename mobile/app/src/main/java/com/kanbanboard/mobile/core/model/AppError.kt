package com.kanbanboard.mobile.core.model

import kotlin.coroutines.cancellation.CancellationException

/** Ожидаемые ошибки предметной области. Сообщение показывается пользователю как есть. */
sealed class AppError(message: String) : Exception(message) {
    class Unauthorized : AppError("Сессия истекла. Войдите снова")
    class InvalidCredentials : AppError("Неверный логин или пароль")
    class Forbidden(message: String = "Недостаточно прав") : AppError(message)
    class NotFound(message: String = "Не найдено") : AppError(message)
    class Validation(message: String) : AppError(message)
}

/**
 * Превращает [AppError] в Result.failure. Отмену корутины и неожиданные исключения
 * не глотает: ошибки программиста должны падать заметно, а не превращаться в тост.
 */
suspend fun <T> appResult(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: AppError) {
    Result.failure(e)
}
