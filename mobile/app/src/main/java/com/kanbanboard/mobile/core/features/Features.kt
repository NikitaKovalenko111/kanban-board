package com.kanbanboard.mobile.core.features

/**
 * Функции, которых нет в веб-версии. По ТЗ спринта 3 их не удаляют, а блокируют до решения команды:
 * кнопка видна, но неактивна. Чтобы включить функцию, поменяйте значение на true —
 * код и проверки прав на демо-сервере уже готовы.
 */
object Features {
    /** Удаление аккаунта капитаном (окно участника). */
    const val DELETE_ACCOUNT = false

    /** «Сделать капитаном» / «Снять роль капитана» (окно участника). */
    const val CHANGE_TEAM_ROLE = false

    /** Удаление проекта (карточка проекта). */
    const val DELETE_PROJECT = false

    /** Удаление файла задачи. */
    const val DELETE_ATTACHMENT = false

    /** Удаление своего типа события. */
    const val DELETE_EVENT_TYPE = false

    const val BLOCKED_HINT = "Недоступно до решения команды"
}
