package com.kanbanboard.mobile.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kanbanboard.mobile.MainActivity
import com.kanbanboard.mobile.R
import com.kanbanboard.mobile.core.data.NotificationRepository
import com.kanbanboard.mobile.core.data.settings.SettingsRepository
import com.kanbanboard.mobile.core.model.NotificationKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private const val TASKS_CHANNEL_ID = "task_updates"

/**
 * Системные уведомления о задачах: комментарии, возвраты на доработку, смена статуса.
 * Показываются для новых непрочитанных уведомлений, если они включены в настройках.
 *
 * Пока это работает, только когда процесс приложения жив (демо-режим). С настоящим сервером
 * сюда добавится FCM — серверные push будут приходить и при закрытом приложении.
 */
class TaskPushSync(
    private val context: Context,
    private val notifications: NotificationRepository,
    private val settings: SettingsRepository,
) {
    private val shown = mutableSetOf<String>()
    private var initialized = false

    fun start(scope: CoroutineScope) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(TASKS_CHANNEL_ID, "Задачи", NotificationManager.IMPORTANCE_DEFAULT),
        )
        scope.launch {
            combine(notifications.notifications, settings.settings) { list, prefs -> list to prefs.pushTasks }
                .collect { (list, enabled) ->
                    val fresh = list.filter { !it.read && it.kind != NotificationKind.EVENT && it.id !in shown }
                    shown += fresh.map { it.id }
                    // Уведомления, которые уже были при запуске, не показываем повторно.
                    if (!initialized) {
                        initialized = true
                        return@collect
                    }
                    if (!enabled || !hasNotificationPermission(context)) return@collect
                    fresh.forEach { item -> show(item.id.hashCode(), item.title, item.text) }
                }
        }
    }

    private fun show(id: Int, title: String, text: String) {
        val open = PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, TASKS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Разрешение отозвали — ничего не показываем.
        }
    }
}
