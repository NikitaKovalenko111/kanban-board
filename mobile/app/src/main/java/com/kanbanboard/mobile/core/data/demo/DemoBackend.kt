@file:UseSerializers(InstantSerializer::class, LocalDateSerializer::class)

package com.kanbanboard.mobile.core.data.demo

import com.kanbanboard.mobile.core.model.AppError
import com.kanbanboard.mobile.core.model.EventVisibility
import com.kanbanboard.mobile.core.model.InstantSerializer
import com.kanbanboard.mobile.core.model.LocalDateSerializer
import com.kanbanboard.mobile.core.model.AppNotification
import com.kanbanboard.mobile.core.model.NotificationKind
import com.kanbanboard.mobile.core.model.PickedFile
import com.kanbanboard.mobile.core.model.SchedulePrivacy
import com.kanbanboard.mobile.core.model.TeamRole
import com.kanbanboard.mobile.core.model.Attachment
import com.kanbanboard.mobile.core.model.AttachmentKind
import com.kanbanboard.mobile.core.model.BoardColumn
import com.kanbanboard.mobile.core.model.ColumnDraft
import com.kanbanboard.mobile.core.model.ColumnId
import com.kanbanboard.mobile.core.model.ColumnKind
import com.kanbanboard.mobile.core.model.NewAccount
import com.kanbanboard.mobile.core.model.EventType
import com.kanbanboard.mobile.core.model.Project
import com.kanbanboard.mobile.core.model.ScheduleEvent
import com.kanbanboard.mobile.core.model.ProjectId
import com.kanbanboard.mobile.core.model.Session
import com.kanbanboard.mobile.core.model.StudyGroup
import com.kanbanboard.mobile.core.model.Task
import com.kanbanboard.mobile.core.model.TaskAction
import com.kanbanboard.mobile.core.model.TaskComment
import com.kanbanboard.mobile.core.model.TaskDraft
import com.kanbanboard.mobile.core.model.TaskHistoryEntry
import com.kanbanboard.mobile.core.model.TaskId
import com.kanbanboard.mobile.core.model.Team
import com.kanbanboard.mobile.core.model.TeamMember
import com.kanbanboard.mobile.core.model.User
import com.kanbanboard.mobile.core.model.UserId
import com.kanbanboard.mobile.core.policy.TaskPolicy
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
data class DemoAccount(val member: TeamMember, val password: String)

/** Всё состояние демо-сервера. Сериализуется в JSON и сохраняется на устройстве между запусками. */
@Serializable
data class DemoState(
    val team: Team,
    val accounts: List<DemoAccount>,
    val projects: List<Project>,
    val columns: List<BoardColumn>,
    val tasks: List<Task>,
    /** События хранятся без маскировки; маскирует репозиторий при выдаче конкретному зрителю. */
    val events: List<ScheduleEvent> = emptyList(),
    val eventTypes: List<EventType> = emptyList(),
    /** Ключ «userId|itemKey» → минуты до начала. */
    val reminders: Map<String, Set<Int>> = emptyMap(),
    /** Уведомления о задачах по получателям. */
    val notifications: Map<UserId, List<AppNotification>> = emptyMap(),
    /** Какие уведомления «скоро событие» пользователь уже видел (они вычисляются на лету). */
    val seenEventNotifications: Map<UserId, Set<String>> = emptyMap(),
) {
    fun user(id: UserId): User? = accounts.firstOrNull { it.member.user.id == id }?.member?.user

    fun groupOf(userId: UserId): StudyGroup? =
        accounts.firstOrNull { it.member.user.id == userId }?.member?.user?.studyGroup

    fun sessionFor(userId: UserId): Session? = accounts
        .firstOrNull { it.member.user.id == userId && it.member.user.isActive }
        ?.let { Session(it.member.user, it.member.role) }

    fun columnsOf(projectId: ProjectId): List<BoardColumn> =
        columns.filter { it.projectId == projectId }.sortedBy { it.position }

    fun task(id: TaskId): Task = tasks.firstOrNull { it.id == id } ?: throw AppError.NotFound("Задача не найдена")

    fun project(id: ProjectId): Project =
        projects.firstOrNull { it.id == id } ?: throw AppError.NotFound("Проект не найден")
}

