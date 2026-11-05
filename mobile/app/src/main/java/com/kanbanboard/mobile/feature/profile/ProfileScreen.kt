package com.kanbanboard.mobile.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kanbanboard.mobile.core.designsystem.AppButton
import com.kanbanboard.mobile.core.designsystem.AppButtonKind
import com.kanbanboard.mobile.core.designsystem.AppTopBar
import com.kanbanboard.mobile.core.designsystem.Caption
import com.kanbanboard.mobile.core.designsystem.UserAvatar
import com.kanbanboard.mobile.di.AppContainer
import com.kanbanboard.mobile.di.LocalAppContainer

/** Один способ создать ViewModel профиля для всех его экранов. */
@Composable
internal fun profileViewModel(): ProfileViewModel {
    val c: AppContainer = LocalAppContainer.current
    return viewModel {
        ProfileViewModel(c.sessionRepository, c.teamRepository, c.settingsRepository, c.notificationRepository, c.fileStore)
    }
}

@Composable
fun ProfileRouteScreen(
    onOpenAccountSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenMembers: () -> Unit,
) {
    val vm = profileViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    val session = state.session

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { AppTopBar(title = "Профиль") },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (session != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(session.user.fullName, size = 64.dp, photo = session.user.photo)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(session.user.fullName, style = MaterialTheme.typography.titleLarge)
                                Caption("${if (session.isCaptain) "Капитан" else "Участник"} · группа ${session.user.studyGroup.code}")
                                Caption("@${session.user.login}")
                            }
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    MenuRow(Icons.Outlined.ManageAccounts, "Настройки аккаунта", "Группа, фото, приватность расписания", onOpenAccountSettings)
                    MenuRow(Icons.Outlined.Settings, "Настройки приложения", "Тема, нижняя панель, обои, push", onOpenAppSettings)
                    MenuRow(
                        Icons.Outlined.Notifications,
                        "Уведомления",
                        if (state.unread > 0) "Новых: ${state.unread}" else "Новых нет",
                        onOpenNotifications,
                        badge = state.unread,
                    )
                }
            }
            item {
                // Участники свёрнуты в одну плашку: полный список с поиском — на отдельном экране «Участники».
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    MenuRow(
                        Icons.Outlined.Group,
                        "Участники",
                        null,
                        onOpenMembers,
                        trailingText = "${state.activeMembers.size}",
                    )
                }
            }
            item {
                AppButton(
                    "Выйти из аккаунта",
                    onClick = { vm.logout() },
                    kind = AppButtonKind.DANGER,
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    badge: Int = 0,
    trailingText: String? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (badge > 0) {
                    Badge { Text("$badge") }
                    Spacer(Modifier.width(8.dp))
                }
                if (trailingText != null) {
                    Text(trailingText, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}
