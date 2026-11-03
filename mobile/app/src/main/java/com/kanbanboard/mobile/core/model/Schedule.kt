@file:UseSerializers(InstantSerializer::class, LocalDateSerializer::class, LocalTimeSerializer::class)

package com.kanbanboard.mobile.core.model

import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.LocalTime

@Serializable @JvmInline value class EventId(val value: String)
@Serializable @JvmInline value class EventTypeId(val value: String)

@Serializable
enum class LessonKind(val title: String) {
    LECTURE("Лекция"),
    PRACTICE("Практика"),
    LAB("Лабораторная"),
}

/** Пара из ИСУ. Время — местное время университета. */
@Serializable
data class Lesson(
    val id: String,
    val group: StudyGroup,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val title: String,
    val room: String,
    val teacher: String,
    val kind: LessonKind,
)

enum class LessonsOrigin {
    /** Свежие данные с сайта ИСУ. */
    ISU,
    /** ИСУ недоступна — показан сохранённый на устройстве кэш. */
    CACHE,
    /** ИСУ недоступна и кэша нет — пар нет (состояние UNAVAILABLE в БД). */
    UNAVAILABLE,
}

/** Расписание группы вместе с источником данных (fallback-режим из ТЗ). */
data class LessonsSnapshot(
    val lessons: List<Lesson>,
    val updatedAt: Instant?,
    val source: LessonsOrigin,
)

@Serializable
enum class EventVisibility(val title: String) {
    TEAM("Команда"),
    GROUP("Группа"),
    PERSONAL("Лично"),
}

/** Тип события: базовый (ownerId == null) или созданный участником. Цвет — ARGB. */
@Serializable
data class EventType(
    val id: EventTypeId,
    val title: String,
    val color: Long,
    val ownerId: UserId?,
    val defaultReminders: Set<Int>,
) {
    val isBuiltIn: Boolean get() = ownerId == null
}

/**
 * Событие в том виде, в каком его видит текущий пользователь.
 * Чужие личные события урезаются по настройке приватности владельца ([SchedulePrivacy]):
 * [masked] — видно только «Занят» и время; [partial] — видно название (и, возможно, описание),
 * но не всё. Полностью скрытые события в выдачу не попадают вовсе.
 */
@Serializable
data class ScheduleEvent(
    val id: EventId,
    val ownerId: UserId,
    val title: String,
    val description: String,
    val location: String,
    val startsAt: Instant,
    /** Окончание необязательно (ТЗ Спринта 2). */
    val endsAt: Instant?,
    val visibility: EventVisibility,
    val group: StudyGroup?,
    val typeId: EventTypeId?,
    val masked: Boolean,
    val partial: Boolean = false,
)

data class EventDraft(
    val title: String,
    val description: String,
    val location: String,
    val startsAt: Instant,
    val endsAt: Instant?,
    val visibility: EventVisibility,
    val group: StudyGroup?,
    val typeId: EventTypeId,
    /** Мои напоминания для нового события; у остальных участников своих напоминаний нет. */
    val myReminders: Set<Int>,
)

data class EventTypeDraft(val title: String, val color: Long, val defaultReminders: Set<Int>)

/**
 * Напоминания — личная подписка пользователя на элемент расписания.
 * Ключ элемента общий для событий и пар, чтобы напомнить можно было о чём угодно в ленте.
 */
object ReminderKeys {
    fun event(id: EventId): String = "event:${id.value}"
    fun lesson(id: String): String = "lesson:$id"
}

object ReminderPresets {
    /** Минуты до начала. */
    val all: List<Int> = listOf(5, 10, 15, 30, 60, 120, 180, 360, 720, 1440, 2880, 4320, 10080)

    /** Максимум — 4 недели. */
    const val MAX_MINUTES: Int = 40320

    fun label(minutes: Int): String {
        val weeks = minutes / 10080
        if (minutes % 10080 == 0 && weeks > 0) return "за $weeks нед."
        val days = minutes / 1440
        val hours = (minutes % 1440) / 60
        val mins = minutes % 60
        val parts = buildList {
            if (days > 0) add("$days д")
            if (hours > 0) add("$hours ч")
            if (mins > 0) add("$mins мин")
        }
        return "за " + parts.joinToString(" ").ifEmpty { "0 мин" }
    }
}
