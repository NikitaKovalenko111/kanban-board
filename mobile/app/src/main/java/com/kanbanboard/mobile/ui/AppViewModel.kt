package com.kanbanboard.mobile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.kanbanboard.mobile.data.FakeKanbanRepository
import com.kanbanboard.mobile.data.KanbanRepository
import com.kanbanboard.mobile.model.Comment
import com.kanbanboard.mobile.model.Task
import com.kanbanboard.mobile.model.TaskStatus
import com.kanbanboard.mobile.model.User

data class AppUiState(
    val user: User? = null,
    val tasks: List<Task> = emptyList(),
    val loginError: String? = null,
)

class AppViewModel(
    private val repository: KanbanRepository = FakeKanbanRepository(),
) : ViewModel() {
    var uiState by mutableStateOf(AppUiState(tasks = repository.tasks()))
        private set

    val events get() = repository.events()

    fun lessons(group: String) = repository.lessons(group)

    fun login(id: String) {
        val user = repository.login(id)
        uiState = if (user == null) {
            uiState.copy(loginError = "Введите личный ID")
        } else {
            uiState.copy(user = user, loginError = null)
        }
    }

    fun logout() {
        uiState = uiState.copy(user = null, loginError = null)
    }

    fun moveTask(taskId: Long, status: TaskStatus) {
        uiState = uiState.copy(tasks = uiState.tasks.map { task ->
            if (task.id == taskId) task.copy(status = status) else task
        })
    }

    fun addComment(taskId: Long, text: String) {
        val normalized = text.trim()
        if (normalized.isEmpty()) return
        uiState = uiState.copy(tasks = uiState.tasks.map { task ->
            if (task.id == taskId) {
                task.copy(comments = task.comments + Comment(
                    id = "local-${System.currentTimeMillis()}",
                    author = uiState.user?.name ?: "Пользователь",
                    text = normalized,
                    time = "Только что",
                ))
            } else task
        })
    }
}
