package com.kanbanboard.mobile.feature.main

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ViewKanban
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ViewKanban
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kanbanboard.mobile.core.data.settings.AppSettings
import com.kanbanboard.mobile.core.data.settings.BottomBarStyle
import com.kanbanboard.mobile.core.model.EventId
import com.kanbanboard.mobile.core.model.ProjectId
import com.kanbanboard.mobile.core.model.TaskId
import com.kanbanboard.mobile.di.LocalAppContainer
import com.kanbanboard.mobile.feature.board.BoardRouteScreen
import com.kanbanboard.mobile.feature.history.HistoryRouteScreen
import com.kanbanboard.mobile.feature.profile.ProfileRouteScreen
import com.kanbanboard.mobile.feature.projects.ProjectsRouteScreen
import com.kanbanboard.mobile.feature.schedule.ScheduleRouteScreen
import com.kanbanboard.mobile.navigation.BoardTab
import com.kanbanboard.mobile.navigation.HistoryTab
import com.kanbanboard.mobile.navigation.ProfileTab
import com.kanbanboard.mobile.navigation.ProjectsTab
import com.kanbanboard.mobile.navigation.ScheduleTab
import java.time.LocalDate

/** Вкладки нижней панели в порядке ТЗ. Порядок задаёт и направление анимации сдвига. */
private enum class MainTab(
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
    val route: Any,
) {
    BOARD("Доска", Icons.Filled.ViewKanban, Icons.Outlined.ViewKanban, BoardTab),
    SCHEDULE("Расписание", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth, ScheduleTab),
    PROJECTS("Проекты", Icons.Filled.Folder, Icons.Outlined.Folder, ProjectsTab),
    HISTORY("История", Icons.Filled.History, Icons.Outlined.History, HistoryTab),
    PROFILE("Профиль", Icons.Filled.AccountCircle, Icons.Outlined.AccountCircle, ProfileTab);

    companion object {
        fun of(destination: NavDestination?): MainTab? =
            entries.firstOrNull { tab -> destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true }
    }
}

/** Переключение вкладки с сохранением состояния каждой (скролл, выбранная колонка). */
private fun NavController.openTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private const val TAB_ANIMATION_MS = 260

/** Сдвиг влево/вправо по порядку вкладок (включается в настройках приложения). */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabDirection(): AnimatedContentTransitionScope.SlideDirection? {
    val from = MainTab.of(initialState.destination)?.ordinal ?: return null
    val to = MainTab.of(targetState.destination)?.ordinal ?: return null
    return when {
        to > from -> AnimatedContentTransitionScope.SlideDirection.Left
        to < from -> AnimatedContentTransitionScope.SlideDirection.Right
        else -> null
    }
}

@Composable
fun MainScreen(
    openBoardRequest: Boolean,
    onOpenBoardHandled: () -> Unit,
    onOpenProject: (ProjectId) -> Unit,
    onCreateProject: () -> Unit,
    onOpenTask: (TaskId) -> Unit,
    onCreateTask: (ProjectId) -> Unit,
    onOpenEvent: (EventId) -> Unit,
    onCreateEvent: (LocalDate) -> Unit,
    onOpenAccountSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenMembers: () -> Unit,
) {
    val container = LocalAppContainer.current
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val tabs = rememberNavController()
    val current by tabs.currentBackStackEntryAsState()
    val selectedTab = MainTab.of(current?.destination)

    // Запрос «открыть доску» приходит из карточки проекта после возврата на главный экран.
    LaunchedEffect(openBoardRequest) {
        if (openBoardRequest) {
            tabs.openTab(BoardTab)
            onOpenBoardHandled()
        }
    }

    val animate = settings.tabAnimation
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        val direction = if (animate) tabDirection() else null
        if (direction != null) slideIntoContainer(direction, tween(TAB_ANIMATION_MS)) else fadeIn(tween(150))
    }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        val direction = if (animate) tabDirection() else null
        if (direction != null) slideOutOfContainer(direction, tween(TAB_ANIMATION_MS)) else fadeOut(tween(150))
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            when (settings.bottomBar) {
                BottomBarStyle.STATIC -> StaticNavBar(selectedTab) { tabs.openTab(it.route) }
                BottomBarStyle.FLOATING -> FloatingNavBar(selectedTab) { tabs.openTab(it.route) }
            }
        },
    ) { padding ->
        NavHost(
            navController = tabs,
            startDestination = BoardTab,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            enterTransition = enter,
            exitTransition = exit,
            popEnterTransition = enter,
            popExitTransition = exit,
        ) {
            composable<BoardTab> {
                BoardRouteScreen(
                    onOpenTask = onOpenTask,
                    onCreateTask = onCreateTask,
                    onOpenHistory = { tabs.openTab(HistoryTab) },
                    onOpenProjects = { tabs.openTab(ProjectsTab) },
                    onCreateProject = onCreateProject,
                )
            }
            composable<ScheduleTab> {
                ScheduleRouteScreen(onOpenEvent = onOpenEvent, onCreateEvent = onCreateEvent, onOpenTask = onOpenTask)
            }
            composable<ProjectsTab> {
                ProjectsRouteScreen(
                    onOpenProject = onOpenProject,
                    onCreateProject = onCreateProject,
                    onOpenBoard = { tabs.openTab(BoardTab) },
                )
            }
            composable<HistoryTab> { HistoryRouteScreen(onOpenTask = onOpenTask) }
            composable<ProfileTab> {
                ProfileRouteScreen(
                    onOpenAccountSettings = onOpenAccountSettings,
                    onOpenAppSettings = onOpenAppSettings,
                    onOpenNotifications = onOpenNotifications,
                    onOpenMembers = onOpenMembers,
                )
            }
        }
    }
}

/** Статичная нижняя панель — стандартный NavigationBar M3 с подписями всех вкладок. */
@Composable
private fun StaticNavBar(selected: MainTab?, onSelect: (MainTab) -> Unit) {
    NavigationBar {
        MainTab.entries.forEach { tab ->
            val isSelected = tab == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { if (!isSelected) onSelect(tab) },
                icon = { Icon(if (isSelected) tab.selectedIcon else tab.icon, contentDescription = null) },
                label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

/**
 * Плавающая панель-«овал»: только иконки, у выбранной вкладки появляется название.
 * Зона нажатия каждой вкладки не меньше 48 dp; для TalkBack у иконок есть подписи.
 */
@Composable
private fun FloatingNavBar(selected: MainTab?, onSelect: (MainTab) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
        ) {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 8.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MainTab.entries.forEach { tab ->
                    val isSelected = tab == selected
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.selectable(
                            selected = isSelected,
                            onClick = { if (!isSelected) onSelect(tab) },
                            role = Role.Tab,
                        ),
                    ) {
                        Row(
                            Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (isSelected) tab.selectedIcon else tab.icon,
                                contentDescription = tab.label,
                            )
                            AnimatedVisibility(
                                visible = isSelected,
                                enter = expandHorizontally() + fadeIn(),
                                exit = shrinkHorizontally() + fadeOut(),
                            ) {
                                Row {
                                    Spacer(Modifier.width(8.dp))
                                    Text(tab.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
