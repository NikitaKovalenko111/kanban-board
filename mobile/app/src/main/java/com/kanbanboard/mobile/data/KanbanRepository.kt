package com.kanbanboard.mobile.data

import com.kanbanboard.mobile.model.Attachment
import com.kanbanboard.mobile.model.Comment
import com.kanbanboard.mobile.model.EventVisibility
import com.kanbanboard.mobile.model.Lesson
import com.kanbanboard.mobile.model.LessonKind
import com.kanbanboard.mobile.model.LoginCredentials
import com.kanbanboard.mobile.model.Task
import com.kanbanboard.mobile.model.TaskHistoryEntry
import com.kanbanboard.mobile.model.TaskStatus
import com.kanbanboard.mobile.model.TeamEvent
import com.kanbanboard.mobile.model.User
import com.kanbanboard.mobile.model.UserRole
import com.kanbanboard.mobile.model.TeamSettings
import com.kanbanboard.mobile.model.Team
import com.kanbanboard.mobile.model.Project
import java.time.LocalDate

interface KanbanRepository {
    fun login(credentials: LoginCredentials): User?
    fun tasks(): List<Task>
    fun members(): List<User>
    fun teamSettings(): TeamSettings
    fun teams(): List<Team>
    fun projects(): List<Project>
    fun lessons(group: String): List<Lesson>
    fun events(): List<TeamEvent>
}

class FakeKanbanRepository : KanbanRepository {
    private data class Account(val user: User, val password: String)

    private val accounts = listOf(
        Account(User(1, "Алексей Морозов", "alexey", UserRole.LEAD, "Тимлид", true, "2026-04-12"), "lead123"),
        Account(User(2, "Мария Волкова", "maria", UserRole.MEMBER, "Дизайнер", true, "2026-05-08"), "member123"),
        Account(User(3, "Илья Горин", "ilya", UserRole.MEMBER, "Frontend-разработчик", true, "2026-06-03"), "member123"),
        Account(User(4, "Анна Лукина", "anna", UserRole.MEMBER, "QA-инженер", true, "2026-07-21"), "member123"),
        Account(User(5, "Олег Миронов", "oleg", UserRole.MEMBER, "Backend-разработчик", false, "2026-08-15"), "member123"),
        Account(User(6, "Самир Ахмедов", "samir", UserRole.MEMBER, "Android-разработчик", true, "2026-09-01"), "member123"),
        Account(User(7, "Денис", "denis", UserRole.MEMBER, "Backend-разработчик", true, "2026-09-01"), "member123"),
        Account(User(8, "Никита", "nikita", UserRole.LEAD, "Тимлид", true, "2026-09-01"), "member123"),
        Account(User(9, "Алёна", "alena", UserRole.MEMBER, "DevOps", true, "2026-09-01"), "member123"),
    )

    override fun login(credentials: LoginCredentials): User? = accounts.firstOrNull {
        it.user.login == credentials.login.trim().lowercase() &&
            it.password == credentials.password &&
            it.user.active
    }?.user

    override fun members(): List<User> = accounts.map { it.user }

    override fun teamSettings() = TeamSettings(
        name = "Команда Kanban",
        description = "Студенческая команда проекта Kanban Board.",
    )

    override fun teams(): List<Team> = listOf(
        Team(1, "Команда Kanban", "Разработка Kanban Board для студенческой команды.", setOf(1, 2, 3, 4, 5, 6, 7, 8, 9)),
        Team(2, "Мобильная лаборатория", "Android, Wear OS и интеграция с расписанием.", setOf(1, 2, 4, 6)),
        Team(3, "Исследования", "Учебные проекты по данным и автоматизации.", setOf(3, 5)),
    )

    override fun projects(): List<Project> = listOf(
        Project(1, 1, "Kanban Board", "Основное приложение команды.", setOf(1, 2, 3, 4, 5, 6, 7, 8, 9)),
        Project(2, 1, "Хакатон", "Подготовка и демонстрация проекта.", setOf(1, 2, 6)),
        Project(3, 2, "Мобильная разработка", "Android и интеграция с API.", setOf(1, 2, 4, 6)),
        Project(4, 2, "Лабораторные", "Учебные задания.", setOf(1, 4)),
        Project(5, 3, "Исследования", "Данные и автоматизация.", setOf(3, 5)),
    )

