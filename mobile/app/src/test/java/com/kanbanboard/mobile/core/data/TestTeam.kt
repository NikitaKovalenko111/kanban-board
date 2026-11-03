package com.kanbanboard.mobile.core.data

import com.kanbanboard.mobile.core.data.demo.DemoBackend
import com.kanbanboard.mobile.core.data.demo.DemoSeed
import com.kanbanboard.mobile.core.data.demo.createProject
import com.kanbanboard.mobile.core.model.AppError
import com.kanbanboard.mobile.core.model.BoardColumn
import com.kanbanboard.mobile.core.model.ColumnKind
import com.kanbanboard.mobile.core.model.NewAccount
import com.kanbanboard.mobile.core.model.Project
import com.kanbanboard.mobile.core.model.ProjectDraft
import com.kanbanboard.mobile.core.model.Session
import com.kanbanboard.mobile.core.model.StudyGroup
import com.kanbanboard.mobile.core.model.Task
import com.kanbanboard.mobile.core.model.TaskDraft
import com.kanbanboard.mobile.core.model.TaskPriority
import com.kanbanboard.mobile.core.model.TeamRole
import org.junit.Assert.assertTrue
import org.junit.Assert.fail

/**
 * Тестовая команда поверх чистого демо-сервера: в seed только тимлид и участник,
 * всё остальное (второй участник, проект, задачи) тесты создают сами — как пользователи в приложении.
 */
class TestTeam private constructor(
    val backend: DemoBackend,
    val lead: Session,
    val samir: Session,
    val maria: Session,
    val project: Project,
) {
    fun columns(): List<BoardColumn> = backend.state.value.columnsOf(project.id)
    fun column(kind: ColumnKind): BoardColumn = columns().first { it.kind == kind }
    fun task(task: Task): Task = backend.state.value.task(task.id)

    suspend fun newTask(title: String = "Задача", assignee: Session? = null): Task =
        backend.createTask(lead, project.id, TaskDraft(title, "", TaskPriority.NORMAL, assignee?.user?.id, null))

    companion object {
        suspend fun create(): TestTeam {
            val backend = DemoBackend(latencyMs = 0)
            val lead = backend.login(DemoSeed.LEAD_LOGIN, DemoSeed.LEAD_PASSWORD)
            val samir = backend.login(DemoSeed.MEMBER_LOGIN, DemoSeed.MEMBER_PASSWORD)
            backend.createAccount(lead, NewAccount("Мария Волкова", "maria", "maria12345", StudyGroup.G204, TeamRole.MEMBER))
            val maria = backend.login("maria", "maria12345")
            val project = backend.createProject(
                lead,
                ProjectDraft(
                    "Kanban Board", "",
                    mapOf(lead.user.id to "Тимлид", samir.user.id to "Android", maria.user.id to "Дизайн"),
                ),
            )
            return TestTeam(backend, lead, samir, maria, project)
        }
    }
}

suspend fun expectError(type: Class<out AppError>, block: suspend () -> Unit) {
    try {
        block()
        fail("Ожидалась ${type.simpleName}")
    } catch (e: AppError) {
        assertTrue("Получена ${e::class.simpleName}: ${e.message}", type.isInstance(e))
    }
}
