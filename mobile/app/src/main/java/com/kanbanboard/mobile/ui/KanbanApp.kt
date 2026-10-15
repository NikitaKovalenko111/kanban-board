package com.kanbanboard.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kanbanboard.mobile.model.EventVisibility
import com.kanbanboard.mobile.model.Lesson
import com.kanbanboard.mobile.model.Task
import com.kanbanboard.mobile.model.TaskStatus
import com.kanbanboard.mobile.model.TeamEvent
import com.kanbanboard.mobile.ui.theme.AppBorder
import com.kanbanboard.mobile.ui.theme.AppDanger
import com.kanbanboard.mobile.ui.theme.AppMuted
import com.kanbanboard.mobile.ui.theme.AppPrimary
import com.kanbanboard.mobile.ui.theme.AppSuccess

private enum class HomeSection(val title: String, val symbol: String) {
    BOARD("Доска", "▦"),
    SCHEDULE("Расписание", "◫"),
    EVENTS("События", "●"),
    PROFILE("Профиль", "○"),
}

@Composable
fun KanbanApp(viewModel: AppViewModel = viewModel()) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = if (viewModel.uiState.user == null) "login" else "home",
    ) {
        composable("login") {
            LoginScreen(
                error = viewModel.uiState.loginError,
                onLogin = { id ->
                    viewModel.login(id)
                    if (viewModel.uiState.user != null) {
                        navController.navigate("home") { popUpTo("login") { inclusive = true } }
                    }
                },
            )
        }
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onTaskClick = { navController.navigate("task/${it.id}") },
                onLogout = {
                    viewModel.logout()
                    navController.navigate("login") { popUpTo("home") { inclusive = true } }
                },
            )
        }
        composable(
            route = "task/{taskId}",
            arguments = listOf(navArgument("taskId") { type = NavType.LongType }),
        ) { entry ->
            val taskId = entry.arguments?.getLong("taskId") ?: return@composable
            val task = viewModel.uiState.tasks.firstOrNull { it.id == taskId }
            if (task == null) {
                EmptyState("Задача не найдена")
            } else {
                TaskDetailScreen(
                    task = task,
                    onBack = navController::popBackStack,
                    onMove = { viewModel.moveTask(taskId, it) },
                    onComment = { viewModel.addComment(taskId, it) },
                )
            }
        }
    }
}

@Composable
private fun LoginScreen(error: String?, onLogin: (String) -> Unit) {
    var id by rememberSaveable { mutableStateOf("") }
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                BrandMark()
                Spacer(Modifier.height(28.dp))
                Text("Добро пожаловать", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Введите личный ID, чтобы открыть задачи вашей команды.",
                    color = AppMuted,
                    lineHeight = 21.sp,
                )
                Spacer(Modifier.height(22.dp))
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it },
                    label = { Text("Личный ID") },
                    placeholder = { Text("Например, samir-203") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onLogin(id) }),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onLogin(id) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Войти", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Sprint 0 · демонстрационные данные",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = AppMuted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun BrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(AppPrimary),
            contentAlignment = Alignment.Center,
        ) {
            Text("K", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("KANBAN", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            Text("командная работа", color = AppMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun HomeScreen(
    viewModel: AppViewModel,
    onTaskClick: (Task) -> Unit,
    onLogout: () -> Unit,
) {
    var selectedSection by rememberSaveable { mutableIntStateOf(0) }
    val section = HomeSection.entries[selectedSection]

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppHeader(section.title, viewModel.uiState.user?.name ?: "") },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                HomeSection.entries.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedSection == index,
                        onClick = { selectedSection = index },
                        icon = { Text(item.symbol, fontSize = 19.sp, fontWeight = FontWeight.Bold) },
                        label = { Text(item.title, fontSize = 10.sp) },
                    )
                }
            }
        },
    ) { padding ->
        when (section) {
            HomeSection.BOARD -> BoardScreen(viewModel.uiState.tasks, onTaskClick, Modifier.padding(padding))
            HomeSection.SCHEDULE -> ScheduleScreen(viewModel::lessons, Modifier.padding(padding))
            HomeSection.EVENTS -> EventsScreen(viewModel.events, Modifier.padding(padding))
            HomeSection.PROFILE -> ProfileScreen(viewModel.uiState.user, onLogout, Modifier.padding(padding))
        }
    }
}

@Composable
private fun AppHeader(title: String, userName: String) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("РАБОЧЕЕ ПРОСТРАНСТВО", color = AppMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(title, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        }
        Avatar(userName)
    }
}

@Composable
private fun Avatar(name: String, size: Int = 42) {
    val initials = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
    Box(
        modifier = Modifier.size(size.dp).clip(CircleShape).background(Color(0xFFEEF0FF)),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials.ifEmpty { "СА" }, color = Color(0xFF5968C5), fontWeight = FontWeight.Bold, fontSize = (size / 3).sp)
    }
}

