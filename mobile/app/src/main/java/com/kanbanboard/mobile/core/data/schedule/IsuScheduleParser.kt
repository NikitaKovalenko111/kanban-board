package com.kanbanboard.mobile.core.data.schedule

import com.kanbanboard.mobile.core.model.Lesson
import com.kanbanboard.mobile.core.model.LessonKind
import com.kanbanboard.mobile.core.model.StudyGroup
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Разбор HTML-страницы расписания ИСУ УУНиТ.
 *
 * Разметку сайта из среды разработки проверить не удалось (сайт закрыт для автоматических
 * запросов), поэтому парсер опирается не на конкретные CSS-классы, а на содержимое строк
 * таблиц — так он переживёт мелкие изменения вёрстки:
 *  - заголовок дня: строка/элемент с названием дня недели и (обычно) датой «дд.мм.гггг»;
 *  - строка пары: время «08:00-09:35» (или номер пары «1 пара»), название дисциплины,
 *    вид занятия (лекция / практика / лабораторная), аудитория, преподаватель «Фамилия И.О.».
 * Если у дня нет даты, она вычисляется от понедельника недели [today].
 * Пустой результат означает «страница не распознана» — источник тогда берёт кэш.
 *
 * TODO(Самир): сохранить реальную страницу ИСУ в app/src/test/resources/isu_sample.html
 * и добавить тест на неё в IsuScheduleParserTest — так разбор будет закреплён на настоящей разметке.
 */
object IsuScheduleParser {

    private val dateRegex = Regex("""(\d{1,2})\.(\d{1,2})\.(\d{4})""")
    private val timeRangeRegex = Regex("""(\d{1,2})[:.](\d{2})\s*[-–—]\s*(\d{1,2})[:.](\d{2})""")
    private val pairNumberRegex = Regex("""(?i)(\d)\s*пара""")
    private val teacherRegex = Regex("""[А-ЯЁ][а-яё]+(?:-[А-ЯЁ][а-яё]+)?\s+[А-ЯЁ]\.\s?[А-ЯЁ]\.""")
    private val roomRegex = Regex("""(?i)(?:ауд\.?\s*|аудитория\s*|корп(?:ус)?\.?\s*\S+[,\s]+)?(?:\d{1,2}[-–]\d{2,4}[а-яa-z]?|\d{3,4}[а-яa-z]?)""")

    /** Стандартное время пар УУНиТ на случай, если на странице указан только номер пары. */
    private val pairTimes = listOf(
        LocalTime.of(8, 0) to LocalTime.of(9, 35),
        LocalTime.of(9, 45) to LocalTime.of(11, 20),
        LocalTime.of(12, 10) to LocalTime.of(13, 45),
        LocalTime.of(13, 55) to LocalTime.of(15, 30),
        LocalTime.of(16, 10) to LocalTime.of(17, 45),
        LocalTime.of(17, 55) to LocalTime.of(19, 30),
        LocalTime.of(19, 40) to LocalTime.of(21, 15),
    )

    private val dayNames = mapOf(
        "понедельник" to DayOfWeek.MONDAY, "вторник" to DayOfWeek.TUESDAY, "среда" to DayOfWeek.WEDNESDAY,
        "четверг" to DayOfWeek.THURSDAY, "пятница" to DayOfWeek.FRIDAY, "суббота" to DayOfWeek.SATURDAY,
        "воскресенье" to DayOfWeek.SUNDAY,
    )

    fun parse(html: String, group: StudyGroup, today: LocalDate): List<Lesson> {
        val document = Jsoup.parse(html)
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        var currentDate: LocalDate? = null
        val result = mutableListOf<Lesson>()

        // Обходим документ по порядку: заголовки дней и строки таблиц идут вперемешку.
        for (element in document.body()?.select("h1, h2, h3, h4, h5, caption, th, tr, div.day, div.date") ?: emptyList()) {
            val text = element.ownTextOrAll()
            dayHeader(text, monday)?.let { date ->
                // Строка с днём и без времени — это заголовок дня.
                if (timeRangeRegex.find(text) == null) {
                    currentDate = date
                    return@let
                }
            }
            if (element.tagName() != "tr") continue
            val lesson = parseRow(element, group, currentDate) ?: continue
            result += lesson
            currentDate = lesson.date
        }
        return result
            .distinctBy { Triple(it.date, it.start, it.title) }
            .sortedWith(compareBy<Lesson>({ it.date }, { it.start }))
    }

