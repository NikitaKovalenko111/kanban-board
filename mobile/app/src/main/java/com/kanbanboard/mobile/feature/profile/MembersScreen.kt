package com.kanbanboard.mobile.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kanbanboard.mobile.core.designsystem.AppTopBar
import com.kanbanboard.mobile.core.designsystem.Caption
import com.kanbanboard.mobile.core.designsystem.BackButton
import com.kanbanboard.mobile.core.designsystem.EmptyState
import com.kanbanboard.mobile.core.designsystem.SpecRow
import com.kanbanboard.mobile.core.designsystem.Tag
import com.kanbanboard.mobile.core.designsystem.UserAvatar
import com.kanbanboard.mobile.core.designsystem.ext
import com.kanbanboard.mobile.core.features.Features
import com.kanbanboard.mobile.core.model.NewAccount
import com.kanbanboard.mobile.core.model.StudyGroup
import com.kanbanboard.mobile.core.model.TeamMember
import com.kanbanboard.mobile.core.model.TeamRole
import com.kanbanboard.mobile.core.time.Formats

/**
 * Полный список участников с поиском. Капитан дополнительно видит заблокированных,
 * может создать аккаунт, заблокировать/восстановить доступ и сбросить пароль.
 */
@Composable
fun MembersRouteScreen(onBack: () -> Unit) {
    val vm = profileViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    var query by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf<Pair<String, String>?>(null) }
    var deleting by remember { mutableStateOf<TeamMember?>(null) }

    val visible = (if (state.isCaptain) state.members else state.activeMembers)
        .filter { query.isBlank() || it.user.fullName.contains(query, true) || it.user.login.contains(query, true) }
        .sortedWith(compareBy<TeamMember>({ !it.user.isActive }, { it.role != TeamRole.CAPTAIN }, { it.user.fullName }))

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { AppTopBar(title = "Участники", navigation = { BackButton(onBack) }) },
        floatingActionButton = {
            if (state.isCaptain) {
                ExtendedFloatingActionButton(
                    onClick = { adding = true },
                    icon = { Icon(Icons.Outlined.PersonAdd, contentDescription = null) },
                    text = { Text("Добавить") },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = if (state.isCaptain) 96.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Поиск по имени или логину") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        { IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, contentDescription = "Очистить") } }
                    } else null,
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (visible.isEmpty()) {
                item { EmptyState(Icons.Outlined.PersonSearch, "Никого не нашли", Modifier.padding(top = 24.dp)) }
            }
            items(visible, key = { it.user.id.value }) { member ->
                Card(
                    onClick = { selectedId = member.user.id.value },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    ListItem(
                        leadingContent = { UserAvatar(member.user.fullName, photo = member.user.photo) },
                        headlineContent = { Text(member.user.fullName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text("${roleTitle(member.role)} · группа ${member.user.studyGroup.code} · @${member.user.login}")
                        },
                        trailingContent = if (!member.user.isActive) {
                            { Tag("Заблокирован", color = MaterialTheme.ext.danger) }
                        } else null,
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }

    val selected = state.members.firstOrNull { it.user.id.value == selectedId }
    if (selected != null) {
        val isSelf = selected.user.id == state.session?.user?.id
        MemberDialog(
            member = selected,
            canManage = state.isCaptain && !isSelf,
            onDismiss = { selectedId = null },
            onToggleActive = {
                vm.setMemberActive(selected.user.id, !selected.user.isActive)
                selectedId = null
            },
            onResetPassword = {
                vm.resetPassword(selected.user.id) { newPassword = selected.user.login to it }
                selectedId = null
            },
            onToggleCaptain = {
                vm.setMemberRole(
                    selected.user.id,
                    if (selected.role == TeamRole.CAPTAIN) TeamRole.MEMBER else TeamRole.CAPTAIN,
                )
                selectedId = null
            },
            onDelete = {
                deleting = selected
                selectedId = null
            },
        )
    }

    deleting?.let { member ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Icons.Outlined.PersonRemove, contentDescription = null) },
            title = { Text("Удалить аккаунт?") },
            text = {
                Text(
                    "${member.user.fullName} больше не сможет войти и пропадёт из проектов. " +
                        "Его личные события удалятся, выполненные задачи и комментарии останутся.",
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteAccount(member.user.id) { deleting = null }; deleting = null }) {
                    Text("Удалить", color = MaterialTheme.ext.danger)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } },
        )
    }

    newPassword?.let { (login, password) ->
        AlertDialog(
            onDismissRequest = { newPassword = null },
            title = { Text("Новый временный пароль") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Для @$login. Пароль показывается один раз — передайте его лично.")
                    Text(password, style = MaterialTheme.typography.headlineSmall)
                }
            },
            confirmButton = { TextButton(onClick = { newPassword = null }) { Text("Готово") } },
        )
    }

    if (adding) {
        AddAccountDialog(
            onDismiss = { adding = false },
            onCreate = { account, onError -> vm.createAccount(account, onError) { adding = false } },
        )
    }
}

