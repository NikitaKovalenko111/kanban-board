package com.kanbanboard.mobile.di

import com.kanbanboard.mobile.core.data.demo.DemoAccount
import com.kanbanboard.mobile.core.data.demo.DemoSchedule
import com.kanbanboard.mobile.core.data.demo.DemoSeed
import com.kanbanboard.mobile.core.data.demo.DemoState
import com.kanbanboard.mobile.core.model.AppNotification
import com.kanbanboard.mobile.core.model.Attachment
import com.kanbanboard.mobile.core.model.AttachmentKind
import com.kanbanboard.mobile.core.model.BoardColumn
import com.kanbanboard.mobile.core.model.ColumnId
import com.kanbanboard.mobile.core.model.ColumnKind
import com.kanbanboard.mobile.core.model.EventId
import com.kanbanboard.mobile.core.model.EventType
import com.kanbanboard.mobile.core.model.EventTypeId
import com.kanbanboard.mobile.core.model.EventVisibility
import com.kanbanboard.mobile.core.model.Lesson
import com.kanbanboard.mobile.core.model.LessonKind
import com.kanbanboard.mobile.core.model.NotificationKind
import com.kanbanboard.mobile.core.model.Project
import com.kanbanboard.mobile.core.model.ProjectId
import com.kanbanboard.mobile.core.model.ProjectSource
import com.kanbanboard.mobile.core.model.ScheduleEvent
import com.kanbanboard.mobile.core.model.SchedulePrivacy
import com.kanbanboard.mobile.core.model.SourceKind
import com.kanbanboard.mobile.core.model.StudyGroup
import com.kanbanboard.mobile.core.model.Task
import com.kanbanboard.mobile.core.model.TaskAction
import com.kanbanboard.mobile.core.model.TaskComment
import com.kanbanboard.mobile.core.model.TaskHistoryEntry
import com.kanbanboard.mobile.core.model.TaskId
import com.kanbanboard.mobile.core.model.TaskPriority
import com.kanbanboard.mobile.core.model.Team
import com.kanbanboard.mobile.core.model.TeamMember
import com.kanbanboard.mobile.core.model.TeamRole
import com.kanbanboard.mobile.core.model.User
import com.kanbanboard.mobile.core.model.UserId
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Мок-данные для показа и тестирования: вся команда, три проекта с задачами во всех колонках,
 * история выполненных задач, события, свои типы событий, файлы и уведомления.
 * Пароли: капитаны — lead12345, участники — member12345.
 * Не зависит от Android, поэтому проверяется обычными unit-тестами.
 */
object MockData {
    const val LEAD_PASSWORD = "lead12345"
    const val MEMBER_PASSWORD = "member12345"

    val nikita = DemoSeed.LEAD_ID
    val samir = DemoSeed.MEMBER_ID
    val radmir = UserId("u-radmir")
    val denis = UserId("u-denis")
    val almaz = UserId("u-almaz")
    val alena = UserId("u-alena")
    val alexey = UserId("u-alexey")
    val maria = UserId("u-maria")
    val ilya = UserId("u-ilya")

    val kanban = ProjectId("p-kanban")
    val hackathon = ProjectId("p-hackathon")
    val labs = ProjectId("p-labs")

