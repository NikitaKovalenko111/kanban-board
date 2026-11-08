package com.kanbanboard.mobile.navigation

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import com.kanbanboard.mobile.feature.files.FileViewerRouteScreen
import com.kanbanboard.mobile.feature.profile.AccountSettingsRouteScreen
import com.kanbanboard.mobile.feature.profile.AppSettingsRouteScreen
import com.kanbanboard.mobile.feature.profile.MembersRouteScreen
import com.kanbanboard.mobile.feature.profile.NotificationsRouteScreen
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kanbanboard.mobile.feature.projects.ProjectDetailRouteScreen
import com.kanbanboard.mobile.feature.projects.ProjectEditorRouteScreen
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kanbanboard.mobile.core.model.EventId
import com.kanbanboard.mobile.core.model.ProjectId
import com.kanbanboard.mobile.feature.schedule.EventDetailRouteScreen
import com.kanbanboard.mobile.feature.schedule.EventEditorRouteScreen
import java.time.LocalDate
import com.kanbanboard.mobile.core.model.TaskId
import com.kanbanboard.mobile.feature.auth.LoginRouteScreen
import com.kanbanboard.mobile.feature.main.MainScreen
import com.kanbanboard.mobile.feature.task.TaskDetailRouteScreen
import com.kanbanboard.mobile.feature.task.TaskEditorRouteScreen

private const val DURATION = 280
private const val POP_DURATION = 220

