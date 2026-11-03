package com.kanbanboard.mobile.core.data

import com.kanbanboard.mobile.core.data.schedule.IsuScheduleParser
import com.kanbanboard.mobile.core.model.LessonKind
import com.kanbanboard.mobile.core.model.StudyGroup
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тест на синтетической странице, похожей на таблицу расписания.
 * Когда появится сохранённая страница ИСУ (app/src/test/resources/isu_sample.html),
 * добавьте рядом тест на неё — он важнее этого.
 */
class IsuScheduleParserTest {
    private val today = LocalDate.of(2026, 11, 4) // среда

    private val html = """
        <html><body>
        <h3>Понедельник 02.11.2026</h3>
        <table>
          <tr><th>Время</th><th>Дисциплина</th><th>Ауд.</th><th>Преподаватель</th></tr>
          <tr><td>08:00-09:35</td><td>Мобильная разработка (лекция)</td><td>1-405</td><td>Иванов И.И.</td></tr>
          <tr><td>09:45-11:20</td><td>Базы данных</td><td>лаб.</td><td>ауд. 312</td><td>Петрова А.С.</td></tr>
        </table>
        <h3>Вторник</h3>
        <table>
          <tr><td>3 пара</td><td>Иностранный язык</td><td>практика</td><td>2-108</td></tr>
        </table>
        </body></html>
    """.trimIndent()

    @Test
    fun `parses rows with dates, times, kinds and teachers`() {
        val lessons = IsuScheduleParser.parse(html, StudyGroup.G203, today)
        assertEquals(3, lessons.size)

        val first = lessons[0]
        assertEquals(LocalDate.of(2026, 11, 2), first.date)
        assertEquals(LocalTime.of(8, 0), first.start)
        assertEquals("Мобильная разработка", first.title)
        assertEquals(LessonKind.LECTURE, first.kind)
        assertEquals("Иванов И.И.", first.teacher)
        assertTrue(first.room.contains("1-405"))

        assertEquals(LessonKind.LAB, lessons[1].kind)
        assertEquals("Базы данных", lessons[1].title)
    }

    @Test
    fun `day without date uses current week and pair number uses standard time`() {
        val tuesday = IsuScheduleParser.parse(html, StudyGroup.G204, today).last()
        assertEquals(LocalDate.of(2026, 11, 3), tuesday.date)
        assertEquals(LocalTime.of(12, 10), tuesday.start)
        assertEquals(LessonKind.PRACTICE, tuesday.kind)
        assertEquals(StudyGroup.G204, tuesday.group)
    }

    @Test
    fun `unknown page gives empty result`() {
        assertTrue(IsuScheduleParser.parse("<html><body><p>Сервис недоступен</p></body></html>", StudyGroup.G203, today).isEmpty())
    }
}
