package com.kanbanboard.mobile.ui

import android.app.DatePickerDialog
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ViewKanban
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import com.kanbanboard.mobile.data.ReminderScheduler
import com.kanbanboard.mobile.model.Lesson
import com.kanbanboard.mobile.model.Task
import com.kanbanboard.mobile.model.TaskStatus
import com.kanbanboard.mobile.model.TeamEvent
import com.kanbanboard.mobile.model.TeamSettings
import com.kanbanboard.mobile.model.Team
import com.kanbanboard.mobile.model.User
import com.kanbanboard.mobile.model.UserRole
import com.kanbanboard.mobile.ui.theme.AppBorder
import com.kanbanboard.mobile.ui.theme.AppDanger
import com.kanbanboard.mobile.ui.theme.AppMuted
import com.kanbanboard.mobile.ui.theme.AppPrimary
import com.kanbanboard.mobile.ui.theme.AppSuccess
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Calendar

private enum class HomeSection(val title: String, val icon: ImageVector) {
    BOARD("Доска", Icons.Filled.ViewKanban),
    SCHEDULE("Расписание", Icons.Filled.CalendarMonth),
    HISTORY("История", Icons.Filled.History),
    MEMBERS("Участники", Icons.Filled.Groups),
    PROFILE("Профиль", Icons.Filled.AccountCircle),
}

@Composable
fun KanbanApp(viewModel: AppViewModel = viewModel()) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    NavHost(
        navController = navController,
        startDestination = if (viewModel.uiState.user == null) "login" else "home",
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(280)) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(280)) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(280)) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(280)) },
    ) {
        composable("login") {
            LoginScreen(
                error = viewModel.uiState.loginError,
                onLogin = { login, password ->
                    viewModel.login(login, password)
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
                onMemberClick = { navController.navigate("member/${it.id}") },
                onCreateTask = { navController.navigate("create-task") },
                onManageTeam = { navController.navigate("team") },
                onCreateEvent = { navController.navigate("create-event") },
                onOpenSettings = { navController.navigate("settings") },
                onOpenTeams = { navController.navigate("teams") },
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
                    currentUser = viewModel.uiState.user,
                    onBack = navController::popBackStack,
                    onMove = { viewModel.moveTask(taskId, it) },
                    onComment = { viewModel.addComment(taskId, it) },
                )
            }
        }
        composable("create-task") {
            CreateTaskScreen(
                onBack = navController::popBackStack,
                onCreate = { title, description, deadline ->
                    viewModel.createTask(title, description, deadline)
                    navController.popBackStack()
                },
            )
        }
        composable(
            route = "member/{memberId}",
            arguments = listOf(navArgument("memberId") { type = NavType.LongType }),
        ) { entry ->
            val memberId = entry.arguments?.getLong("memberId") ?: return@composable
            val member = viewModel.uiState.members.firstOrNull { it.id == memberId }
            MemberProfileScreen(
                member,
                viewModel.uiState.tasks.filter { it.teamId == viewModel.uiState.selectedTeamId },
                navController::popBackStack,
            )
        }
        composable("team") {
            val activeTeam = viewModel.uiState.teams.firstOrNull { it.id == viewModel.uiState.selectedTeamId }
            TeamManagementScreen(
                user = viewModel.uiState.user,
                members = viewModel.uiState.members.filter { it.id in (activeTeam?.memberIds ?: emptySet()) },
                settings = viewModel.uiState.teamSettings,
                onBack = navController::popBackStack,
                onToggleMember = viewModel::toggleMember,
                onUpdateTeam = viewModel::updateTeam,
                onAddMember = viewModel::addMember,
                onMemberClick = { navController.navigate("member/${it.id}") },
            )
        }
        composable("settings") {
            SettingsScreen(viewModel, navController::popBackStack)
        }
        composable("create-event") {
            CreateEventScreen(viewModel, navController::popBackStack) { event ->
                ReminderScheduler.schedule(context, event)
                if (event.reminderMinutes.isNotEmpty() && Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                navController.popBackStack()
            }
        }
        composable("teams") {
            TeamsScreen(
                user = viewModel.uiState.user,
                teams = viewModel.uiState.teams,
                selectedTeamId = viewModel.uiState.selectedTeamId,
                onBack = navController::popBackStack,
                onTeamClick = { navController.navigate("team-details/${it.id}") },
            )
        }
        composable(
            route = "team-details/{teamId}",
            arguments = listOf(navArgument("teamId") { type = NavType.LongType }),
        ) { entry ->
            val teamId = entry.arguments?.getLong("teamId") ?: return@composable
            TeamDetailsScreen(
                team = viewModel.uiState.teams.firstOrNull { it.id == teamId },
                user = viewModel.uiState.user,
                members = viewModel.uiState.members,
                selected = viewModel.uiState.selectedTeamId == teamId,
                onBack = navController::popBackStack,
                onSelect = viewModel::selectTeam,
                onJoin = viewModel::joinTeam,
                onLeave = viewModel::leaveTeam,
            )
        }
    }
}