/**
 * Имитация сервера в памяти процесса. Проверяет права так же, как должен проверять backend,
 * поэтому демо-режим ведёт себя честно: кнопка не спасает от запрета.
 * Данные сбрасываются при перезапуске приложения.
 */
class DemoBackend(
    internal val clock: Clock = Clock.systemDefaultZone(),
    private val latencyMs: Long = 200,
    initial: DemoState = DemoSeed.create(clock),
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<DemoState> = _state.asStateFlow()

    private fun requireCaptain(actor: Session, message: String) {
        if (!TaskPolicy.canCreateAccounts(actor)) throw AppError.Forbidden(message)
    }

    private fun DemoState.doneColumnIds(): Set<ColumnId> =
        columns.filter { it.kind == ColumnKind.DONE }.map { it.id }.toSet()

    internal suspend fun <T> mutate(block: (DemoState) -> Pair<DemoState, T>): T = mutex.withLock {
        delay(latencyMs)
        val (next, result) = block(_state.value)
        _state.value = next
        result
    }

    internal fun newId(): String = UUID.randomUUID().toString()

    private fun entry(
        action: TaskAction,
        actor: Session,
        at: Instant,
        from: String? = null,
        to: String? = null,
        comment: String? = null,
    ) = TaskHistoryEntry(newId(), action, actor.user.id, at, from, to, comment)

    private fun DemoState.replaceTask(task: Task): DemoState =
        copy(tasks = tasks.map { if (it.id == task.id) task else it })

    private fun DemoState.nextPosition(columnId: ColumnId): Int =
        (tasks.filter { it.columnId == columnId }.maxOfOrNull { it.position } ?: -1) + 1

    private fun DemoState.columnOf(task: Task): BoardColumn =
        columns.firstOrNull { it.id == task.columnId } ?: throw AppError.NotFound("Колонка не найдена")

    private fun DemoState.userName(id: UserId): String =
        accounts.firstOrNull { it.member.user.id == id }?.member?.user?.fullName ?: "Неизвестный"

    private fun DemoState.updateUser(id: UserId, transform: (User) -> User): DemoState = copy(
        accounts = accounts.map { account ->
            if (account.member.user.id == id) account.copy(member = account.member.copy(user = transform(account.member.user)))
            else account
        },
    )

    /** Добавляет уведомление каждому получателю, кроме автора действия. */
    private fun DemoState.notify(
        recipients: Collection<UserId?>,
        actor: Session,
        kind: NotificationKind,
        title: String,
        text: String,
        taskId: TaskId,
    ): DemoState {
        val now = clock.instant()
        var result = notifications
        recipients.filterNotNull().toSet().filter { it != actor.user.id }.forEach { userId ->
            val item = AppNotification(newId(), kind, title, text, now, read = false, taskId = taskId)
            result = result + (userId to (listOf(item) + result[userId].orEmpty()).take(MAX_NOTIFICATIONS))
        }
        return copy(notifications = result)
    }

    private fun DemoState.captains(): List<UserId> =
        accounts.filter { it.member.role == TeamRole.CAPTAIN && it.member.user.isActive }.map { it.member.user.id }

    // ---------- Авторизация и команда ----------

    suspend fun login(login: String, password: String): Session = mutate { s ->
        val account = s.accounts.firstOrNull {
            it.member.user.login.equals(login.trim(), ignoreCase = true) && it.password == password
        } ?: throw AppError.InvalidCredentials()
        if (!account.member.user.isActive) throw AppError.Forbidden("Доступ к аккаунту закрыт")
        s to Session(account.member.user, account.member.role)
    }

    suspend fun updateStudyGroup(actor: Session, group: StudyGroup) = mutate { s ->
        s.updateUser(actor.user.id) { it.copy(studyGroup = group) } to Unit
    }

    suspend fun updateSchedulePrivacy(actor: Session, privacy: SchedulePrivacy) = mutate { s ->
        s.updateUser(actor.user.id) { it.copy(schedulePrivacy = privacy) } to Unit
    }

    suspend fun updatePhoto(actor: Session, photo: String?) = mutate { s ->
        s.updateUser(actor.user.id) { it.copy(photo = photo) } to Unit
    }

    suspend fun setMemberActive(actor: Session, userId: UserId, active: Boolean) = mutate { s ->
        if (!TaskPolicy.canCreateAccounts(actor)) throw AppError.Forbidden("Управлять доступом может только капитан")
        if (userId == actor.user.id) throw AppError.Validation("Нельзя заблокировать самого себя")
        s.user(userId) ?: throw AppError.NotFound("Участник не найден")
        s.updateUser(userId) { it.copy(isActive = active) } to Unit
    }

    /** Новый временный пароль: 10 символов без похожих букв и цифр (0/O, 1/l). */
    suspend fun resetPassword(actor: Session, userId: UserId): String = mutate { s ->
        if (!TaskPolicy.canCreateAccounts(actor)) throw AppError.Forbidden("Сбрасывать пароли может только капитан")
        s.user(userId) ?: throw AppError.NotFound("Участник не найден")
        val alphabet = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = java.security.SecureRandom()
        val password = (1..10).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        s.copy(accounts = s.accounts.map { if (it.member.user.id == userId) it.copy(password = password) else it }) to password
    }

    /** Смена своего пароля: нужен текущий пароль (в том числе временный, выданный капитаном). */
    suspend fun changePassword(actor: Session, current: String, new: String) = mutate { s ->
        val account = s.accounts.firstOrNull { it.member.user.id == actor.user.id }
            ?: throw AppError.NotFound("Аккаунт не найден")
        if (account.password != current) throw AppError.Validation("Текущий пароль указан неверно")
        if (new.length < 8) throw AppError.Validation("Новый пароль не короче 8 символов")
        if (new == current) throw AppError.Validation("Новый пароль совпадает с текущим")
        s.copy(accounts = s.accounts.map { if (it === account) it.copy(password = new) else it }) to Unit
    }

    /** Капитан назначает другого участника капитаном или снимает с него эту роль. */
    suspend fun setMemberRole(actor: Session, userId: UserId, role: TeamRole) = mutate { s ->
        requireCaptain(actor, "Менять роли может только капитан")
        if (userId == actor.user.id) throw AppError.Validation("Свою роль поменять нельзя")
        s.user(userId) ?: throw AppError.NotFound("Участник не найден")
        s.copy(
            accounts = s.accounts.map { account ->
                if (account.member.user.id == userId) account.copy(member = account.member.copy(role = role)) else account
            },
        ) to Unit
    }

    /**
     * Удаление аккаунта капитаном. Нельзя удалить себя и человека с незакрытыми задачами —
     * их сначала нужно переназначить. Человек убирается из проектов; его личные события
     * и напоминания удаляются, командные и групповые события и свои типы переходят капитану.
     * Выполненные задачи, комментарии и журнал остаются.
     */
    suspend fun deleteAccount(actor: Session, userId: UserId) = mutate { s ->
        requireCaptain(actor, "Удалять аккаунты может только капитан")
        if (userId == actor.user.id) throw AppError.Validation("Нельзя удалить свой аккаунт")
        s.user(userId) ?: throw AppError.NotFound("Участник не найден")
        val done = s.doneColumnIds()
        val open = s.tasks.count { it.assigneeId == userId && it.columnId !in done }
        if (open > 0) {
            throw AppError.Validation("У участника незакрытых задач: $open — сначала переназначьте их")
        }
        val prefix = "${userId.value}|"
        s.copy(
            accounts = s.accounts.filterNot { it.member.user.id == userId },
            projects = s.projects.map { it.copy(members = it.members - userId) },
            events = s.events
                .filterNot { it.ownerId == userId && it.visibility == EventVisibility.PERSONAL }
                .map { if (it.ownerId == userId) it.copy(ownerId = actor.user.id) else it },
            eventTypes = s.eventTypes.map { if (it.ownerId == userId) it.copy(ownerId = actor.user.id) else it },
            reminders = s.reminders.filterKeys { !it.startsWith(prefix) },
            notifications = s.notifications - userId,
            seenEventNotifications = s.seenEventNotifications - userId,
        ) to Unit
    }

    suspend fun markNotificationsRead(actor: Session, eventKeys: Set<String>) = mutate { s ->
        val id = actor.user.id
        s.copy(
            notifications = s.notifications + (id to s.notifications[id].orEmpty().map { it.copy(read = true) }),
            seenEventNotifications = s.seenEventNotifications + (id to (s.seenEventNotifications[id].orEmpty() + eventKeys)),
        ) to Unit
    }

    suspend fun createAccount(actor: Session, account: NewAccount): TeamMember = mutate { s ->
        if (!TaskPolicy.canCreateAccounts(actor)) throw AppError.Forbidden("Создавать аккаунты может только капитан")
        val login = account.login.trim().lowercase()
        if (account.fullName.isBlank()) throw AppError.Validation("Укажите имя")
        if (!login.matches(Regex("[a-z0-9._-]{3,32}"))) {
            throw AppError.Validation("Логин: 3–32 символа, латиница, цифры, . _ -")
        }
        if (account.temporaryPassword.length < 8) throw AppError.Validation("Пароль не короче 8 символов")
        if (s.accounts.any { it.member.user.login == login }) throw AppError.Validation("Логин уже занят")
        val member = TeamMember(
            User(UserId(newId()), account.fullName.trim(), login, account.studyGroup, null, true, LocalDate.now(clock)),
            account.role,
        )
        s.copy(accounts = s.accounts + DemoAccount(member, account.temporaryPassword)) to member
    }

    // ---------- Задачи ----------

    private fun DemoState.validate(draft: TaskDraft, project: Project) {
        if (draft.title.isBlank()) throw AppError.Validation("Введите название задачи")
        if (draft.title.length > 200) throw AppError.Validation("Название длиннее 200 символов")
        val assignee = draft.assigneeId
        if (assignee != null && !project.isMember(assignee)) {
            throw AppError.Validation("${userName(assignee)} не состоит в проекте")
        }
    }

    suspend fun createTask(actor: Session, projectId: ProjectId, draft: TaskDraft): Task = mutate { s ->
        if (!TaskPolicy.canCreateTask(actor)) throw AppError.Forbidden("Создавать задачи может только капитан")
        val project = s.project(projectId)
        s.validate(draft, project)
        val columns = s.columnsOf(projectId)
        val column = draft.columnId?.let { id -> columns.firstOrNull { it.id == id } }
            ?: columns.firstOrNull { it.kind == ColumnKind.BACKLOG }
            ?: throw AppError.Validation("На доске нет колонки ожидания")
        val now = clock.instant()
        val history = buildList {
            add(entry(TaskAction.CREATED, actor, now, to = column.title))
            draft.assigneeId?.let { add(entry(TaskAction.ASSIGNED, actor, now, comment = s.userName(it))) }
        }
        val task = Task(
            id = TaskId(newId()),
            projectId = projectId,
            columnId = column.id,
            position = s.nextPosition(column.id),
            title = draft.title.trim(),
            description = draft.description.trim(),
            priority = draft.priority,
            assigneeId = draft.assigneeId,
            createdBy = actor.user.id,
            createdAt = now,
            dueAt = draft.dueAt,
            completedAt = if (column.kind == ColumnKind.DONE) now else null,
            attachments = emptyList(),
            comments = emptyList(),
            history = history,
        )
        s.copy(tasks = s.tasks + task) to task
    }

    suspend fun updateTask(actor: Session, taskId: TaskId, draft: TaskDraft): Task = mutate { s ->
        if (!TaskPolicy.canEditTask(actor)) throw AppError.Forbidden("Редактировать задачи может только капитан")
        val task = s.task(taskId)
        s.validate(draft, s.project(task.projectId))
        val now = clock.instant()
        val contentChanged = task.title != draft.title.trim() || task.description != draft.description.trim() ||
            task.priority != draft.priority || task.dueAt != draft.dueAt
        val history = buildList {
            addAll(task.history)
            if (contentChanged) add(entry(TaskAction.EDITED, actor, now))
            if (task.assigneeId != draft.assigneeId) {
                add(entry(TaskAction.ASSIGNED, actor, now, comment = draft.assigneeId?.let { s.userName(it) } ?: "Без исполнителя"))
            }
        }
        val updated = task.copy(
            title = draft.title.trim(),
            description = draft.description.trim(),
            priority = draft.priority,
            assigneeId = draft.assigneeId,
            dueAt = draft.dueAt,
            history = history,
        )
        s.replaceTask(updated) to updated
    }

    /**
     * BUG-08: капитан явно переназначает задачу на себя (например, чтобы сдать её самому).
     * Если капитана нет в составе проекта, он добавляется в проект.
     */
    suspend fun assignToSelf(actor: Session, taskId: TaskId) = mutate { s ->
        val task = s.task(taskId)
        if (!TaskPolicy.canAssignToSelf(actor, task, s.columnOf(task))) {
            throw AppError.Forbidden("Переназначить задачу на себя может капитан, пока задача не принята")
        }
        val project = s.project(task.projectId)
        val projects = if (project.isMember(actor.user.id)) s.projects else s.projects.map {
            if (it.id == project.id) it.copy(members = it.members + (actor.user.id to "Капитан")) else it
        }
        val updated = task.copy(
            assigneeId = actor.user.id,
            history = task.history + entry(TaskAction.ASSIGNED, actor, clock.instant(), comment = actor.user.fullName),
        )
        s.copy(projects = projects).replaceTask(updated).notify(
            listOf(task.assigneeId), actor, NotificationKind.STATUS,
            task.title, "${actor.user.fullName} переназначил(а) задачу на себя", task.id,
        ) to Unit
    }

    /** Возвращает ссылки на файлы задачи, чтобы репозиторий удалил их с устройства. */
    suspend fun deleteTask(actor: Session, taskId: TaskId): List<String> = mutate { s ->
        if (!TaskPolicy.canDeleteTask(actor)) throw AppError.Forbidden("Удалять задачи может только капитан")
        val task = s.task(taskId)
        s.copy(
            tasks = s.tasks.filterNot { it.id == taskId },
            notifications = s.notifications.mapValues { (_, list) -> list.filterNot { it.taskId == taskId } },
        ) to task.attachments.mapNotNull { it.fileRef }
    }

    suspend fun takeTask(actor: Session, taskId: TaskId) = mutate { s ->
        val task = s.task(taskId)
        val current = s.columnOf(task)
        if (!TaskPolicy.canTake(actor, task, current)) {
            throw AppError.Forbidden("Задача назначена другому участнику")
        }
        val target = s.columnsOf(task.projectId).firstOrNull { it.kind == ColumnKind.IN_PROGRESS }
            ?: throw AppError.Validation("На доске нет рабочей колонки")
        val now = clock.instant()
        val updated = task.copy(
            assigneeId = actor.user.id,
            columnId = target.id,
            position = s.nextPosition(target.id),
            history = task.history + entry(TaskAction.TAKEN, actor, now, current.title, target.title),
        )
        s.replaceTask(updated).notify(
            listOf(task.createdBy), actor, NotificationKind.STATUS,
            task.title, "${actor.user.fullName} взял(а) задачу в работу", task.id,
        ) to Unit
    }

    suspend fun moveTask(actor: Session, taskId: TaskId, columnId: ColumnId, comment: String?) = mutate { s ->
        val task = s.task(taskId)
        val columns = s.columnsOf(task.projectId)
        val current = s.columnOf(task)
        val option = TaskPolicy.moveOptions(actor, task, columns).firstOrNull { it.target.id == columnId }
            ?: throw AppError.Forbidden("Этот переход вам недоступен")
        val note = comment?.trim()?.takeIf { it.isNotEmpty() }
        if (option.requiresComment && note == null) throw AppError.Validation("Укажите замечания для доработки")
        val target = option.target
        val now = clock.instant()
        val updated = task.copy(
            columnId = target.id,
            position = s.nextPosition(target.id),
            completedAt = if (target.kind == ColumnKind.DONE) now else null,
            history = task.history + entry(
                TaskPolicy.actionFor(current.kind, target.kind), actor, now, current.title, target.title, note,
            ),
            comments = if (note != null && option.requiresComment) {
                task.comments + TaskComment(newId(), actor.user.id, "Замечания: $note", now)
            } else task.comments,
        )
        val action = TaskPolicy.actionFor(current.kind, target.kind)
        val notified = when (action) {
            TaskAction.RETURNED -> s.replaceTask(updated).notify(
                listOf(task.assigneeId), actor, NotificationKind.RETURNED,
                task.title, "Вернули на доработку: $note", task.id,
            )
            TaskAction.SUBMITTED -> s.replaceTask(updated).notify(
                s.captains() + task.createdBy, actor, NotificationKind.STATUS,
                task.title, "${actor.user.fullName} сдал(а) задачу на проверку", task.id,
            )
            else -> s.replaceTask(updated).notify(
                listOf(task.assigneeId, task.createdBy), actor, NotificationKind.STATUS,
                task.title, "Статус: ${target.title}", task.id,
            )
        }
        notified to Unit
    }

    suspend fun addComment(actor: Session, taskId: TaskId, text: String) = mutate { s ->
        val normalized = text.trim()
        if (normalized.isEmpty()) throw AppError.Validation("Пустой комментарий")
        if (normalized.length > 2000) throw AppError.Validation("Комментарий длиннее 2000 символов")
        val task = s.task(taskId)
        val updated = task.copy(comments = task.comments + TaskComment(newId(), actor.user.id, normalized, clock.instant()))
        s.replaceTask(updated).notify(
            listOf(task.assigneeId, task.createdBy), actor, NotificationKind.COMMENT,
            task.title, "${actor.user.fullName}: ${normalized.take(120)}", task.id,
        ) to Unit
    }

    suspend fun addAttachment(actor: Session, taskId: TaskId, file: PickedFile, kind: AttachmentKind) =
        mutate { s ->
            val task = s.task(taskId)
            if (!TaskPolicy.canUpload(actor, task, s.columnOf(task), kind)) {
                throw AppError.Forbidden(
                    if (kind == AttachmentKind.RESULT) "Сдать результат может только исполнитель задачи"
                    else "Исходники прикрепляет капитан",
                )
            }
            if (file.sizeBytes > MAX_FILE_BYTES) throw AppError.Validation("Файл больше 10 МБ")
            val attachment = Attachment(newId(), file.name, file.sizeBytes, kind, actor.user.id, clock.instant(), file.fileRef)
            s.replaceTask(task.copy(attachments = task.attachments + attachment)) to Unit
        }

    /**
     * Удаление файла задачи: автор файла или капитан, пока задача не принята.
     * Возвращает ссылку на содержимое, чтобы репозиторий удалил сам файл с устройства.
     */
    suspend fun removeAttachment(actor: Session, taskId: TaskId, attachmentId: String): String? = mutate { s ->
        val task = s.task(taskId)
        val attachment = task.attachments.firstOrNull { it.id == attachmentId } ?: throw AppError.NotFound("Файл не найден")
        if (!TaskPolicy.canRemoveAttachment(actor, attachment, s.columnOf(task))) {
            throw AppError.Forbidden("Удалить файл может его автор или капитан, пока задача не принята")
        }
        s.replaceTask(task.copy(attachments = task.attachments - attachment)) to attachment.fileRef
    }

    // ---------- Колонки ----------

    suspend fun saveColumns(actor: Session, projectId: ProjectId, drafts: List<ColumnDraft>) = mutate { s ->
        if (!TaskPolicy.canManageColumns(actor)) throw AppError.Forbidden("Настраивать доску может только капитан")
        s.project(projectId)
        if (drafts.any { it.title.isBlank() }) throw AppError.Validation("У каждой колонки должно быть название")
        if (drafts.size < ColumnKind.MIN_COLUMNS) {
            throw AppError.Validation("На доске должно быть не меньше ${ColumnKind.MIN_COLUMNS} колонок")
        }
        val existing = s.columnsOf(projectId)
        val keptIds = drafts.mapNotNull { it.id }.toSet()
        val removed = existing.filter { it.id !in keptIds }
        val blocked = removed.firstOrNull { column -> s.tasks.any { it.columnId == column.id } }
        if (blocked != null) throw AppError.Validation("В колонке «${blocked.title}» есть задачи — сначала перенесите их")
        val updated = drafts.mapIndexed { index, draft ->
            BoardColumn(
                draft.id ?: ColumnId(newId()), projectId, draft.title.trim(), index,
                ColumnKind.forPosition(index, drafts.size), draft.color,
            )
        }
        // Если колонка сменила роль, время выполнения задач в ней пересчитывается.
        val doneIds = updated.filter { it.kind == ColumnKind.DONE }.map { it.id }.toSet()
        val now = clock.instant()
        val tasks = s.tasks.map { task ->
            if (task.projectId != projectId) task
            else if (task.columnId in doneIds) task.copy(completedAt = task.completedAt ?: now)
            else task.copy(completedAt = null)
        }
        s.copy(columns = s.columns.filterNot { it.projectId == projectId } + updated, tasks = tasks) to Unit
    }

    companion object {
        const val MAX_FILE_BYTES = 10L * 1024 * 1024
        const val MAX_NOTIFICATIONS = 100
    }
}
