package com.kanbanboard.mobile.core.policy

import com.kanbanboard.mobile.core.model.AttachmentKind
import com.kanbanboard.mobile.core.model.BoardColumn
import com.kanbanboard.mobile.core.model.ColumnId
import com.kanbanboard.mobile.core.model.ColumnKind
import com.kanbanboard.mobile.core.model.ProjectId
import com.kanbanboard.mobile.core.model.Session
import com.kanbanboard.mobile.core.model.StudyGroup
import com.kanbanboard.mobile.core.model.Task
import com.kanbanboard.mobile.core.model.TaskId
import com.kanbanboard.mobile.core.model.TaskPriority
import com.kanbanboard.mobile.core.model.TeamRole
import com.kanbanboard.mobile.core.model.User
import com.kanbanboard.mobile.core.model.UserId
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskPolicyTest {
    private val project = ProjectId("p")
    private val backlog = BoardColumn(ColumnId("b"), project, "Ожидает", 0, ColumnKind.BACKLOG, 0xFF8A94A6)
    private val work = BoardColumn(ColumnId("w"), project, "В работе", 1, ColumnKind.IN_PROGRESS, 0xFF6576DF)
    private val review = BoardColumn(ColumnId("r"), project, "На проверке", 2, ColumnKind.REVIEW, 0xFFDA8A37)
    private val done = BoardColumn(ColumnId("d"), project, "Выполнено", 3, ColumnKind.DONE, 0xFF58A779)
    private val columns = listOf(backlog, work, review, done)

    private fun session(id: String, role: TeamRole = TeamRole.MEMBER) = Session(
        User(UserId(id), id, id, StudyGroup.G203, null, true, LocalDate.of(2026, 9, 1)),
        role,
    )

    private val captain = session("lead", TeamRole.CAPTAIN)
    private val maria = session("maria")
    private val ilya = session("ilya")

    private fun task(column: BoardColumn, assignee: String?) = Task(
        TaskId("t"), project, column.id, 0, "Задача", "", TaskPriority.NORMAL,
        assignee?.let(::UserId), captain.user.id, Instant.EPOCH, null, null,
        emptyList(), emptyList(), emptyList(),
    )

    @Test
    fun `member takes free task from backlog`() {
        val free = task(backlog, null)
        assertTrue(TaskPolicy.canTake(maria, free, backlog))
        assertEquals(QuickMove.Take(work), TaskPolicy.quickMove(maria, free, columns, +1))
    }

    @Test
    fun `QA-01 member cannot take task assigned to someone else`() {
        val ilyas = task(backlog, "ilya")
        assertFalse(TaskPolicy.canTake(maria, ilyas, backlog))
        assertTrue(TaskPolicy.moveOptions(maria, ilyas, columns).isEmpty())
        assertNull(TaskPolicy.quickMove(maria, ilyas, columns, +1))
    }

    @Test
    fun `assignee submits to review but cannot accept`() {
        val mine = task(work, "maria")
        val targets = TaskPolicy.moveOptions(maria, mine, columns).map { it.target.kind }
        assertEquals(listOf(ColumnKind.REVIEW), targets)
        assertEquals(QuickMove.Move(review), TaskPolicy.quickMove(maria, mine, columns, +1))
        assertTrue(TaskPolicy.moveOptions(maria, task(review, "maria"), columns).isEmpty())
    }

    @Test
    fun `QA-02 BUG-08 only assignee uploads result, captain too must take the task`() {
        val mine = task(work, "maria")
        assertTrue(TaskPolicy.canUpload(maria, mine, work, AttachmentKind.RESULT))
        assertFalse(TaskPolicy.canUpload(ilya, mine, work, AttachmentKind.RESULT))
        assertFalse(TaskPolicy.canUpload(captain, mine, work, AttachmentKind.RESULT))
        assertTrue(TaskPolicy.canUpload(captain, task(work, "lead"), work, AttachmentKind.RESULT))
        assertFalse(TaskPolicy.canUpload(maria, mine, work, AttachmentKind.SOURCE))
    }

    @Test
    fun `only assignee submits to review`() {
        val mine = task(work, "maria")
        assertTrue(TaskPolicy.moveOptions(captain, mine, columns).none { it.target.kind == ColumnKind.REVIEW })
    }

    @Test
    fun `column kind follows position`() {
        assertEquals(ColumnKind.BACKLOG, ColumnKind.forPosition(0, 5))
        assertEquals(ColumnKind.IN_PROGRESS, ColumnKind.forPosition(2, 5))
        assertEquals(ColumnKind.REVIEW, ColumnKind.forPosition(3, 5))
        assertEquals(ColumnKind.DONE, ColumnKind.forPosition(4, 5))
    }

    @Test
    fun `captain return from review requires comment and is not a swipe`() {
        val inReview = task(review, "maria")
        val back = TaskPolicy.moveOptions(captain, inReview, columns).first { it.target.id == work.id }
        assertTrue(back.requiresComment)
        assertNull(TaskPolicy.quickMove(captain, inReview, columns, -1))
        assertEquals(QuickMove.Move(done), TaskPolicy.quickMove(captain, inReview, columns, +1))
    }

    @Test
    fun `custom second work column is allowed for assignee`() {
        val design = BoardColumn(ColumnId("w2"), project, "Дизайн", 2, ColumnKind.IN_PROGRESS, 0xFF6576DF)
        val custom = listOf(backlog, work, design, review.copy(position = 3), done.copy(position = 4))
        val mine = task(work, "maria")
        assertEquals(QuickMove.Move(design), TaskPolicy.quickMove(maria, mine, custom, +1))
    }

    @Test
    fun `missing kinds are reported`() {
        assertEquals(listOf(ColumnKind.REVIEW), TaskPolicy.missingKinds(listOf(ColumnKind.BACKLOG, ColumnKind.IN_PROGRESS, ColumnKind.DONE)))
    }
}