    private fun Element.ownTextOrAll(): String = text().replace(' ', ' ').trim()

    private fun dayHeader(text: String, monday: LocalDate): LocalDate? {
        val lower = text.lowercase()
        dateRegex.find(text)?.let { match ->
            val (d, m, y) = match.destructured
            return runCatching { LocalDate.of(y.toInt(), m.toInt(), d.toInt()) }.getOrNull()
        }
        val day = dayNames.entries.firstOrNull { lower.startsWith(it.key) }?.value ?: return null
        return monday.with(TemporalAdjusters.nextOrSame(day))
    }

    private fun parseRow(row: Element, group: StudyGroup, fallbackDate: LocalDate?): Lesson? {
        val cells = row.select("td, th").map { it.ownTextOrAll() }.filter { it.isNotBlank() }
        if (cells.isEmpty()) return null
        val full = cells.joinToString(" | ")

        val date = dateRegex.find(full)?.let { match ->
            val (d, m, y) = match.destructured
            runCatching { LocalDate.of(y.toInt(), m.toInt(), d.toInt()) }.getOrNull()
        } ?: fallbackDate ?: return null

        val (start, end) = timeRangeRegex.find(full)?.let { match ->
            val (h1, m1, h2, m2) = match.destructured
            runCatching { LocalTime.of(h1.toInt(), m1.toInt()) to LocalTime.of(h2.toInt(), m2.toInt()) }.getOrNull()
        } ?: pairNumberRegex.find(full)?.let { match ->
            pairTimes.getOrNull(match.groupValues[1].toInt() - 1)
        } ?: return null

        val kind = kindOf(full) ?: LessonKind.LECTURE
        val teacher = teacherRegex.find(full)?.value.orEmpty()
        // Название — самая длинная ячейка, где нет времени, даты и одного лишь вида занятия.
        val title = cells
            .map { cleanTitle(it, teacher) }
            .filter { it.length >= 3 && timeRangeRegex.find(it) == null && dateRegex.find(it) == null && !isOnlyKind(it) }
            .maxByOrNull { it.length }
            ?: return null
        val room = cells.firstOrNull { cell ->
            cell != title && roomRegex.containsMatchIn(cell) && timeRangeRegex.find(cell) == null && dateRegex.find(cell) == null
        }?.let { roomRegex.find(it)?.value?.trim() }.orEmpty()

        return Lesson(
            id = "isu-${group.code}-$date-$start",
            group = group,
            date = date,
            start = start,
            end = end,
            title = title,
            room = room,
            teacher = teacher,
            kind = kind,
        )
    }

    internal fun kindOf(text: String): LessonKind? {
        val lower = text.lowercase()
        return when {
            "лаб" in lower -> LessonKind.LAB
            "практ" in lower || Regex("""\bпр\.""").containsMatchIn(lower) || "семинар" in lower -> LessonKind.PRACTICE
            "лек" in lower -> LessonKind.LECTURE
            else -> null
        }
    }

    private fun isOnlyKind(text: String): Boolean =
        kindOf(text) != null && text.length < 25

    private fun cleanTitle(cell: String, teacher: String): String = cell
        .replace(teacher, "")
        .replace(Regex("""(?i)\((лек|лекция|практ\.?|практика|пр\.|лаб\.?|лабораторная)[^)]*\)"""), "")
        .replace(Regex("""\s{2,}"""), " ")
        .trim(' ', ',', ';', '-', '|')
}
