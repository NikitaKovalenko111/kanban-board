package com.kanbanboard.mobile.data

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kanbanboard.mobile.MainActivity
import com.kanbanboard.mobile.R
import com.kanbanboard.mobile.model.TeamEvent
import java.time.LocalDateTime
import java.time.ZoneId

object ReminderScheduler {
    private const val channelId = "event_reminders"

    fun schedule(context: Context, event: TeamEvent) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val start = runCatching { LocalDateTime.parse("${event.date}T${event.time}")
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull() ?: return
        event.reminderMinutes.forEach { minutes ->
            val trigger = start - minutes * 60_000L
            if (trigger <= System.currentTimeMillis()) return@forEach
            val intent = Intent(context, EventReminderReceiver::class.java).apply {
                putExtra("title", event.title)
                putExtra("minutes", minutes)
                putExtra("id", event.id)
            }
            val pending = PendingIntent.getBroadcast(
                context, (event.id.toString() + ":" + minutes).hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        }
    }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(channelId, "Напоминания о событиях", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun show(context: Context, title: String, minutes: Int, id: Long) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        ensureChannel(context)
        val openApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val label = when (minutes) {
            10080 -> "через неделю"
            1440 -> "через день"
            60 -> "через час"
            30 -> "через 30 минут"
            else -> "скоро"
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText("Событие начнётся $label")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify((id.toString() + ":" + minutes).hashCode(), notification)
    }
}

class EventReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.show(context, intent.getStringExtra("title") ?: return, intent.getIntExtra("minutes", 0), intent.getLongExtra("id", 0))
    }
}

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            SharedPreferencesAppStorage(context).events().forEach { ReminderScheduler.schedule(context, it) }
        }
    }
}