    /**
     * [writeFile] кладёт текстовый файл в хранилище и возвращает (ссылка, размер);
     * в unit-тестах можно не передавать — тогда файлы существуют только как записи.
     */
    fun create(
        clock: Clock,
        writeFile: (name: String, content: String) -> Pair<String, Long>? = { _, _ -> null },
    ): DemoState {
        val now = clock.instant()
        val today = LocalDate.now(clock)
        fun daysAgo(days: Long): Instant = now.minus(Duration.ofDays(days))
        fun inDays(days: Long, hours: Long = 0): Instant = now.plus(Duration.ofDays(days)).plus(Duration.ofHours(hours))
        fun file(name: String, content: String): Pair<String?, Long> =
            writeFile(name, content)?.let { (ref, size) -> ref to size } ?: (null to content.length.toLong())

        val (matrixRef, matrixSize) = file("access-matrix.md", ACCESS_MATRIX)
        val (qaRef, qaSize) = file("QA_REPORT.md", QA_REPORT)
        val (protocolRef, protocolSize) = file("Протокол спринта 3.txt", PROTOCOL)

        fun account(
            id: UserId, name: String, login: String, group: StudyGroup, role: TeamRole,
            privacy: SchedulePrivacy = SchedulePrivacy.BUSY,
        ) = DemoAccount(
            TeamMember(User(id, name, login, group, null, true, today.minusDays(60), schedulePrivacy = privacy), role),
            if (role == TeamRole.CAPTAIN) LEAD_PASSWORD else MEMBER_PASSWORD,
        )

        val accounts = listOf(
            account(nikita, "Никита Коваленко", "nikita", StudyGroup.G204, TeamRole.CAPTAIN, SchedulePrivacy.TITLE),
            account(radmir, "Радмир Байсалямов", "radmir", StudyGroup.G204, TeamRole.MEMBER, SchedulePrivacy.TITLE_AND_DESCRIPTION),
            account(almaz, "Алмаз Магасумов", "almaz", StudyGroup.G204, TeamRole.MEMBER),
            account(alena, "Алёна Леушина", "alena", StudyGroup.G204, TeamRole.MEMBER, SchedulePrivacy.NOTHING),
            account(denis, "Денис Стрельников", "denis", StudyGroup.G203, TeamRole.MEMBER),
            account(samir, "Самир Миргазямов", "samir", StudyGroup.G203, TeamRole.MEMBER),
            // Тестовые аккаунты из QA-отчёта: второй капитан и участники для проверки прав.
            account(alexey, "Алексей Морозов", "alexey", StudyGroup.G203, TeamRole.CAPTAIN),
            account(maria, "Мария Волкова", "maria", StudyGroup.G204, TeamRole.MEMBER),
            account(ilya, "Илья Горин", "ilya", StudyGroup.G203, TeamRole.MEMBER),
        )

        val projects = listOf(
            Project(
                kanban, "Kanban Board", "Основное приложение команды: веб, Android и backend.",
                mapOf(
                    nikita to "Тимлид, frontend", radmir to "Scrum master", samir to "Android",
                    denis to "Backend", almaz to "QA", alena to "DevOps", maria to "Дизайн", ilya to "Frontend",
                ),
                sources = listOf(
                    ProjectSource("s-1", SourceKind.LINK, "Документация спринтов", "https://docs.google.com/document/d/demo", null, radmir, daysAgo(14)),
                    ProjectSource("s-2", SourceKind.LINK, "Репозиторий kanban-board", "https://github.com/NikitaKovalenko111/kanban-board", null, nikita, daysAgo(20)),
                    ProjectSource("s-3", SourceKind.FILE, "access-matrix.md", null, matrixSize, denis, daysAgo(12), matrixRef),
                    ProjectSource("s-4", SourceKind.FILE, "QA_REPORT.md", null, qaSize, almaz, daysAgo(9), qaRef),
                    ProjectSource("s-5", SourceKind.FILE, "Протокол спринта 3.txt", null, protocolSize, radmir, daysAgo(2), protocolRef),
                ),
            ),
            Project(
                hackathon, "Хакатон", "Прототип и питч к студенческому хакатону.",
                mapOf(nikita to "Капитан хакатона", samir to "Mobile", denis to "Backend", maria to "Дизайн"),
            ),
            Project(
                labs, "Учебные лабы", "Лабораторные и курсовые работы по дисциплинам.",
                mapOf(radmir to "Отчёты", almaz to "Проверка", alena to "Стенд", ilya to "Код", samir to "Код"),
            ),
        )

        val suffixes = listOf("backlog", "work", "review", "done")
        fun col(project: ProjectId, suffix: String) = ColumnId("${project.value}-$suffix")
        val columns = projects.flatMap { project ->
            DemoSeed.STANDARD_COLUMNS.mapIndexed { index, (title, color) ->
                BoardColumn(
                    col(project.id, suffixes[index]), project.id, title, index,
                    ColumnKind.forPosition(index, DemoSeed.STANDARD_COLUMNS.size), color,
                )
            }
        }

        var counter = 0
        fun hid() = "h-${counter++}"
        fun created(at: Instant, column: String = "Ожидает") = TaskHistoryEntry(hid(), TaskAction.CREATED, nikita, at, toColumn = column)
        fun assigned(at: Instant, name: String) = TaskHistoryEntry(hid(), TaskAction.ASSIGNED, nikita, at, comment = name)
        fun taken(who: UserId, at: Instant) = TaskHistoryEntry(hid(), TaskAction.TAKEN, who, at, "Ожидает", "В работе")
        fun submitted(who: UserId, at: Instant) = TaskHistoryEntry(hid(), TaskAction.SUBMITTED, who, at, "В работе", "На проверке")
        fun returned(at: Instant, note: String) = TaskHistoryEntry(hid(), TaskAction.RETURNED, nikita, at, "На проверке", "В работе", note)
        fun accepted(at: Instant) = TaskHistoryEntry(hid(), TaskAction.ACCEPTED, nikita, at, "На проверке", "Выполнено")

        /** Выполненная задача с историей: создана → взята → (возвраты) → сдана → принята. */
        fun doneTask(
            id: String, project: ProjectId, title: String, who: UserId,
            takenDaysAgo: Long, doneDaysAgo: Long, returns: Int, priority: TaskPriority = TaskPriority.NORMAL,
        ): Task {
            val history = buildList {
                add(created(daysAgo(takenDaysAgo + 1)))
                add(taken(who, daysAgo(takenDaysAgo)))
                repeat(returns) { index ->
                    val at = daysAgo(takenDaysAgo - 1 - index.toLong()).coerceAtMost(daysAgo(doneDaysAgo))
                    add(submitted(who, at.minus(Duration.ofHours(3))))
                    add(returned(at, "Поправить замечания ревью"))
                }
                add(submitted(who, daysAgo(doneDaysAgo).minus(Duration.ofHours(4))))
                add(accepted(daysAgo(doneDaysAgo)))
            }
            return Task(
                TaskId(id), project, col(project, "done"), 0, title, "", priority, who, nikita,
                daysAgo(takenDaysAgo + 1), null, daysAgo(doneDaysAgo), emptyList(), emptyList(), history,
            )
        }

        val tasks = listOf(
            Task(
                TaskId("t-1"), kanban, col(kanban, "backlog"), 0,
                "Экран расписания в веб-версии",
                "Выбор группы в «Моё расписание», фильтр по типам событий, расписание других участников с приватностью.",
                TaskPriority.HIGH, null, nikita, daysAgo(2), inDays(4), null,
                emptyList(), emptyList(), listOf(created(daysAgo(2))),
            ),
            Task(
                TaskId("t-2"), kanban, col(kanban, "backlog"), 1,
                "Таблица совместимости Android 10–17",
                "Эмуляторы Android 10, 13, 15 и 17; компактный и большой телефон, планшет; увеличенный шрифт.",
                TaskPriority.NORMAL, almaz, nikita, daysAgo(1), inDays(3, 5), null,
                emptyList(), emptyList(), listOf(created(daysAgo(1)), assigned(daysAgo(1), "Алмаз Магасумов")),
            ),
            Task(
                TaskId("t-3"), kanban, col(kanban, "work"), 0,
                "Матрица прав в REST API",
                "Проверка JWT и прав на смену статуса и сдачу решения (QA-01, QA-02) на уровне обработчиков.",
                TaskPriority.HIGH, denis, nikita, daysAgo(5), inDays(1, 2), null,
                listOf(Attachment("a-1", "access-matrix.md", matrixSize, AttachmentKind.SOURCE, nikita, daysAgo(5), matrixRef)),
                listOf(TaskComment("c-1", denis, "Начал с middleware, эндпоинты задач — следующие.", daysAgo(2))),
                listOf(created(daysAgo(5)), assigned(daysAgo(5), "Денис Стрельников"), taken(denis, daysAgo(4))),
            ),
            Task(
                TaskId("t-4"), kanban, col(kanban, "work"), 1,
                "Контейнеризация и секреты для деплоя",
                "Dockerfile для backend и frontend, docker-compose, SSH-ключи в GitHub Secrets.",
                TaskPriority.NORMAL, alena, nikita, daysAgo(6), inDays(-1), null,
                emptyList(), emptyList(),
                listOf(created(daysAgo(6)), taken(alena, daysAgo(5))),
            ),
            Task(
                TaskId("t-5"), kanban, col(kanban, "review"), 0,
                "Парсер расписания ИСУ в мобильном приложении",
                "Группы 203 и 204, кэш на устройстве на случай недоступности ИСУ.",
                TaskPriority.HIGH, samir, nikita, daysAgo(7), inDays(2), null,
                emptyList(),
                listOf(TaskComment("c-2", nikita, "Замечания: Нет предупреждения, когда показан кэш", daysAgo(3))),
                listOf(
                    created(daysAgo(7)), taken(samir, daysAgo(6)), submitted(samir, daysAgo(4)),
                    returned(daysAgo(3), "Нет предупреждения, когда показан кэш"), submitted(samir, daysAgo(1)),
                ),
            ),
            Task(
                TaskId("t-6"), kanban, col(kanban, "done"), 0,
                "QA-отчёт по веб-версии",
                "Сквозное тестирование, перечень дефектов QA-01…QA-13.",
                TaskPriority.NORMAL, almaz, nikita, daysAgo(12), daysAgo(8), daysAgo(9),
                listOf(Attachment("a-3", "QA_REPORT.md", qaSize, AttachmentKind.RESULT, almaz, daysAgo(10), qaRef)),
                emptyList(),
                listOf(created(daysAgo(12)), taken(almaz, daysAgo(11)), submitted(almaz, daysAgo(10)), accepted(daysAgo(9))),
            ),
            doneTask("t-9", kanban, "Каркас веб-приложения на React/Vite", nikita, 30, 26, returns = 0),
            doneTask("t-10", kanban, "C4-диаграммы и ERD", denis, 24, 18, returns = 1),
            doneTask("t-11", kanban, "CI/CD в GitHub Actions", alena, 18, 11, returns = 2),
            doneTask("t-12", kanban, "Шаблоны протоколов спринтов", radmir, 9, 6, returns = 0, priority = TaskPriority.LOW),
            doneTask("t-13", kanban, "Нижняя панель: статичная и плавающая", samir, 6, 3, returns = 1),
            doneTask("t-14", kanban, "Сбор пожеланий команды", radmir, 3, 1, returns = 0),
            Task(
                TaskId("t-7"), hackathon, col(hackathon, "backlog"), 0,
                "Прототип экрана питча",
                "Один экран с ключевой метрикой и демо-данными.",
                TaskPriority.NORMAL, null, nikita, daysAgo(1), inDays(6), null,
                emptyList(), emptyList(), listOf(created(daysAgo(1))),
            ),
            Task(
                TaskId("t-8"), labs, col(labs, "work"), 0,
                "Отчёт по лабораторной №3",
                "Оформить по шаблону кафедры, приложить исходники.",
                TaskPriority.NORMAL, radmir, nikita, daysAgo(3), inDays(2, 3), null,
                emptyList(), emptyList(),
                listOf(created(daysAgo(3)), taken(radmir, daysAgo(2))),
            ),
            doneTask("t-15", labs, "Лабораторная по теории вероятностей", ilya, 10, 7, returns = 1),
        )

        val zone = ZoneId.systemDefault()
        fun at(daysFromToday: Long, hour: Int, minute: Int = 0) =
            today.plusDays(daysFromToday).atTime(hour, minute).atZone(zone).toInstant()

        val customTypes = listOf(
            EventType(EventTypeId("t-hack"), "Хакатон", 0xFFDA8A37, nikita, setOf(10080, 1440)),
            EventType(EventTypeId("t-gym"), "Спорт", 0xFF2E9E8F, samir, setOf(30)),
        )
        val events = listOf(
            ScheduleEvent(
                EventId("e-1"), nikita, "Демо спринта", "Показываем доску и мобилку, собираем замечания.",
                "Discord, «Спринты проектов»", at(1, 19), at(1, 20, 30), EventVisibility.TEAM, null, EventTypeId("t-meeting"), false,
            ),
            ScheduleEvent(
                EventId("e-2"), radmir, "Сдать протокол созвона", "", "Google Docs",
                at(2, 23), null, EventVisibility.TEAM, null, EventTypeId("t-deadline"), false,
            ),
            ScheduleEvent(
                EventId("e-3"), samir, "Консультация по ОС", "Вопросы по лабораторной №3.", "Ауд. 1-405",
                at(3, 15, 20), at(3, 16, 50), EventVisibility.GROUP, StudyGroup.G203, EventTypeId("t-study"), false,
            ),
            ScheduleEvent(
                EventId("e-4"), almaz, "Пересдача матанализа", "", "Ауд. 2-210",
                at(4, 11, 40), at(4, 13, 10), EventVisibility.GROUP, StudyGroup.G204, EventTypeId("t-study"), false,
            ),
            ScheduleEvent(
                EventId("e-5"), samir, "Тренировка", "Ноги и кардио.", "Спортзал",
                at(0, 18, 30), at(0, 20), EventVisibility.PERSONAL, null, EventTypeId("t-gym"), false,
            ),
            ScheduleEvent(
                EventId("e-6"), nikita, "Встреча с куратором", "Обсудить тему курсовой.", "Ауд. 3-112",
                at(1, 13, 30), at(1, 14), EventVisibility.PERSONAL, null, EventTypeId("t-personal"), false,
            ),
            ScheduleEvent(
                EventId("e-7"), radmir, "Дописать отчёт по ПД", "Разделы 3–5.", "Дома",
                at(2, 10), at(2, 13), EventVisibility.PERSONAL, null, EventTypeId("t-personal"), false,
            ),
            ScheduleEvent(
                EventId("e-8"), nikita, "Хакатон", "Регистрация до 10:00.", "Коворкинг университета",
                at(6, 10), null, EventVisibility.TEAM, null, EventTypeId("t-hack"), false,
            ),
        )

        val notifications = mapOf(
            samir to listOf(
                AppNotification(
                    "n-1", NotificationKind.RETURNED, "Парсер расписания ИСУ в мобильном приложении",
                    "Вернули на доработку: Нет предупреждения, когда показан кэш", daysAgo(3), read = true, taskId = TaskId("t-5"),
                ),
                AppNotification(
                    "n-2", NotificationKind.COMMENT, "Нижняя панель: статичная и плавающая",
                    "Никита Коваленко: плавающий вариант отличный", daysAgo(3), read = false, taskId = TaskId("t-13"),
                ),
            ),
            nikita to listOf(
                AppNotification(
                    "n-3", NotificationKind.STATUS, "Парсер расписания ИСУ в мобильном приложении",
                    "Самир Миргазямов сдал(а) задачу на проверку", daysAgo(1), read = false, taskId = TaskId("t-5"),
                ),
            ),
        )

        return DemoState(
            team = Team("Код в сапоге", "Студенческая команда: задачи, проекты и расписание в одном месте."),
            accounts = accounts,
            projects = projects,
            columns = columns,
            tasks = tasks,
            events = events,
            eventTypes = DemoSchedule.builtInTypes + customTypes,
            reminders = mapOf(
                "${samir.value}|event:e-1" to setOf(60, 15),
                "${nikita.value}|event:e-8" to setOf(1440),
            ),
            notifications = notifications,
        )
    }

