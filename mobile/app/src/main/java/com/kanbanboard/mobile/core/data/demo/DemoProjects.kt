package com.kanbanboard.mobile.core.data.demo

import com.kanbanboard.mobile.core.model.AppError
import com.kanbanboard.mobile.core.model.BoardColumn
import com.kanbanboard.mobile.core.model.ColumnId
import com.kanbanboard.mobile.core.model.ColumnKind
import com.kanbanboard.mobile.core.model.Project
import com.kanbanboard.mobile.core.model.ProjectDraft
import com.kanbanboard.mobile.core.model.ProjectId
import com.kanbanboard.mobile.core.model.ProjectSource
import com.kanbanboard.mobile.core.model.Session
import com.kanbanboard.mobile.core.model.SourceKind
import com.kanbanboard.mobile.core.policy.ProjectPolicy

/** Операции «сервера» над проектами и их источниками. */

private fun DemoState.validateProject(draft: ProjectDraft, selfId: ProjectId?) {
    val name = draft.name.trim()
    if (name.isEmpty()) throw AppError.Validation("Введите название проекта")
    if (name.length > 80) throw AppError.Validation("Название длиннее 80 символов")
    if (projects.any { it.id != selfId && it.name.equals(name, ignoreCase = true) }) {
        throw AppError.Validation("Проект «$name» уже есть")
    }
    val known = accounts.map { it.member.user.id }.toSet()
    if (draft.members.keys.any { it !in known }) throw AppError.Validation("В составе есть неизвестный участник")
    if (draft.members.values.any { it.length > 60 }) throw AppError.Validation("Роль длиннее 60 символов")
}

internal suspend fun DemoBackend.createProject(actor: Session, draft: ProjectDraft): Project = mutate { s ->
    if (!ProjectPolicy.canManage(actor)) throw AppError.Forbidden("Создавать проекты может капитан")
    s.validateProject(draft, null)
    val id = ProjectId(newId())
    val project = Project(id, draft.name.trim(), draft.description.trim(), draft.members.mapValues { it.value.trim() })
    // Новая доска сразу получает стандартный набор колонок; капитан может его поменять.
    val columns = DemoSeed.STANDARD_COLUMNS.mapIndexed { index, (title, color) ->
        BoardColumn(ColumnId(newId()), id, title, index, ColumnKind.forPosition(index, DemoSeed.STANDARD_COLUMNS.size), color)
    }
    s.copy(projects = s.projects + project, columns = s.columns + columns) to project
}

internal suspend fun DemoBackend.updateProject(actor: Session, id: ProjectId, draft: ProjectDraft) = mutate { s ->
    if (!ProjectPolicy.canManage(actor)) throw AppError.Forbidden("Настраивать проект может капитан")
    val current = s.project(id)
    s.validateProject(draft, id)
    // Нельзя исключить человека, у которого в проекте остались незакрытые задачи: они потеряют исполнителя.
    val doneColumns = s.columns.filter { it.projectId == id && it.kind == ColumnKind.DONE }.map { it.id }.toSet()
    val removed = current.members.keys - draft.members.keys
    val blocked = removed.firstOrNull { userId ->
        s.tasks.any { it.projectId == id && it.assigneeId == userId && it.columnId !in doneColumns }
    }
    if (blocked != null) {
        val name = s.accounts.firstOrNull { it.member.user.id == blocked }?.member?.user?.fullName ?: "участника"
        throw AppError.Validation("У $name есть незакрытые задачи в проекте — сначала переназначьте их")
    }
    val updated = current.copy(
        name = draft.name.trim(),
        description = draft.description.trim(),
        members = draft.members.mapValues { it.value.trim() },
    )
    s.copy(projects = s.projects.map { if (it.id == id) updated else it }) to Unit
}

/**
 * Удаление проекта капитаном вместе с доской, задачами и источниками.
 * Возвращает ссылки на файлы проекта и его задач, чтобы удалить их с устройства.
 */
internal suspend fun DemoBackend.deleteProject(actor: Session, id: ProjectId): List<String> = mutate { s ->
    if (!ProjectPolicy.canManage(actor)) throw AppError.Forbidden("Удалять проекты может капитан")
    val project = s.project(id)
    val tasks = s.tasks.filter { it.projectId == id }
    val files = project.sources.mapNotNull { it.fileRef } + tasks.flatMap { task -> task.attachments.mapNotNull { it.fileRef } }
    val taskIds = tasks.map { it.id }.toSet()
    s.copy(
        projects = s.projects.filterNot { it.id == id },
        columns = s.columns.filterNot { it.projectId == id },
        tasks = s.tasks.filterNot { it.projectId == id },
        notifications = s.notifications.mapValues { (_, list) -> list.filterNot { it.taskId in taskIds } },
    ) to files
}

private fun DemoState.replaceProject(project: Project): DemoState =
    copy(projects = projects.map { if (it.id == project.id) project else it })

internal suspend fun DemoBackend.addSource(actor: Session, id: ProjectId, source: ProjectSource) = mutate { s ->
    val project = s.project(id)
    if (!ProjectPolicy.canAddSource(actor, project)) {
        throw AppError.Forbidden("Добавлять источники могут участники проекта")
    }
    if (source.title.isBlank()) throw AppError.Validation("Укажите название")
    if (source.kind == SourceKind.LINK && !ProjectPolicy.isValidUrl(source.url.orEmpty())) {
        throw AppError.Validation("Ссылка должна начинаться с https://")
    }
    if (source.kind == SourceKind.FILE && (source.sizeBytes ?: 0) > DemoBackend.MAX_FILE_BYTES) {
        throw AppError.Validation("Файл больше 10 МБ")
    }
    s.replaceProject(project.copy(sources = project.sources + source)) to Unit
}

internal suspend fun DemoBackend.removeSource(actor: Session, id: ProjectId, sourceId: String) = mutate { s ->
    val project = s.project(id)
    val source = project.sources.firstOrNull { it.id == sourceId } ?: throw AppError.NotFound("Источник не найден")
    if (!ProjectPolicy.canRemoveSource(actor, source)) {
        throw AppError.Forbidden("Удалить источник может его автор или капитан")
    }
    s.replaceProject(project.copy(sources = project.sources.filterNot { it.id == sourceId })) to source.fileRef
}

/** Новый источник с серверными полями (id, автор, время). */
internal fun DemoBackend.newSource(
    actor: Session,
    kind: SourceKind,
    title: String,
    url: String?,
    sizeBytes: Long?,
    fileRef: String?,
): ProjectSource = ProjectSource(newId(), kind, title.trim(), url?.trim(), sizeBytes, actor.user.id, clock.instant(), fileRef)