private fun roleTitle(role: TeamRole) = if (role == TeamRole.CAPTAIN) "Капитан" else "Участник"

@Composable
private fun MemberDialog(
    member: TeamMember,
    canManage: Boolean,
    onDismiss: () -> Unit,
    onToggleActive: () -> Unit,
    onResetPassword: () -> Unit,
    onToggleCaptain: () -> Unit,
    onDelete: () -> Unit,
) {
    val user = member.user
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { UserAvatar(user.fullName, size = 56.dp, photo = user.photo) },
        title = { Text(user.fullName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SpecRow("Роль", roleTitle(member.role))
                SpecRow("Группа", user.studyGroup.code)
                SpecRow("Логин", "@${user.login}")
                user.contact?.let { SpecRow("Контакт", it) }
                SpecRow("В команде с", Formats.date(user.joinedAt))
                SpecRow(
                    "Доступ",
                    if (user.isActive) "Активен" else "Заблокирован",
                    valueColor = if (user.isActive) MaterialTheme.ext.success else MaterialTheme.ext.danger,
                )
                if (canManage) {
                    Column(Modifier.padding(top = 8.dp)) {
                        TextButton(onClick = onResetPassword) { Text("Сбросить пароль") }
                        TextButton(onClick = onToggleCaptain, enabled = Features.CHANGE_TEAM_ROLE) {
                            Text(if (member.role == TeamRole.CAPTAIN) "Снять роль капитана" else "Сделать капитаном")
                        }
                        TextButton(onClick = onDelete, enabled = Features.DELETE_ACCOUNT) {
                            Text(
                                "Удалить аккаунт",
                                color = if (Features.DELETE_ACCOUNT) MaterialTheme.ext.danger else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            )
                        }
                        if (!Features.CHANGE_TEAM_ROLE || !Features.DELETE_ACCOUNT) {
                            Caption(Features.BLOCKED_HINT, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
        dismissButton = if (canManage) {
            {
                TextButton(onClick = onToggleActive) {
                    Text(
                        if (user.isActive) "Заблокировать" else "Восстановить",
                        color = if (user.isActive) MaterialTheme.ext.danger else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        } else null,
    )
}

@Composable
private fun AddAccountDialog(
    onDismiss: () -> Unit,
    onCreate: (NewAccount, (String) -> Unit) -> Unit,
) {
    var fullName by rememberSaveable { mutableStateOf("") }
    var login by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf(StudyGroup.G203) }
    var role by rememberSaveable { mutableStateOf(TeamRole.MEMBER) }
    var error by remember { mutableStateOf<String?>(null) }
    val valid = fullName.isNotBlank() && login.isNotBlank() && password.length >= 8

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый аккаунт") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(fullName, { fullName = it }, label = { Text("ФИО") }, singleLine = true)
                OutlinedTextField(login, { login = it.trim() }, label = { Text("Логин") }, singleLine = true)
                OutlinedTextField(
                    password, { password = it },
                    label = { Text("Временный пароль") },
                    supportingText = { Text("Не короче 8 символов") },
                    singleLine = true,
                )
                Text("Группа", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    StudyGroup.entries.forEachIndexed { i, g ->
                        SegmentedButton(
                            selected = group == g,
                            onClick = { group = g },
                            shape = SegmentedButtonDefaults.itemShape(i, StudyGroup.entries.size),
                        ) { Text(g.code) }
                    }
                }
                Text("Роль", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    TeamRole.entries.forEachIndexed { i, r ->
                        SegmentedButton(
                            selected = role == r,
                            onClick = { role = r },
                            shape = SegmentedButtonDefaults.itemShape(i, TeamRole.entries.size),
                        ) { Text(roleTitle(r)) }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    error = null
                    onCreate(NewAccount(fullName.trim(), login, password, group, role)) { error = it }
                },
            ) { Text("Создать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