    // ---------- Пример пар (показывается, только если ИСУ недоступна и кэша нет) ----------

    private data class Slot(val day: DayOfWeek, val pair: Int, val title: String, val kind: LessonKind, val room: String, val teacher: String)

    /** Время пар УУНиТ. */
    private val pairTimes = listOf(
        LocalTime.of(8, 0) to LocalTime.of(9, 35),
        LocalTime.of(9, 45) to LocalTime.of(11, 20),
        LocalTime.of(12, 10) to LocalTime.of(13, 45),
        LocalTime.of(13, 55) to LocalTime.of(15, 30),
        LocalTime.of(16, 10) to LocalTime.of(17, 45),
    )

    private val common = listOf(
        Slot(DayOfWeek.MONDAY, 1, "Теория вероятностей и мат. статистика", LessonKind.LECTURE, "1-405", "Иванова Е.А."),
        Slot(DayOfWeek.TUESDAY, 3, "Проектная деятельность", LessonKind.PRACTICE, "3-112", "Сазонова Е.Ю."),
        Slot(DayOfWeek.THURSDAY, 2, "Математический анализ", LessonKind.LECTURE, "1-405", "Смирнов А.Н."),
        Slot(DayOfWeek.FRIDAY, 4, "Операционные системы", LessonKind.LECTURE, "2-301", "Кузнецов Д.И."),
    )

