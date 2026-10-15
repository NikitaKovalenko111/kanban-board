package com.kanbanboard.mobile.data

import com.kanbanboard.mobile.model.Attachment
import com.kanbanboard.mobile.model.Comment
import com.kanbanboard.mobile.model.EventVisibility
import com.kanbanboard.mobile.model.Lesson
import com.kanbanboard.mobile.model.LoginCredentials
import com.kanbanboard.mobile.model.Task
import com.kanbanboard.mobile.model.TaskStatus
import com.kanbanboard.mobile.model.TeamEvent
import com.kanbanboard.mobile.model.User
import com.kanbanboard.mobile.model.UserRole
import com.kanbanboard.mobile.model.TeamSettings

interface KanbanRepository {
    fun login(credentials: LoginCredentials): User?
    fun tasks(): List<Task>
    fun members(): List<User>
    fun teamSettings(): TeamSettings
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
    )

    override fun login(credentials: LoginCredentials): User? = accounts.firstOrNull {
        it.user.login == credentials.login.trim().lowercase() &&
            it.password == credentials.password &&
            it.user.active
    }?.user

    override fun members(): List<User> = accounts.map { it.user }

    override fun teamSettings() = TeamSettings(
        name = "Веб-продукт",
        description = "Команда разработки внутренних и клиентских веб-продуктов.",
    )

    override fun tasks(): List<Task> = listOf(
        Task(
            id = 101,
            title = "Шаблон мобильного приложения",
            description = "Подготовить Android code base и основные экраны из Vision проекта.",
            status = TaskStatus.WORKING,
            assignee = "Самир Ахмедов",
            deadline = "16 окт",
            attachments = listOf(Attachment("a1", "mobile-requirements.pdf", "840 КБ")),
            comments = listOf(Comment("c1", "Никита", "Сохраняем единый стиль с веб-версией.", "Сегодня, 10:20")),
        ),
        Task(
            id = 102,
            title = "Спроектировать UI-kit",
            description = "Перенести цвета, скругления, карточки и состояния задач из веб-приложения.",
            status = TaskStatus.WAITING,
            assignee = "Самир Ахмедов",
            deadline = "16 окт",
        ),
        Task(
            id = 103,
            title = "Проверить архитектуру API",
            description = "Согласовать модели задач, пользователей и файлов с backend-командой.",
            status = TaskStatus.CHECKING,
            assignee = "Денис",
            deadline = "17 окт",
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
    )

    override fun lessons(group: String): List<Lesson> = if (group == "203") {
        listOf(
            Lesson("09:50–11:20", "Проектная деятельность", "ауд. 312", "А. В. Иванов"),
            Lesson("11:40–13:10", "Разработка мобильных приложений", "ауд. 405", "Е. С. Орлова"),
            Lesson("13:30–15:00", "Базы данных", "ауд. 208", "Д. П. Волков"),
        )
    } else {
        listOf(
            Lesson("08:10–09:40", "Архитектура ПО", "ауд. 211", "М. И. Семёнов"),
            Lesson("09:50–11:20", "Тестирование систем", "ауд. 118", "О. А. Белова"),
            Lesson("11:40–13:10", "Проектная деятельность", "ауд. 312", "А. В. Иванов"),
        )
    }

    override fun events(): List<TeamEvent> = listOf(
        TeamEvent(1, "Встреча по итогам Sprint 0", "16 октября", "18:00", EventVisibility.TEAM, "За 1 день и 30 минут"),
        TeamEvent(2, "Лабораторная по мобильной разработке", "18 октября", "11:40", EventVisibility.GROUP, "За 1 день"),
        TeamEvent(3, "Проверить pull request", "Сегодня", "20:00", EventVisibility.PERSONAL, "За 1 час"),
    )
}
