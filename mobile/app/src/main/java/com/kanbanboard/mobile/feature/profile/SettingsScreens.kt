package com.kanbanboard.mobile.feature.profile

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kanbanboard.mobile.core.data.settings.BottomBarStyle
import com.kanbanboard.mobile.core.data.settings.ThemeMode
import com.kanbanboard.mobile.core.designsystem.AppButton
import com.kanbanboard.mobile.core.designsystem.AppButtonKind
import com.kanbanboard.mobile.core.designsystem.AppTopBar
import com.kanbanboard.mobile.core.designsystem.BackButton
import com.kanbanboard.mobile.core.designsystem.Caption
import com.kanbanboard.mobile.core.designsystem.SectionHeader
import com.kanbanboard.mobile.core.designsystem.UserAvatar
import com.kanbanboard.mobile.core.designsystem.rememberLocalImage
import com.kanbanboard.mobile.core.model.SchedulePrivacy
import com.kanbanboard.mobile.core.model.StudyGroup
import com.kanbanboard.mobile.core.notifications.rememberNotificationPermissionRequest

/** Профиль → Настройки аккаунта: группа, фото профиля, приватность расписания. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsRouteScreen(onBack: () -> Unit) {
    val vm = profileViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    val user = state.session?.user
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setPhoto(uri)
    }
    var changingPassword by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { AppTopBar(title = "Настройки аккаунта", navigation = { BackButton(onBack) }) },
    ) { padding ->
        if (user == null) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SectionHeader("Фото профиля") }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(user.fullName, size = 72.dp, photo = user.photo)
                    Spacer(Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AppButton(
                            if (user.photo == null) "Выбрать фото" else "Изменить",
                            onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            kind = AppButtonKind.SECONDARY,
                            icon = Icons.Outlined.PhotoCamera,
                        )
                        if (user.photo != null) {
                            AppButton("Удалить фото", onClick = { vm.removePhoto() }, kind = AppButtonKind.GHOST, icon = Icons.Outlined.Delete)
                        }
                    }
                }
            }
            item { SectionHeader("Учебная группа", modifier = Modifier.padding(top = 8.dp)) }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    StudyGroup.entries.forEachIndexed { index, group ->
                        SegmentedButton(
                            selected = user.studyGroup == group,
                            onClick = { vm.setGroup(group) },
                            shape = SegmentedButtonDefaults.itemShape(index, StudyGroup.entries.size),
                        ) { Text("Группа ${group.code}") }
                    }
                }
            }
            item { SectionHeader("Приватность расписания", modifier = Modifier.padding(top = 8.dp)) }
            item { Caption("Что другие участники видят о ваших личных событиях", maxLines = 2) }
            item {
                RadioGroup(
                    options = SchedulePrivacy.entries,
                    selected = user.schedulePrivacy,
                    title = { it.title },
                    onSelect = { vm.setPrivacy(it) },
                )
            }
            item { SectionHeader("Безопасность", modifier = Modifier.padding(top = 8.dp)) }
            item {
                AppButton(
                    "Сменить пароль",
                    onClick = { changingPassword = true },
                    kind = AppButtonKind.SECONDARY,
                    icon = Icons.Outlined.Password,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (changingPassword) {
        ChangePasswordDialog(
            onDismiss = { changingPassword = false },
            onSubmit = { current, new, onError -> vm.changePassword(current, new, onError) { changingPassword = false } },
        )
    }
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit, onSubmit: (String, String, (String) -> Unit) -> Unit) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val mismatch = repeat.isNotEmpty() && repeat != new
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Смена пароля") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    current, { current = it; error = null },
                    label = { Text("Текущий пароль") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                OutlinedTextField(
                    new, { new = it; error = null },
                    label = { Text("Новый пароль") },
                    supportingText = { Text("Не короче 8 символов") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                OutlinedTextField(
                    repeat, { repeat = it; error = null },
                    label = { Text("Повторите новый пароль") },
                    isError = mismatch,
                    supportingText = if (mismatch) { { Text("Пароли не совпадают") } } else null,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = current.isNotEmpty() && new.length >= 8 && repeat == new,
                onClick = { onSubmit(current, new) { error = it } },
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Профиль → Настройки приложения: тема, нижняя панель, анимации, обои, уведомления, папка для файлов. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsRouteScreen(onBack: () -> Unit, onOpenNotifications: () -> Unit) {
    val vm = profileViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    val settings = state.settings
    val context = LocalContext.current
    val askPermission = rememberNotificationPermissionRequest()
    val pickWallpaper = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setWallpaper(uri)
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            // Право на папку сохраняется между перезапусками приложения.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            vm.setSaveFolder(uri)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { AppTopBar(title = "Настройки приложения", navigation = { BackButton(onBack) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SectionHeader("Тема") }
            item {
                RadioGroup(
                    options = ThemeMode.entries,
                    selected = settings.theme,
                    title = { it.title },
                    onSelect = { vm.setTheme(it) },
                )
            }

            item { SectionHeader("Нижняя панель", modifier = Modifier.padding(top = 8.dp)) }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    BottomBarStyle.entries.forEachIndexed { index, style ->
                        SegmentedButton(
                            selected = settings.bottomBar == style,
                            onClick = { vm.setBottomBar(style) },
                            shape = SegmentedButtonDefaults.itemShape(index, BottomBarStyle.entries.size),
                        ) { Text(style.title) }
                    }
                }
            }
            item {
                SwitchRow(
                    title = "Анимация вкладок",
                    subtitle = "Сдвиг влево и вправо при переходе между вкладками",
                    checked = settings.tabAnimation,
                    onChange = { vm.setTabAnimation(it) },
                )
            }
            if (settings.tabAnimation) {
                item {
                    // Анимация вкладок пока в бета-версии: по умолчанию выключена (решение команды).
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "Функция в бета-версии",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    "Анимация может срабатывать дважды и подтормаживать.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    "«РАДМИРУ НЕЛЬЗЯ ЭТО ВКЛЮЧАТЬ»",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }

            item { SectionHeader("Обои", modifier = Modifier.padding(top = 8.dp)) }
            item {
                val preview = settings.wallpaper?.let { rememberLocalImage(it, maxSidePx = 512) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (preview != null) {
                        androidx.compose.foundation.Image(
                            bitmap = preview,
                            contentDescription = "Текущие обои",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(72.dp)
                                .height(128.dp)
                                .clip(MaterialTheme.shapes.medium),
                        )
                        Spacer(Modifier.width(16.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AppButton(
                            if (preview == null) "Выбрать изображение" else "Изменить",
                            onClick = { pickWallpaper.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            kind = AppButtonKind.SECONDARY,
                            icon = Icons.Outlined.Image,
                        )
                        if (settings.wallpaper != null) {
                            AppButton("Убрать обои", onClick = { vm.removeWallpaper() }, kind = AppButtonKind.GHOST)
                        }
                        Caption("Хранятся только на этом устройстве", maxLines = 2)
                    }
                }
            }

            item { SectionHeader("Уведомления", modifier = Modifier.padding(top = 8.dp)) }
            item {
                SwitchRow(
                    title = "Задачи",
                    subtitle = "Комментарии, возвраты на доработку, смена статуса",
                    checked = settings.pushTasks,
                    onChange = { enabled ->
                        if (enabled) askPermission()
                        vm.setPush(tasks = enabled, events = settings.pushEvents)
                    },
                )
            }
            item {
                SwitchRow(
                    title = "Напоминания о событиях и парах",
                    subtitle = "Срабатывают по вашим напоминаниям в расписании",
                    checked = settings.pushEvents,
                    onChange = { enabled ->
                        if (enabled) askPermission()
                        vm.setPush(tasks = settings.pushTasks, events = enabled)
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Все уведомления") },
                    supportingContent = { if (state.unread > 0) Text("Новых: ${state.unread}") },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(onClick = onOpenNotifications),
                )
            }

            item { SectionHeader("Файлы", modifier = Modifier.padding(top = 8.dp)) }
            item {
                ListItem(
                    headlineContent = { Text("Папка для сохранения") },
                    supportingContent = {
                        Text(
                            settings.saveFolder?.let { folderLabel(it) }
                                ?: "Не выбрана — спросим при первом сохранении. В ней создаются папки проектов.",
                        )
                    },
                    leadingContent = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { pickFolder.launch(null) },
                )
            }
        }
    }
}

/** Человекочитаемое имя папки из SAF-ссылки (последний сегмент пути). */
internal fun folderLabel(uri: String): String =
    android.net.Uri.decode(uri).substringAfterLast(':').substringAfterLast('/').ifBlank { "Выбранная папка" }

@Composable
private fun <T> RadioGroup(options: List<T>, selected: T, title: (T) -> String, onSelect: (T) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.selectableGroup()) {
            options.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = option == selected, onClick = { onSelect(option) }, role = Role.RadioButton)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = option == selected, onClick = null)
                    Spacer(Modifier.width(16.dp))
                    Text(title(option), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.selectable(selected = checked, onClick = { onChange(!checked) }, role = Role.Switch),
    )
}