    private val byGroup = mapOf(
        StudyGroup.G203 to listOf(
            Slot(DayOfWeek.MONDAY, 2, "Теория вероятностей и мат. статистика", LessonKind.PRACTICE, "2-306", "Орлова М.П."),
            Slot(DayOfWeek.WEDNESDAY, 3, "Операционные системы", LessonKind.LAB, "3-105", "Кузнецов Д.И."),
            Slot(DayOfWeek.WEDNESDAY, 4, "Операционные системы", LessonKind.LAB, "3-105", "Кузнецов Д.И."),
            Slot(DayOfWeek.THURSDAY, 3, "Математический анализ", LessonKind.PRACTICE, "2-410", "Волков Н.С."),
        ),
        StudyGroup.G204 to listOf(
            Slot(DayOfWeek.MONDAY, 3, "Теория вероятностей и мат. статистика", LessonKind.PRACTICE, "2-308", "Орлова М.П."),
            Slot(DayOfWeek.TUESDAY, 4, "Операционные системы", LessonKind.LAB, "3-106", "Белов К.Р."),
            Slot(DayOfWeek.TUESDAY, 5, "Операционные системы", LessonKind.LAB, "3-106", "Белов К.Р."),
            Slot(DayOfWeek.FRIDAY, 2, "Математический анализ", LessonKind.PRACTICE, "2-411", "Волков Н.С."),
        ),
    )

