package com.kanbanboard.mobile.core.data.demo

import com.kanbanboard.mobile.core.model.StudyGroup
import com.kanbanboard.mobile.core.model.Team
import com.kanbanboard.mobile.core.model.TeamMember
import com.kanbanboard.mobile.core.model.TeamRole
import com.kanbanboard.mobile.core.model.User
import com.kanbanboard.mobile.core.model.UserId
import java.time.Clock
import java.time.LocalDate

/**
 * Начальное состояние демо-сервера: команда и два аккаунта — тимлид и участник.
 * Проекты, задачи, события и остальных участников создают сами пользователи
 * (тимлид — проекты и аккаунты). Пароли — в README, на экране входа их нет.
 */
object DemoSeed {
    /** Колонки новой доски: название и цвет. Роль колонки задаётся её позицией. */
    val STANDARD_COLUMNS: List<Pair<String, Long>> = listOf(
        "Ожидает" to 0xFF8A94A6,
        "В работе" to 0xFF6576DF,
        "На проверке" to 0xFFDA8A37,
        "Выполнено" to 0xFF58A779,
    )

    val LEAD_ID = UserId("u-nikita")
    val MEMBER_ID = UserId("u-samir")
    const val LEAD_LOGIN = "nikita"
    const val MEMBER_LOGIN = "samir"
    const val LEAD_PASSWORD = "lead12345"
    const val MEMBER_PASSWORD = "member12345"

    fun create(clock: Clock): DemoState {
        val today = LocalDate.now(clock)
        fun account(id: UserId, name: String, login: String, group: StudyGroup, role: TeamRole, password: String) =
            DemoAccount(TeamMember(User(id, name, login, group, null, true, today), role), password)

        return DemoState(
            team = Team("Код в сапоге", "Студенческая команда: задачи, проекты и расписание в одном месте."),
            accounts = listOf(
                account(LEAD_ID, "Никита Коваленко", LEAD_LOGIN, StudyGroup.G204, TeamRole.CAPTAIN, LEAD_PASSWORD),
                account(MEMBER_ID, "Самир Миргазямов", MEMBER_LOGIN, StudyGroup.G203, TeamRole.MEMBER, MEMBER_PASSWORD),
            ),
            projects = emptyList(),
            columns = emptyList(),
            tasks = emptyList(),
            events = emptyList(),
            eventTypes = DemoSchedule.builtInTypes,
        )
    }
}
