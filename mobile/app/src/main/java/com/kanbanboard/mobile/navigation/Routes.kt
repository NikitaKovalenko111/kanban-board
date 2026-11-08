package com.kanbanboard.mobile.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import kotlinx.serialization.Serializable

// Корневые экраны (без нижней панели).
@Serializable data object LoginRoute
@Serializable data object MainRoute
@Serializable data class TaskRoute(val taskId: String)
@Serializable data class TaskEditorRoute(val projectId: String, val taskId: String? = null)
@Serializable data class EventRoute(val eventId: String)
/** date — ISO-дата дня, с которого открыли создание события. */
@Serializable data class EventEditorRoute(val eventId: String? = null, val date: String? = null)

@Serializable data class ProjectRoute(val projectId: String)
@Serializable data class ProjectEditorRoute(val projectId: String? = null)
@Serializable data object AccountSettingsRoute
@Serializable data object AppSettingsRoute
@Serializable data object NotificationsRoute
@Serializable data object MembersRoute

/** Просмотр файла: ссылка в хранилище, имя и проект (для папки при сохранении через SAF). */
@Serializable data class FileViewerRoute(val fileRef: String, val name: String, val projectName: String)

/** Флаг в SavedStateHandle главного экрана: «после возврата открыть вкладку доски». */
const val OPEN_BOARD_KEY = "open_board"

// Вкладки нижней панели.
@Serializable data object BoardTab
@Serializable data object ScheduleTab
@Serializable data object ProjectsTab
@Serializable data object HistoryTab
@Serializable data object ProfileTab

/**
 * MOB-05: экран реагирует на навигацию, только пока он активен. Во время анимации перехода
 * исходный экран уже не RESUMED, поэтому повторный тап не откроет второй экран.
 */
fun NavBackStackEntry.isResumed(): Boolean = lifecycle.currentState == Lifecycle.State.RESUMED