    fun lessons(group: StudyGroup, from: LocalDate, to: LocalDate): List<Lesson> {
        val slots = common + byGroup.getValue(group)
        val result = mutableListOf<Lesson>()
        var date = from
        while (!date.isAfter(to)) {
            slots.filter { it.day == date.dayOfWeek }.forEach { slot ->
                val (start, end) = pairTimes[slot.pair - 1]
                result += Lesson("example-${group.code}-$date-${slot.pair}", group, date, start, end, slot.title, slot.room, slot.teacher, slot.kind)
            }
            date = date.plusDays(1)
        }
        return result.sortedWith(compareBy<Lesson>({ it.date }, { it.start }))
    }

    private val ACCESS_MATRIX = """
        # Матрица прав

        | Действие | Капитан | Участник |
        |---|---|---|
        | Создать, изменить, удалить задачу | да | нет |
        | Взять свободную задачу | да | да |
        | Взять задачу, назначенную другому | только через «Назначить на себя» | нет |
        | Сдать на проверку, прикрепить результат | только свою | только свою |
        | Принять или вернуть на доработку | да, с замечаниями | нет |
        | Настроить колонки и проекты | да | нет |
        | Создать, заблокировать, удалить аккаунт, сбросить пароль | да | нет |
    """.trimIndent()

    private val QA_REPORT = """
        # QA_REPORT (мок)

        Повторная проверка 31.10.

        - QA-01 — взятие чужой задачи: исправлено, проверено под участником и капитаном.
        - QA-02 — сдача чужого решения: исправлено.
        - QA-03 — исходники сохраняются при смене статуса: исправлено.
        - QA-04 — скачивание файлов: требует файлового API.
    """.trimIndent()

    private val PROTOCOL = """
        Протокол демонстрации спринта 3 (мок)

        1. Мобильное приложение: строгий Material 3, плавающая нижняя панель, парсер ИСУ.
        2. Веб: убрать артефакты ИИ, объединить участников и управление доступом.
        3. Backend: Swagger и README к следующему созвону.
    """.trimIndent()
}