    override fun tasks(): List<Task> = listOf(
        Task(
            id = 101,
            title = "Шаблон мобильного приложения",
            description = "Подготовить Android code base и основные экраны из Vision проекта.",
            status = TaskStatus.WORKING,
            assignee = "Самир Ахмедов",
            deadline = LocalDate.now().plusDays(2).toString(),
            attachments = listOf(Attachment("a1", "mobile-requirements.pdf", "840 КБ")),
            comments = listOf(Comment("c1", "Никита", "Сохраняем единый стиль с веб-версией.", "Сегодня, 10:20")),
            assigneeId = 6,
            history = listOf(
                TaskHistoryEntry("h101-1", "Создана", 8, "Никита", "2026-10-20T10:00:00+03:00"),
                TaskHistoryEntry("h101-2", "Взята в работу", 6, "Самир Ахмедов", "2026-10-21T11:30:00+03:00"),
            ),
        ),
        Task(
            id = 102,
            title = "Спроектировать UI-kit",
            description = "Перенести цвета, скругления, карточки и состояния задач из веб-приложения.",
            status = TaskStatus.WAITING,
            assignee = "Мария Волкова",
            deadline = LocalDate.now().plusDays(3).toString(),
        ),
        Task(
            id = 103,
            title = "Проверить архитектуру API",
            description = "Согласовать модели задач, пользователей и файлов с backend-командой.",
            status = TaskStatus.CHECKING,
            assignee = "Денис",
            deadline = LocalDate.now().plusDays(4).toString(),
        ),
        Task(
            id = 104,
            title = "Vision проекта",
            description = "Зафиксировать основной сценарий работы команды с Kanban-доской.",
            status = TaskStatus.DONE,
            assignee = "Никита",
            deadline = "12 окт",
            attachments = listOf(Attachment("a2", "vision.md", "18 КБ")),
            resultAttachments = listOf(Attachment("r1", "vision-result.pdf", "430 КБ")),
            completedAt = "12.10.2026",
        ),
        Task(
            id = 105,
            title = "Настроить CI",
            description = "Добавить сборку и автоматические проверки проекта.",
            status = TaskStatus.DONE,
            assignee = "Алёна",
            deadline = "14 окт",
            resultAttachments = listOf(Attachment("r2", "ci-pipeline.yml", "12 КБ")),
            completedAt = "14.10.2026",
        ),
        Task(106, "Подготовить документацию", "Актуализировать инструкции по локальному запуску.", TaskStatus.DONE, "Анна Лукина", "13 окт", completedAt = "13.10.2026"),
        Task(107, "Собрать UI-kit", "Согласовать состояния кнопок, полей и типографику.", TaskStatus.DONE, "Мария Волкова", "11 окт", completedAt = "11.10.2026"),
        Task(108, "Экран расписания", "Подготовить мобильное отображение расписания групп.", TaskStatus.WORKING, "Мария Волкова", LocalDate.now().plusDays(2).toString(), teamId = 2, projectId = 3),
        Task(109, "Подготовить демо хакатона", "Собрать сценарий демонстрации.", TaskStatus.WAITING, "Мария Волкова", LocalDate.now().plusDays(5).toString(), projectId = 2),
        Task(110, "Проверить мобильный сценарий", "Свободная задача для участника команды.", TaskStatus.WAITING, "Не назначен", LocalDate.now().plusDays(3).toString()),
    )

    override fun lessons(group: String): List<Lesson> = if (group == "203") {
        listOf(
            Lesson(LocalDate.now().toString(), "09:50–11:20", "Проектная деятельность", "ауд. 312", "А. В. Иванов", LessonKind.PRACTICE),
            Lesson(LocalDate.now().toString(), "11:40–13:10", "Разработка мобильных приложений", "ауд. 405", "Е. С. Орлова", LessonKind.LAB),
            Lesson(LocalDate.now().toString(), "13:30–15:00", "Базы данных", "ауд. 208", "Д. П. Волков", LessonKind.LECTURE),
        )
    } else {
        listOf(
            Lesson(LocalDate.now().toString(), "08:10–09:40", "Архитектура ПО", "ауд. 211", "М. И. Семёнов", LessonKind.LECTURE),
            Lesson(LocalDate.now().toString(), "09:50–11:20", "Тестирование систем", "ауд. 118", "О. А. Белова", LessonKind.LAB),
            Lesson(LocalDate.now().toString(), "11:40–13:10", "Проектная деятельность", "ауд. 312", "А. В. Иванов", LessonKind.PRACTICE),
        )
    }

    override fun events(): List<TeamEvent> = listOf(
        TeamEvent(1, "Созвон команды", LocalDate.now().plusDays(1).toString(), "18:00", EventVisibility.TEAM, endTime = "19:00", description = "Обсуждение задач и результатов спринта.", reminderMinutes = setOf(60)),
        TeamEvent(2, "Подготовка к лабораторной", LocalDate.now().plusDays(1).toString(), "11:40", EventVisibility.GROUP, endTime = "13:10", description = "Разобрать материалы к занятию.", group = "203", typeId = "study", reminderMinutes = setOf(1440)),
        TeamEvent(3, "Проверить pull request", LocalDate.now().toString(), "20:00", EventVisibility.PERSONAL, endTime = "20:30", description = "Просмотреть замечания и проверить сборку.", typeId = "personal", reminderMinutes = setOf(30), ownerId = 1),
    )
}
