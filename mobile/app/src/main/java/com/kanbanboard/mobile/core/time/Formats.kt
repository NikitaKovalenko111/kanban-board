package com.kanbanboard.mobile.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Единственное место форматирования дат для пользователя (MOB-10: дд.мм.гггг). */
object Formats {
    private val ru = Locale.forLanguageTag("ru")
    private val date = DateTimeFormatter.ofPattern("dd.MM.yyyy", ru)
    private val dateTime = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", ru)
    private val time = DateTimeFormatter.ofPattern("HH:mm", ru)

    private fun zone(): ZoneId = ZoneId.systemDefault()

    fun date(value: LocalDate): String = value.format(date)
    fun date(value: Instant): String = value.atZone(zone()).format(date)
    fun dateTime(value: Instant): String = value.atZone(zone()).format(dateTime)
    fun time(value: LocalTime): String = value.format(time)

    fun fileSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes Б"
        bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
        else -> String.format(ru, "%.1f МБ", bytes / (1024.0 * 1024.0))
    }
}
