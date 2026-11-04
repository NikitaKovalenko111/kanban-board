package com.kanbanboard.mobile.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kanbanboard.mobile.core.designsystem.AppTopBar
import com.kanbanboard.mobile.core.designsystem.BackButton
import com.kanbanboard.mobile.core.designsystem.EmptyState
import com.kanbanboard.mobile.core.designsystem.ext
import com.kanbanboard.mobile.core.model.AppNotification
import com.kanbanboard.mobile.core.model.EventId
import com.kanbanboard.mobile.core.model.NotificationKind
import com.kanbanboard.mobile.core.model.TaskId
import com.kanbanboard.mobile.core.time.Formats

/**
 * Список уведомлений. При открытии все отмечаются прочитанными (кнопки «Прочитать все» нет),
 * но непрочитанные на момент открытия остаются выделенными до выхода с экрана.
 */
@Composable
fun NotificationsRouteScreen(
    onBack: () -> Unit,
    onOpenTask: (TaskId) -> Unit,
    onOpenEvent: (EventId) -> Unit,
) {
    val vm = profileViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    // Запоминаем, что было новым при открытии, до того как всё отметится прочитанным.
    val freshIds = remember { mutableSetOf<String>() }
    LaunchedEffect(state.notifications) {
        val unread = state.notifications.filter { !it.read }
        if (unread.isNotEmpty()) {
            freshIds += unread.map { it.id }
            vm.markNotificationsRead()
        }
    }

    Scaffold(topBar = { AppTopBar(title = "Уведомления", navigation = { BackButton(onBack) }) }) { padding ->
        if (state.notifications.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.NotificationsNone,
                title = "Уведомлений нет",
                text = "Здесь появятся комментарии к задачам, возвраты на доработку и ближайшие события.",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.notifications, key = { it.id }) { item ->
                NotificationCard(
                    item = item,
                    fresh = item.id in freshIds,
                    onClick = {
                        item.taskId?.let(onOpenTask)
                        item.eventId?.let { onOpenEvent(EventId(it)) }
                    },
                )
            }
        }
    }
}

@Composable
private fun NotificationCard(item: AppNotification, fresh: Boolean, onClick: () -> Unit) {
    val (icon, tint) = when (item.kind) {
        NotificationKind.COMMENT -> Icons.AutoMirrored.Outlined.Comment to MaterialTheme.colorScheme.primary
        NotificationKind.RETURNED -> Icons.AutoMirrored.Outlined.Undo to MaterialTheme.ext.warning
        NotificationKind.STATUS -> Icons.Outlined.SwapHoriz to MaterialTheme.ext.success
        NotificationKind.EVENT -> Icons.Outlined.Event to MaterialTheme.colorScheme.tertiary
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (fresh) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        ListItem(
            overlineContent = { Text("${item.kind.title} · ${Formats.dateTime(item.at)}") },
            headlineContent = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text(item.text, maxLines = 3, overflow = TextOverflow.Ellipsis) },
            leadingContent = { Icon(icon, contentDescription = null, tint = tint) },
            trailingContent = if (fresh) {
                { Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)) }
            } else null,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}