@Composable
fun AppNavHost(loggedIn: Boolean) {
    val nav = rememberNavController()
    val start: Any = remember { if (loggedIn) MainRoute else LoginRoute }

    // Вход и выход переключают корень навигации; стек при этом очищается полностью.
    LaunchedEffect(loggedIn) {
        val current = nav.currentBackStackEntry?.destination
        if (loggedIn && current?.hasRoute<LoginRoute>() == true) {
            nav.navigate(MainRoute) { popUpTo(nav.graph.id) { inclusive = true } }
        } else if (!loggedIn && current?.hasRoute<LoginRoute>() != true) {
            nav.navigate(LoginRoute) { popUpTo(nav.graph.id) { inclusive = true } }
        }
    }

    // Горизонтальный слайд «как страницы книги»; системный predictive back управляет pop-анимацией.
    NavHost(
        navController = nav,
        startDestination = start,
        enterTransition = { slideInHorizontally(tween(DURATION)) { it / 4 } + fadeIn(tween(DURATION)) },
        exitTransition = { slideOutHorizontally(tween(DURATION)) { -it / 4 } + fadeOut(tween(DURATION)) },
        // Закрытие (кнопка или жест «Назад»): ускоряющаяся кривая и исчезание, чтобы экран
        // не «притормаживал» в конце жеста.
        popEnterTransition = {
            slideInHorizontally(tween(POP_DURATION, easing = LinearOutSlowInEasing)) { -it / 6 } + fadeIn(tween(POP_DURATION))
        },
        popExitTransition = {
            slideOutHorizontally(tween(POP_DURATION, easing = FastOutLinearInEasing)) { it / 3 } +
                fadeOut(tween(POP_DURATION, easing = FastOutLinearInEasing))
        },
    ) {
        composable<LoginRoute> { LoginRouteScreen() }

        composable<MainRoute> { entry ->
            val openBoard by entry.savedStateHandle.getStateFlow(OPEN_BOARD_KEY, false).collectAsStateWithLifecycle()
            MainScreen(
                openBoardRequest = openBoard,
                onOpenBoardHandled = { entry.savedStateHandle[OPEN_BOARD_KEY] = false },
                onOpenProject = { id -> if (entry.isResumed()) nav.navigate(ProjectRoute(id.value)) },
                onCreateProject = { if (entry.isResumed()) nav.navigate(ProjectEditorRoute()) },
                onOpenTask = { id -> if (entry.isResumed()) nav.navigate(TaskRoute(id.value)) },
                onCreateTask = { projectId -> if (entry.isResumed()) nav.navigate(TaskEditorRoute(projectId.value)) },
                onOpenEvent = { id -> if (entry.isResumed()) nav.navigate(EventRoute(id.value)) },
                onCreateEvent = { date -> if (entry.isResumed()) nav.navigate(EventEditorRoute(date = date.toString())) },
                onOpenAccountSettings = { if (entry.isResumed()) nav.navigate(AccountSettingsRoute) },
                onOpenAppSettings = { if (entry.isResumed()) nav.navigate(AppSettingsRoute) },
                onOpenNotifications = { if (entry.isResumed()) nav.navigate(NotificationsRoute) },
                onOpenMembers = { if (entry.isResumed()) nav.navigate(MembersRoute) },
            )
        }

        composable<TaskRoute> { entry ->
            val route = entry.toRoute<TaskRoute>()
            TaskDetailRouteScreen(
                taskId = TaskId(route.taskId),
                onBack = { if (entry.isResumed()) nav.popBackStack() },
                onOpenFile = { ref, name, project ->
                    if (entry.isResumed()) nav.navigate(FileViewerRoute(ref, name, project))
                },
                onEdit = { projectId, taskId ->
                    if (entry.isResumed()) nav.navigate(TaskEditorRoute(projectId.value, taskId.value))
                },
            )
        }

        composable<TaskEditorRoute> { entry ->
            val route = entry.toRoute<TaskEditorRoute>()
            TaskEditorRouteScreen(
                projectId = ProjectId(route.projectId),
                taskId = route.taskId?.let(::TaskId),
                onClose = { if (entry.isResumed()) nav.popBackStack() },
            )
        }

        composable<EventRoute> { entry ->
            val route = entry.toRoute<EventRoute>()
            EventDetailRouteScreen(
                eventId = EventId(route.eventId),
                onBack = { if (entry.isResumed()) nav.popBackStack() },
                onEdit = { id -> if (entry.isResumed()) nav.navigate(EventEditorRoute(eventId = id.value)) },
            )
        }

        composable<EventEditorRoute> { entry ->
            val route = entry.toRoute<EventEditorRoute>()
            EventEditorRouteScreen(
                eventId = route.eventId?.let(::EventId),
                initialDate = route.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now(),
                onClose = { if (entry.isResumed()) nav.popBackStack() },
            )
        }

        composable<ProjectRoute> { entry ->
            val route = entry.toRoute<ProjectRoute>()
            ProjectDetailRouteScreen(
                projectId = ProjectId(route.projectId),
                onBack = { if (entry.isResumed()) nav.popBackStack() },
                onEdit = { id -> if (entry.isResumed()) nav.navigate(ProjectEditorRoute(id.value)) },
                onOpenFile = { ref, name, project ->
                    if (entry.isResumed()) nav.navigate(FileViewerRoute(ref, name, project))
                },
                onOpenBoard = {
                    if (entry.isResumed()) {
                        nav.previousBackStackEntry?.savedStateHandle?.set(OPEN_BOARD_KEY, true)
                        nav.popBackStack()
                    }
                },
            )
        }

        composable<ProjectEditorRoute> { entry ->
            val route = entry.toRoute<ProjectEditorRoute>()
            ProjectEditorRouteScreen(
                projectId = route.projectId?.let(::ProjectId),
                onClose = { if (entry.isResumed()) nav.popBackStack() },
            )
        }

        composable<AccountSettingsRoute> { entry ->
            AccountSettingsRouteScreen(onBack = { if (entry.isResumed()) nav.popBackStack() })
        }

        composable<AppSettingsRoute> { entry ->
            AppSettingsRouteScreen(
                onBack = { if (entry.isResumed()) nav.popBackStack() },
                onOpenNotifications = { if (entry.isResumed()) nav.navigate(NotificationsRoute) },
            )
        }

        composable<NotificationsRoute> { entry ->
            NotificationsRouteScreen(
                onBack = { if (entry.isResumed()) nav.popBackStack() },
                onOpenTask = { id -> if (entry.isResumed()) nav.navigate(TaskRoute(id.value)) },
                onOpenEvent = { id -> if (entry.isResumed()) nav.navigate(EventRoute(id.value)) },
            )
        }

        composable<MembersRoute> { entry ->
            MembersRouteScreen(onBack = { if (entry.isResumed()) nav.popBackStack() })
        }

        composable<FileViewerRoute> { entry ->
            val route = entry.toRoute<FileViewerRoute>()
            FileViewerRouteScreen(
                fileRef = route.fileRef,
                name = route.name,
                projectName = route.projectName,
                onBack = { if (entry.isResumed()) nav.popBackStack() },
            )
        }

    }
}
