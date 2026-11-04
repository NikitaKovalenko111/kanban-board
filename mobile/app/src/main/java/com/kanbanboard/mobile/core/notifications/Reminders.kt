package com.kanbanboard.mobile.core.notifications

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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kanbanboard.mobile.MainActivity
import com.kanbanboard.mobile.R
import com.kanbanboard.mobile.core.data.ScheduleRepository
import com.kanbanboard.mobile.core.data.SessionRepository
import com.kanbanboard.mobile.core.data.TeamRepository
import com.kanbanboard.mobile.core.data.settings.SettingsRepository
import com.kanbanboard.mobile.core.model.ReminderKeys
import com.kanbanboard.mobile.core.model.ReminderPresets
import com.kanbanboard.mobile.core.model.StudyGroup
import com.kanbanboard.mobile.core.time.Formats
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private const val CHANNEL_ID = "schedule_reminders"
private const val EXTRA_TITLE = "title"
private const val EXTRA_TEXT = "text"
private const val EXTRA_ID = "id"

/** Что и когда напомнить. */
data class PlannedReminder(val requestCode: Int, val triggerAt: Instant, val title: String, val text: String)

fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/**
 * Локальные уведомления через AlarmManager. Напоминания личные, поэтому планируются только
 * мои — по данным с сервера. При каждом изменении старые будильники отменяются и ставятся заново.
 * Используются неточные будильники (setAndAllowWhileIdle): без особого разрешения на точные,
 * погрешность — несколько минут, для напоминаний это приемлемо.
 */
class ReminderScheduler(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private var scheduled: List<Int> = emptyList()

    fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Напоминания расписания", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    @Synchronized
    fun replaceAll(reminders: List<PlannedReminder>) {
        scheduled.forEach { code -> alarms.cancel(pendingIntent(code, null)) }
        val now = Instant.now()
        val upcoming = reminders.filter { it.triggerAt.isAfter(now) }
        upcoming.forEach { reminder ->
            alarms.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminder.triggerAt.toEpochMilli(),
                pendingIntent(reminder.requestCode, reminder),
            )
        }
        scheduled = upcoming.map { it.requestCode }
    }

    private fun pendingIntent(code: Int, reminder: PlannedReminder?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            if (reminder != null) {
                putExtra(EXTRA_TITLE, reminder.title)
                putExtra(EXTRA_TEXT, reminder.text)
                putExtra(EXTRA_ID, code)
            }
        }
        return PendingIntent.getBroadcast(
            context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!hasNotificationPermission(context)) return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        val id = intent.getIntExtra(EXTRA_ID, 0)
        ReminderScheduler(context).ensureChannel()
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Разрешение отозвали между проверкой и показом — просто не показываем.
        }
    }
}

/**
 * Связывает мои напоминания с элементами расписания на ближайшие 30 дней и планирует будильники.
 * Живёт в области приложения; при выходе из аккаунта все будильники снимаются.
 */
class ReminderSync(
    private val scheduler: ReminderScheduler,
    private val sessions: SessionRepository,
    private val schedule: ScheduleRepository,
    private val team: TeamRepository,
    private val settings: SettingsRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        scheduler.ensureChannel()
        scope.launch {
            combine(sessions.session, settings.settings.map { it.pushEvents }.distinctUntilChanged()) { session, enabled ->
                session.takeIf { enabled }
            }.flatMapLatest { session ->
                // Нет сессии или напоминания выключены в настройках — снимаем все будильники.
                if (session == null) return@flatMapLatest flowOf(emptyList<PlannedReminder>())
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now()
                val horizon = today.plusDays(30)
                val groups = StudyGroup.entries.toSet()
                combine(
                    schedule.myReminders,
                    schedule.events(session.user.id, groups, Instant.now(), horizon.atStartOfDay(zone).toInstant()),
                    schedule.lessons(session.user.studyGroup, today, horizon),
                    team.members,
                ) { reminders, events, lessons, members ->
                    val names = members.associate { it.user.id to it.user.fullName }
                    buildList {
                        events.forEach { event ->
                            val key = ReminderKeys.event(event.id)
                            val title = if (event.masked) "${names[event.ownerId] ?: "Участник"}: занят" else event.title
                            reminders[key].orEmpty().forEach { minutes ->
                                add(plan(key, minutes, event.startsAt, title))
                            }
                        }
                        lessons.lessons.forEach { lesson ->
                            val key = ReminderKeys.lesson(lesson.id)
                            val start = lesson.date.atTime(lesson.start).atZone(zone).toInstant()
                            reminders[key].orEmpty().forEach { minutes ->
                                add(plan(key, minutes, start, "${lesson.kind.title}: ${lesson.title}"))
                            }
                        }
                    }
                }
            }.collect { scheduler.replaceAll(it) }
        }
    }

    private fun plan(key: String, minutes: Int, start: Instant, title: String) = PlannedReminder(
        requestCode = "$key:$minutes".hashCode(),
        triggerAt = start.minus(Duration.ofMinutes(minutes.toLong())),
        title = title,
        text = "Начало ${Formats.dateTime(start)} · ${ReminderPresets.label(minutes)}",
    )
}
