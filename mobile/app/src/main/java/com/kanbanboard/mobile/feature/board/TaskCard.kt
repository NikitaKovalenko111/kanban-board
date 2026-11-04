package com.kanbanboard.mobile.feature.board

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kanbanboard.mobile.core.designsystem.Caption
import com.kanbanboard.mobile.core.designsystem.Tag
import com.kanbanboard.mobile.core.designsystem.UserAvatar
import com.kanbanboard.mobile.core.designsystem.color
import com.kanbanboard.mobile.core.designsystem.ext
import com.kanbanboard.mobile.core.model.TaskPriority
import com.kanbanboard.mobile.core.time.Formats
import java.time.Instant

/**
 * Карточка задачи (MOB-12): нажатие открывает задачу, долгое нажатие — меню быстрых действий.
 * Цветная полоса слева — приоритет. Горизонтальный свайп на доске переключает колонки,
 * поэтому перемещение задачи вынесено в меню, а не на свайп карточки.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskCard(card: TaskCardUi, onClick: () -> Unit, onLongClick: () -> Unit) {
    val task = card.task
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Быстрые действия"),
        colors = if (card.isMine) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        },
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(task.priority.color()),
            )
            Column(Modifier.padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (task.description.isNotBlank()) {
                    Text(
                        task.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    val assignee = card.assignee
                    if (assignee != null) {
                        UserAvatar(assignee.fullName, size = 24.dp, photo = assignee.photo)
                        Text(
                            assignee.fullName,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    } else if (task.assigneeId != null) {
                        Text("Удалённый участник", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Tag("Свободная", color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.weight(1f))
                    Counter(Icons.AutoMirrored.Outlined.Comment, task.comments.size, "комментариев")
                    Counter(Icons.Outlined.AttachFile, task.attachments.size, "файлов")
                }
                val due = task.dueAt
                if (due != null || task.priority == TaskPriority.HIGH) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        if (due != null) {
                            val overdue = due.isBefore(Instant.now()) && task.completedAt == null
                            val tint = if (overdue) MaterialTheme.ext.danger else MaterialTheme.colorScheme.onSurfaceVariant
                            Icon(Icons.Outlined.Event, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Caption(Formats.dateTime(due), color = tint)
                            Spacer(Modifier.width(8.dp))
                        }
                        if (task.priority == TaskPriority.HIGH) Tag("Высокий приоритет", color = MaterialTheme.ext.danger)
                    }
                }
            }
        }
    }
}

@Composable
private fun Counter(icon: ImageVector, count: Int, what: String) {
    if (count == 0) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = "$count $what",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(2.dp))
        Caption("$count")
    }
}