@Composable
private fun BoardScreen(tasks: List<Task>, onTaskClick: (Task) -> Unit, modifier: Modifier = Modifier) {
    var selectedStatus by rememberSaveable { mutableStateOf(TaskStatus.WORKING) }
    val visibleTasks = tasks.filter { it.status == selectedStatus }

    Column(modifier = modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(TaskStatus.entries) { status ->
                val count = tasks.count { it.status == status }
                FilterChip(
                    selected = selectedStatus == status,
                    onClick = { selectedStatus = status },
                    label = { Text("${status.title}  $count") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if (visibleTasks.isEmpty()) {
            EmptyState("В этой колонке пока нет задач", Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(visibleTasks, key = { it.id }) { task -> TaskCard(task) { onTaskClick(task) } }
            }
        }
    }
}

@Composable
private fun TaskCard(task: Task, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#${task.id}", color = AppMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                StatusDot(task.status)
            }
            Spacer(Modifier.height(9.dp))
            Text(task.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text(task.description, color = AppMuted, fontSize = 13.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(task.assignee, 30)
                Spacer(Modifier.width(9.dp))
                Text(task.assignee, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Surface(color = Color(0xFFF5F6F8), shape = RoundedCornerShape(8.dp)) {
                    Text(task.deadline, color = AppMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                }
            }
        }
    }
}

@Composable
private fun StatusDot(status: TaskStatus) {
    val color = when (status) {
        TaskStatus.WAITING -> Color(0xFF9AA1AD)
        TaskStatus.WORKING -> AppPrimary
        TaskStatus.CHECKING -> Color(0xFFE0A04B)
        TaskStatus.DONE -> AppSuccess
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(status.title, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ScheduleScreen(lessonsProvider: (String) -> List<Lesson>, modifier: Modifier = Modifier) {
    var selectedGroup by rememberSaveable { mutableStateOf("203") }
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("203", "204").forEach { group ->
                FilterChip(
                    selected = selectedGroup == group,
                    onClick = { selectedGroup = group },
                    label = { Text("Группа $group") },
                )
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Сегодня · четверг", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Данные ИСУ сохранены локально", color = AppMuted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
            }
            items(lessonsProvider(selectedGroup)) { lesson -> LessonCard(lesson) }
        }
    }
}

@Composable
private fun LessonCard(lesson: Lesson) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.width(92.dp)) {
                Text(lesson.time.substringBefore("–"), color = AppPrimary, fontWeight = FontWeight.Bold)
                Text(lesson.time.substringAfter("–"), color = AppMuted, fontSize = 12.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(lesson.title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                Text("${lesson.room} · ${lesson.teacher}", color = AppMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun EventsScreen(events: List<TeamEvent>, modifier: Modifier = Modifier) {
    var visibility by rememberSaveable { mutableStateOf<EventVisibility?>(null) }
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = visibility == null, onClick = { visibility = null }, label = { Text("Все") })
            EventVisibility.entries.forEach { item ->
                FilterChip(selected = visibility == item, onClick = { visibility = item }, label = { Text(item.title) })
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(events.filter { visibility == null || it.visibility == visibility }) { event -> EventCard(event) }
        }
    }
}

@Composable
private fun EventCard(event: TeamEvent) {
    val accent = when (event.visibility) {
        EventVisibility.TEAM -> AppPrimary
        EventVisibility.GROUP -> Color(0xFFE0A04B)
        EventVisibility.PERSONAL -> AppSuccess
    }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Box(Modifier.width(4.dp).height(76.dp).clip(RoundedCornerShape(4.dp)).background(accent))
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(event.visibility.title.uppercase(), color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                Text(event.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text("${event.date} · ${event.time}", color = AppMuted, fontSize = 12.sp)
                Text("Напомнить: ${event.reminder}", color = AppMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ProfileScreen(user: com.kanbanboard.mobile.model.User?, onLogout: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(user?.name.orEmpty(), 78)
                    Spacer(Modifier.height(14.dp))
                    Text(user?.name ?: "Пользователь", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text(user?.role.orEmpty(), color = AppMuted)
                    Spacer(Modifier.height(14.dp))
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(10.dp)) {
                        Text("Группа ${user?.group}", color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item { InfoCard("Команда", "Студенческая команда · 6 участников") }
        item { InfoCard("Уведомления", "События, дедлайны и изменения статусов") }
        item {
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFF0F1), contentColor = AppDanger),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Выйти") }
        }
    }
}

@Composable
private fun InfoCard(title: String, text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(text, color = AppMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TaskDetailScreen(
    task: Task,
    onBack: () -> Unit,
    onMove: (TaskStatus) -> Unit,
    onComment: (String) -> Unit,
) {
    var comment by rememberSaveable { mutableStateOf("") }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("← Назад") }
                Text("Задача #${task.id}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        StatusDot(task.status)
                        Spacer(Modifier.height(12.dp))
                        Text(task.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 29.sp)
                        Spacer(Modifier.height(9.dp))
                        Text(task.description, color = AppMuted, lineHeight = 21.sp)
                        Spacer(Modifier.height(18.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(task.assignee, 34)
                            Spacer(Modifier.width(9.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(task.assignee, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Исполнитель", color = AppMuted, fontSize = 11.sp)
                            }
                            Text(task.deadline, color = AppMuted, fontSize = 12.sp)
                        }
                        task.status.next()?.let { next ->
                            Spacer(Modifier.height(18.dp))
                            Button(
                                onClick = { onMove(next) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            ) { Text("Перевести: ${next.title}") }
                        }
                    }
                }
            }
            item { SectionTitle("Файлы", task.attachments.size) }
            if (task.attachments.isEmpty()) {
                item { MutedPanel("К задаче пока не прикреплены файлы") }
            } else {
                items(task.attachments) { file -> InfoCard(file.name, file.sizeLabel) }
            }
            item { SectionTitle("Комментарии", task.comments.size) }
            if (task.comments.isEmpty()) item { MutedPanel("Комментариев пока нет") }
            items(task.comments) { item ->
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Row {
                            Text(item.author, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text(item.time, color = AppMuted, fontSize = 10.sp)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(item.text, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Добавить комментарий") },
                    trailingIcon = {
                        TextButton(onClick = {
                            onComment(comment)
                            comment = ""
                        }) { Text("Отправить") }
                    },
                    shape = RoundedCornerShape(14.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
            Text("$count", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
        }
    }
}

@Composable
private fun MutedPanel(text: String) {
    Surface(color = Color(0xFFF0F2F8), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, color = AppMuted, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = AppMuted)
    }
}