@Composable
private fun LoginScreen(error: String?, onLogin: (String, String) -> Unit) {
    var login by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
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
                    "Введите логин и пароль, выданные тимлидом.",
                    color = AppMuted,
                    lineHeight = 21.sp,
                )
                Spacer(Modifier.height(22.dp))
                OutlinedTextField(
                    value = login,
                    onValueChange = { login = it },
                    label = { Text("Логин") },
                    placeholder = { Text("Ваш логин") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Пароль") },
                    placeholder = { Text("Ваш пароль") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onLogin(login, password) }),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onLogin(login, password) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Войти", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Тимлид: alexey / lead123\nУчастник: maria / member123",
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
    onMemberClick: (User) -> Unit,
    onCreateTask: () -> Unit,
    onManageTeam: () -> Unit,
    onCreateEvent: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTeams: () -> Unit,
    onLogout: () -> Unit,
) {
    var selectedSection by rememberSaveable { mutableIntStateOf(0) }
    val section = HomeSection.entries[selectedSection]
    val activeTeam = viewModel.uiState.teams.firstOrNull { it.id == viewModel.uiState.selectedTeamId }
    val teamTasks = viewModel.uiState.tasks.filter { it.teamId == viewModel.uiState.selectedTeamId }
    val teamMembers = viewModel.uiState.members.filter { it.id in (activeTeam?.memberIds ?: emptySet()) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppHeader(
                section.title,
                activeTeam?.name ?: "Команда не выбрана",
                viewModel.uiState.user?.name ?: "",
                onProfileClick = { selectedSection = HomeSection.PROFILE.ordinal },
            )
        },
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
                        icon = { Icon(item.icon, contentDescription = item.title, modifier = Modifier.size(24.dp)) },
                        label = { Text(item.title, fontSize = 9.sp, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        when (section) {
            HomeSection.BOARD -> BoardScreen(
                tasks = teamTasks,
                onTaskClick = onTaskClick,
                canCreate = viewModel.uiState.user?.role == UserRole.LEAD && activeTeam != null,
                onCreate = onCreateTask,
                modifier = Modifier.padding(padding),
            )
            HomeSection.SCHEDULE -> ScheduleTimelineScreen(
                viewModel = viewModel,
                tasks = teamTasks,
                onCreateEvent = onCreateEvent,
                modifier = Modifier.padding(padding),
            )
            HomeSection.HISTORY -> HistoryScreen(teamTasks, onTaskClick, Modifier.padding(padding))
            HomeSection.MEMBERS -> MembersScreen(
                members = teamMembers,
                canManage = viewModel.uiState.user?.role == UserRole.LEAD && activeTeam != null,
                onMemberClick = onMemberClick,
                onManage = onManageTeam,
                modifier = Modifier.padding(padding),
            )
            HomeSection.PROFILE -> ProfileScreen(
                user = viewModel.uiState.user,
                tasks = teamTasks,
                activeTeam = activeTeam,
                onOpenTeams = onOpenTeams,
                onOpenSettings = onOpenSettings,
                onLogout = onLogout,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun AppHeader(title: String, teamName: String, userName: String, onProfileClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(teamName.uppercase(), color = AppMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(title, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier.size(48.dp)
                .semantics { contentDescription = "Открыть профиль" }
                .clickable(role = Role.Button, onClick = onProfileClick),
            contentAlignment = Alignment.Center,
        ) { Avatar(userName) }
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
private fun BoardScreen(
    tasks: List<Task>,
    onTaskClick: (Task) -> Unit,
    canCreate: Boolean,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedStatus by rememberSaveable { mutableStateOf(TaskStatus.WORKING) }
    val visibleTasks = tasks.filter { it.status == selectedStatus }

    Column(modifier = modifier.fillMaxSize()) {
        if (canCreate) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(onClick = onCreate, shape = RoundedCornerShape(12.dp)) {
                    Text("＋ Новая задача", fontWeight = FontWeight.SemiBold)
                }
            }
        }
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
private fun HistoryScreen(tasks: List<Task>, onTaskClick: (Task) -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    var assignee by rememberSaveable { mutableStateOf("Все") }
    var periodDays by rememberSaveable { mutableIntStateOf(0) }
    val completed = tasks.filter { it.status == TaskStatus.DONE }
    val assignees = listOf("Все") + completed.map { it.assignee }.distinct()
    val visible = completed.filter { task ->
        val matchesPeriod = if (periodDays == 0 || task.completedAt == null) true else {
            runCatching {
                val date = LocalDate.parse(task.completedAt, DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                ChronoUnit.DAYS.between(date, LocalDate.now()) in 0..periodDays.toLong()
            }.getOrDefault(false)
        }
        matchesPeriod && (assignee == "Все" || task.assignee == assignee) &&
            "${task.title} ${task.description} ${task.assignee}".contains(query, ignoreCase = true)
    }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            placeholder = { Text("Поиск по истории") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { FilterChip(periodDays == 0, { periodDays = 0 }, label = { Text("Всё время") }) }
            item { FilterChip(periodDays == 7, { periodDays = 7 }, label = { Text("7 дней") }) }
            item { FilterChip(periodDays == 30, { periodDays = 30 }, label = { Text("30 дней") }) }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(assignees) { name ->
                FilterChip(selected = assignee == name, onClick = { assignee = name }, label = { Text(name) })
            }
        }
        if (visible.isEmpty()) {
            EmptyState("По заданным фильтрам задач не найдено", Modifier.weight(1f))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { Text("${completed.size} выполнено", color = AppMuted, fontSize = 12.sp) }
                items(visible, key = { it.id }) { task ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onTaskClick(task) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row {
                                Text("#${task.id}", color = AppMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text(task.completedAt ?: "—", color = AppMuted, fontSize = 11.sp)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(task.title, fontWeight = FontWeight.SemiBold)
                            Text(task.assignee, color = AppMuted, fontSize = 12.sp)
                            Text("Результат: ${task.resultAttachments.size} файл(а)", color = AppPrimary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MembersScreen(
    members: List<User>,
    canManage: Boolean,
    onMemberClick: (User) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visible = members.filter { "${it.name} ${it.login} ${it.position}".contains(query, ignoreCase = true) }
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${members.count { it.active }} активных", color = AppMuted, modifier = Modifier.weight(1f))
            if (canManage) Button(onClick = onManage, shape = RoundedCornerShape(12.dp)) { Text("Управление") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            placeholder = { Text("Найти участника") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(visible, key = { it.id }) { member -> MemberCard(member) { onMemberClick(member) } }
        }
    }
}

@Composable
private fun MemberCard(member: User, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(member.name, 44)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(member.name, fontWeight = FontWeight.SemiBold)
                Text(member.position, color = AppMuted, fontSize = 12.sp)
                Text("@${member.login}", color = AppMuted, fontSize = 11.sp)
            }
            Surface(
                color = if (member.active) Color(0xFFEAF7EF) else Color(0xFFFFF0F1),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    if (member.active) "В команде" else "Нет доступа",
                    color = if (member.active) AppSuccess else AppDanger,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun MemberProfileScreen(member: User?, tasks: List<Task>, onBack: () -> Unit) {
    if (member == null) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
            TextButton(onClick = onBack) { Text("← К участникам") }
            EmptyState("Профиль не найден", Modifier.weight(1f))
        }
        return
    }
    val memberTasks = tasks.filter { it.assignee == member.name }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { TextButton(onClick = onBack) { Text("← Назад") } }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(member.name, 82)
                    Spacer(Modifier.height(12.dp))
                    Text(member.name, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text(member.position, color = AppMuted)
                    Text("@${member.login}", color = AppMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    StatusBadge(member.active)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileStat(memberTasks.count { it.status == TaskStatus.WORKING }, "В работе", Modifier.weight(1f))
                ProfileStat(memberTasks.count { it.status == TaskStatus.CHECKING }, "Проверка", Modifier.weight(1f))
                ProfileStat(memberTasks.count { it.status == TaskStatus.DONE }, "Готово", Modifier.weight(1f))
            }
        }
        item { InfoCard("Роль", if (member.role == UserRole.LEAD) "Тимлид" else "Участник") }
        item { InfoCard("В команде с", member.joinedAt) }
        if (memberTasks.isNotEmpty()) {
            item {
                Text(
                    "Последние задачи",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(memberTasks.take(4)) { task -> TaskCard(task) {} }
        }
    }
}

@Composable
private fun StatusBadge(active: Boolean) {
    Surface(color = if (active) Color(0xFFEAF7EF) else Color(0xFFFFF0F1), shape = RoundedCornerShape(8.dp)) {
        Text(
            if (active) "Активен" else "Доступ закрыт",
            color = if (active) AppSuccess else AppDanger,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun CreateTaskScreen(onBack: () -> Unit, onCreate: (String, String, String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var deadline by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val calendar = remember { Calendar.getInstance() }
    val openDatePicker = {
        DatePickerDialog(
            context,
            { _, year, month, day -> deadline = "%02d.%02d.%04d".format(day, month + 1, year) },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        ).apply { datePicker.minDate = System.currentTimeMillis() - 1_000 }.show()
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SimpleTopBar("Новая задача", onBack) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Название задачи") }, shape = RoundedCornerShape(14.dp))
            OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth().height(140.dp), label = { Text("Описание / ТЗ") }, shape = RoundedCornerShape(14.dp))
            OutlinedButton(
                onClick = openDatePicker,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(if (deadline.isBlank()) "Выбрать дедлайн: день, месяц, год" else "Дедлайн: $deadline")
            }
            MutedPanel("Прикрепление исходников будет подключено через системный выбор файлов после согласования backend API.")
            Button(
                onClick = { onCreate(title, description, deadline) },
                enabled = title.isNotBlank() && description.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Создать задачу") }
        }
    }
}

@Composable
internal fun SimpleTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack, modifier = Modifier.height(48.dp)) { Text("← Назад") }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TeamsScreen(
    user: User?,
    teams: List<Team>,
    selectedTeamId: Long,
    onBack: () -> Unit,
    onTeamClick: (Team) -> Unit,
) {
    val myTeams = teams.filter { user != null && user.id in it.memberIds }
    val availableTeams = teams.filterNot { user != null && user.id in it.memberIds }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SimpleTopBar("Мои команды", onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (myTeams.isEmpty()) item { MutedPanel("Вы пока не состоите ни в одной команде") }
            items(myTeams, key = { "my-${it.id}" }) { team ->
                TeamCard(team, selectedTeamId == team.id, true) { onTeamClick(team) }
            }
            if (availableTeams.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(6.dp))
                    Text("Доступные команды", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }
                items(availableTeams, key = { "available-${it.id}" }) { team ->
                    TeamCard(team, false, false) { onTeamClick(team) }
                }
            }
        }
    }
}

@Composable
private fun TeamCard(team: Team, selected: Boolean, joined: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(if (selected) AppPrimary else Color(0xFFEEF0FF)),
                contentAlignment = Alignment.Center,
            ) {
                Text(team.name.take(1).uppercase(), color = if (selected) Color.White else AppPrimary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(team.name, fontWeight = FontWeight.SemiBold)
                Text(team.description, color = AppMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${team.memberIds.size} участников", color = AppMuted, fontSize = 10.sp)
            }
            Text(if (selected) "Выбрана" else if (joined) "Открыть →" else "Вступить →", color = AppPrimary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun TeamDetailsScreen(
    team: Team?,
    user: User?,
    members: List<User>,
    selected: Boolean,
    onBack: () -> Unit,
    onSelect: (Long) -> Unit,
    onJoin: (Long) -> Unit,
    onLeave: (Long) -> Unit,
) {
    if (team == null || user == null) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
            TextButton(onClick = onBack) { Text("← Назад") }
            EmptyState("Команда не найдена", Modifier.weight(1f))
        }
        return
    }
    val joined = user.id in team.memberIds
    val teamMembers = members.filter { it.id in team.memberIds }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SimpleTopBar(team.name, onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(20.dp)) {
                        Text(team.name, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(team.description, color = AppMuted, lineHeight = 20.sp)
                        Spacer(Modifier.height(14.dp))
                        Text("${teamMembers.size} участников", color = AppPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            item {
                when {
                    !joined -> Button(onClick = { onJoin(team.id) }, modifier = Modifier.fillMaxWidth()) { Text("Вступить и выбрать команду") }
                    !selected -> Button(onClick = { onSelect(team.id) }, modifier = Modifier.fillMaxWidth()) { Text("Выбрать эту команду") }
                    else -> MutedPanel("Эта команда выбрана как рабочая")
                }
            }
            if (joined) {
                item {
                    OutlinedButton(
                        onClick = { onLeave(team.id) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppDanger),
                    ) { Text("Выйти из команды") }
                }
            }
            item { Text("Участники", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
            items(teamMembers, key = { it.id }) { member -> MemberCard(member) {} }
        }
    }
}

@Composable
private fun TeamManagementScreen(
    user: User?,
    members: List<User>,
    settings: TeamSettings,
    onBack: () -> Unit,
    onToggleMember: (Long) -> Unit,
    onUpdateTeam: (String, String) -> Unit,
    onAddMember: (String, String, String, String, UserRole) -> Unit,
    onMemberClick: (User) -> Unit,
) {
    if (user?.role != UserRole.LEAD) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
            TextButton(onClick = onBack) { Text("← Назад") }
            EmptyState("Раздел доступен только тимлиду", Modifier.weight(1f))
        }
        return
    }
    var teamName by rememberSaveable(settings.name) { mutableStateOf(settings.name) }
    var description by rememberSaveable(settings.description) { mutableStateOf(settings.description) }
    var name by rememberSaveable { mutableStateOf("") }
    var login by rememberSaveable { mutableStateOf("") }
    var temporaryPassword by rememberSaveable { mutableStateOf("") }
    var position by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf(UserRole.MEMBER) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SimpleTopBar("Управление командой", onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProfileStat(members.size, "Всего", Modifier.weight(1f))
                    ProfileStat(members.count { it.active }, "Активных", Modifier.weight(1f))
                    ProfileStat(members.count { it.role == UserRole.LEAD }, "Тимлидов", Modifier.weight(1f))
                }
            }
            item { Text("Настройки команды", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
            item {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(teamName, { teamName = it }, Modifier.fillMaxWidth(), label = { Text("Название") }, shape = RoundedCornerShape(12.dp))
                        OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Описание") }, shape = RoundedCornerShape(12.dp))
                        Button(onClick = { onUpdateTeam(teamName, description) }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить") }
                    }
                }
            }
            item { Text("Новый аккаунт", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
            item {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Имя и фамилия") }, shape = RoundedCornerShape(12.dp))
                        OutlinedTextField(login, { login = it.lowercase() }, Modifier.fillMaxWidth(), label = { Text("Логин") }, shape = RoundedCornerShape(12.dp))
                        OutlinedTextField(
                            temporaryPassword,
                            { temporaryPassword = it },
                            Modifier.fillMaxWidth(),
                            label = { Text("Временный пароль") },
                            supportingText = { Text("Минимум 6 символов") },
                            visualTransformation = PasswordVisualTransformation(),
                            shape = RoundedCornerShape(12.dp),
                        )
                        OutlinedTextField(position, { position = it }, Modifier.fillMaxWidth(), label = { Text("Должность") }, shape = RoundedCornerShape(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(role == UserRole.MEMBER, { role = UserRole.MEMBER }, label = { Text("Участник") })
                            FilterChip(role == UserRole.LEAD, { role = UserRole.LEAD }, label = { Text("Тимлид") })
                        }
                        Button(
                            onClick = {
                                onAddMember(name, login, temporaryPassword, position, role)
                                name = ""; login = ""; temporaryPassword = ""; position = ""; role = UserRole.MEMBER
                            },
                            enabled = name.isNotBlank() && login.isNotBlank() && temporaryPassword.length >= 6 && position.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Создать аккаунт") }
                    }
                }
            }
            item { Text("Участники", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
            items(members, key = { it.id }) { member ->
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onMemberClick(member) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(member.name, 38)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(member.name, fontWeight = FontWeight.SemiBold)
                                Text("@${member.login} · ${member.position}", color = AppMuted, fontSize = 11.sp)
                            }
                            StatusBadge(member.active)
                        }
                        if (member.id != user.id) {
                            TextButton(onClick = { onToggleMember(member.id) }, modifier = Modifier.align(Alignment.End)) {
                                Text(if (member.active) "Заблокировать" else "Восстановить", color = if (member.active) AppDanger else AppSuccess)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileScreen(
    user: User?,
    tasks: List<Task>,
    activeTeam: Team?,
    onOpenTeams: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                    Text(user?.position.orEmpty(), color = AppMuted)
                    Spacer(Modifier.height(14.dp))
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(10.dp)) {
                        Text(if (user?.role == UserRole.LEAD) "Капитан команды" else "Участник команды", color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileStat(tasks.count { it.status == TaskStatus.WORKING }, "В работе", Modifier.weight(1f))
                ProfileStat(tasks.count { it.status == TaskStatus.DONE }, "Завершено", Modifier.weight(1f))
                ProfileStat(tasks.size, "Всего", Modifier.weight(1f))
            }
        }
        item { InfoCard("Логин", "@${user?.login.orEmpty()}") }
        item { InfoCard("Команды", activeTeam?.name ?: "Команда не выбрана", onOpenTeams) }
        item { InfoCard("Настройки приложения", "Тема и учебная группа", onOpenSettings) }
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
private fun ProfileStat(value: Int, label: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(13.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", color = AppPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(label, color = AppMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun InfoCard(title: String, text: String, onClick: (() -> Unit)? = null) {
    Surface(
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(text, color = AppMuted, fontSize = 12.sp)
            if (onClick != null) Text("Открыть →", color = AppPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TaskDetailScreen(
    task: Task,
    currentUser: User?,
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
                        val isAssignee = task.assignee == currentUser?.name
                        val nextAction = when (task.status) {
                            TaskStatus.WAITING -> if (isAssignee) TaskStatus.WORKING to "Взять в работу" else null
                            TaskStatus.WORKING -> if (isAssignee) TaskStatus.CHECKING to "Отправить на проверку" else null
                            TaskStatus.CHECKING -> if (currentUser?.role == UserRole.LEAD) TaskStatus.DONE to "Принять задачу" else null
                            TaskStatus.DONE -> if (currentUser?.role == UserRole.LEAD) TaskStatus.WORKING to "Вернуть в работу" else null
                        }
                        nextAction?.let { (next, label) ->
                            Spacer(Modifier.height(18.dp))
                            Button(
                                onClick = { onMove(next) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            ) { Text(label) }
                        }
                        if (nextAction == null && task.status != TaskStatus.DONE && currentUser != null) {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                if (task.status == TaskStatus.CHECKING) "Решение по проверке принимает тимлид"
                                else "Статус может изменить только назначенный исполнитель",
                                color = AppMuted,
                                fontSize = 12.sp,
                            )
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
            if (task.status == TaskStatus.CHECKING || task.status == TaskStatus.DONE) {
                item { SectionTitle("Результат работы", task.resultAttachments.size) }
                if (task.resultAttachments.isEmpty()) item { MutedPanel("Результат пока не прикреплён") }
                items(task.resultAttachments) { file -> InfoCard(file.name, file.sizeLabel) }
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
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, color = AppMuted, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = AppMuted)
    }
}
