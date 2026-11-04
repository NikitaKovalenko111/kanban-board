package com.kanbanboard.mobile.core.policy

import com.kanbanboard.mobile.core.model.Attachment
import com.kanbanboard.mobile.core.model.AttachmentKind
import com.kanbanboard.mobile.core.model.BoardColumn
import com.kanbanboard.mobile.core.model.ColumnKind
import com.kanbanboard.mobile.core.model.Session
import com.kanbanboard.mobile.core.model.Task
import com.kanbanboard.mobile.core.model.TaskAction

/** Доступный переход задачи в другую колонку. */
data class MoveOption(val target: BoardColumn, val requiresComment: Boolean)

/** Быстрое действие по свайпу карточки или главной кнопке. */
sealed interface QuickMove {
    val target: BoardColumn

    /** Взять задачу: назначить себя (если свободна) и перевести в первую рабочую колонку. */
    data class Take(override val target: BoardColumn) : QuickMove

    data class Move(override val target: BoardColumn) : QuickMove
}

/**
 * Матрица прав (ТЗ спринтов 2–3). Используется интерфейсом (что показывать) и демо-сервером
 * (что разрешать). Настоящий сервер обязан проверять те же правила сам.
 *
 * Капитан: создаёт, назначает, редактирует, удаляет задачи; принимает работу или возвращает
 * её на доработку (только с замечаниями); переставляет задачи между этапами.
 * Сдать задачу на проверку и прикрепить результат может только назначенный исполнитель —
 * в том числе капитан не сдаёт чужое решение (BUG-08). Чтобы сделать задачу самому,
 * капитан переназначает её на себя в редакторе.
 * Участник: берёт свободную задачу или назначенную на себя (QA-01); свою задачу двигает
 * Ожидание → рабочие этапы → Проверка.
 */
object TaskPolicy {

    fun canCreateTask(session: Session): Boolean = session.isCaptain
    fun canEditTask(session: Session): Boolean = session.isCaptain
    fun canDeleteTask(session: Session): Boolean = session.isCaptain
    fun canManageColumns(session: Session): Boolean = session.isCaptain
    fun canCreateAccounts(session: Session): Boolean = session.isCaptain

    fun isAssignee(session: Session, task: Task): Boolean = task.assigneeId == session.user.id

    /** QA-01: брать можно только свободную задачу или назначенную на себя. */
    fun canTake(session: Session, task: Task, current: BoardColumn): Boolean =
        current.kind == ColumnKind.BACKLOG && (task.assigneeId == null || isAssignee(session, task))

    fun canUpload(session: Session, task: Task, current: BoardColumn, kind: AttachmentKind): Boolean = when (kind) {
        AttachmentKind.SOURCE -> session.isCaptain
        // QA-02, BUG-08: результат прикрепляет только исполнитель, пока задача в работе или на проверке.
        AttachmentKind.RESULT -> isAssignee(session, task) &&
            (current.kind == ColumnKind.IN_PROGRESS || current.kind == ColumnKind.REVIEW)
    }

    /** Файл удаляет его автор или капитан, пока задача не принята. */
    fun canRemoveAttachment(session: Session, attachment: Attachment, current: BoardColumn): Boolean =
        current.kind != ColumnKind.DONE && (session.isCaptain || attachment.uploadedBy == session.user.id)

    /** BUG-08: капитан может явно переназначить задачу на себя, чтобы сдать её самому. */
    fun canAssignToSelf(session: Session, task: Task, current: BoardColumn): Boolean =
        session.isCaptain && !isAssignee(session, task) && current.kind != ColumnKind.DONE

    fun isReturn(from: ColumnKind, to: ColumnKind): Boolean =
        (from == ColumnKind.REVIEW || from == ColumnKind.DONE) &&
            (to == ColumnKind.IN_PROGRESS || to == ColumnKind.BACKLOG)

    private fun memberMayMove(from: ColumnKind, to: ColumnKind): Boolean = when (from) {
        ColumnKind.BACKLOG -> to == ColumnKind.IN_PROGRESS
        ColumnKind.IN_PROGRESS -> to == ColumnKind.IN_PROGRESS || to == ColumnKind.REVIEW
        ColumnKind.REVIEW, ColumnKind.DONE -> false
    }

    /** Все колонки, куда текущий пользователь может перевести задачу. [columns] — колонки доски задачи. */
    fun moveOptions(session: Session, task: Task, columns: List<BoardColumn>): List<MoveOption> {
        val current = columns.firstOrNull { it.id == task.columnId } ?: return emptyList()
        return columns.sortedBy { it.position }
            .filter { it.id != current.id }
            .mapNotNull { target ->
                val own = isAssignee(session, task)
                when {
                    // Сдать на проверку может только исполнитель (BUG-08).
                    target.kind == ColumnKind.REVIEW && current.kind != ColumnKind.REVIEW && !own -> null
                    // В работу задачу переводит только исполнитель; свободную — берут кнопкой «Взять»,
                    // чужую капитан сначала переназначает на себя в редакторе.
                    current.kind == ColumnKind.BACKLOG && target.kind == ColumnKind.IN_PROGRESS && !own -> null
                    session.isCaptain -> MoveOption(target, requiresComment = isReturn(current.kind, target.kind))
                    own && memberMayMove(current.kind, target.kind) -> MoveOption(target, requiresComment = false)
                    else -> null
                }
            }
    }

    /**
     * Переход в соседнюю колонку (direction = +1 вправо, -1 влево), если он не требует комментария.
     * Это действие свайпа карточки и главной кнопки в карточке задачи.
     */
    fun quickMove(session: Session, task: Task, columns: List<BoardColumn>, direction: Int): QuickMove? {
        val sorted = columns.sortedBy { it.position }
        val index = sorted.indexOfFirst { it.id == task.columnId }
        if (index < 0) return null
        val current = sorted[index]
        val target = sorted.getOrNull(index + direction) ?: return null
        if (direction > 0 && target.kind == ColumnKind.IN_PROGRESS && canTake(session, task, current)) {
            return QuickMove.Take(target)
        }
        val allowed = moveOptions(session, task, sorted).any { it.target.id == target.id && !it.requiresComment }
        return if (allowed) QuickMove.Move(target) else null
    }

    fun actionFor(from: ColumnKind, to: ColumnKind): TaskAction = when {
        isReturn(from, to) -> TaskAction.RETURNED
        to == ColumnKind.REVIEW -> TaskAction.SUBMITTED
        to == ColumnKind.DONE -> TaskAction.ACCEPTED
        from == ColumnKind.BACKLOG && to == ColumnKind.IN_PROGRESS -> TaskAction.TAKEN
        else -> TaskAction.MOVED
    }

    /** Доска должна содержать все четыре роли колонок: это гарантирует порядок и минимум колонок. */
    fun missingKinds(kinds: Collection<ColumnKind>): List<ColumnKind> = ColumnKind.entries.filter { it !in kinds }
}
