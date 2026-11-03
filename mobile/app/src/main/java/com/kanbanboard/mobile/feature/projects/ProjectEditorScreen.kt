package com.kanbanboard.mobile.feature.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kanbanboard.mobile.core.data.ProjectRepository
import com.kanbanboard.mobile.core.data.SessionRepository
import com.kanbanboard.mobile.core.data.TeamRepository
import com.kanbanboard.mobile.core.designsystem.BackButton
import com.kanbanboard.mobile.core.designsystem.AppButton
import com.kanbanboard.mobile.core.designsystem.AppTopBar
import com.kanbanboard.mobile.core.designsystem.Caption
import com.kanbanboard.mobile.core.designsystem.Panel
import com.kanbanboard.mobile.core.designsystem.SectionHeader
import com.kanbanboard.mobile.core.designsystem.UserAvatar
import com.kanbanboard.mobile.core.designsystem.ext
import com.kanbanboard.mobile.core.model.ProjectDraft
import com.kanbanboard.mobile.core.model.ProjectId
import com.kanbanboard.mobile.core.model.User
import com.kanbanboard.mobile.core.model.UserId
import com.kanbanboard.mobile.core.policy.ProjectPolicy
import com.kanbanboard.mobile.di.LocalAppContainer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectEditorUiState(
    val loading: Boolean = true,
    val isEdit: Boolean = false,
    val allowed: Boolean = true,
    val name: String = "",
    val description: String = "",
    val users: List<User> = emptyList(),
    /** Кто в проекте и с какой ролью. */
    val roles: Map<UserId, String> = emptyMap(),
    val saving: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean get() = allowed && !loading && !saving && name.isNotBlank()
}

class ProjectEditorViewModel(
    private val projectId: ProjectId?,
    private val sessions: SessionRepository,
    private val team: TeamRepository,
    private val projects: ProjectRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ProjectEditorUiState(isEdit = projectId != null))
    val state: StateFlow<ProjectEditorUiState> = _state.asStateFlow()
    private val _saved = Channel<Unit>(Channel.CONFLATED)
    val saved: Flow<Unit> = _saved.receiveAsFlow()

    init {
        viewModelScope.launch {
            val session = sessions.session.value
            val users = team.members.first().map { it.user }.filter { it.isActive }.sortedBy { it.fullName }
            val project = projectId?.let { id -> projects.project(id).first() }
            _state.update {
                it.copy(
                    loading = false,
                    allowed = session != null && ProjectPolicy.canManage(session),
                    users = users,
                    name = project?.name.orEmpty(),
                    description = project?.description.orEmpty(),
                    // Новый проект: капитан сразу в составе.
                    roles = project?.members ?: session?.let { mapOf(it.user.id to "Капитан") }.orEmpty(),
                    error = if (projectId != null && project == null) "Проект не найден" else null,
                )
            }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value.take(80), error = null) }
    fun setDescription(value: String) = _state.update { it.copy(description = value.take(2000)) }

    fun setMember(userId: UserId, included: Boolean) = _state.update { s ->
        s.copy(roles = if (included) s.roles + (userId to s.roles[userId].orEmpty()) else s.roles - userId, error = null)
    }

    fun setRole(userId: UserId, role: String) = _state.update { s ->
        if (userId !in s.roles) s else s.copy(roles = s.roles + (userId to role.take(60)))
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(saving = true, error = null) }
        val draft = ProjectDraft(s.name, s.description, s.roles)
        viewModelScope.launch {
            val result = if (projectId == null) projects.createProject(draft).map { } else projects.updateProject(projectId, draft)
            result
                .onSuccess { _saved.trySend(Unit) }
                .onFailure { e -> _state.update { it.copy(saving = false, error = e.message) } }
        }
    }
}

@Composable
fun ProjectEditorRouteScreen(projectId: ProjectId?, onClose: () -> Unit) {
    val c = LocalAppContainer.current
    val vm: ProjectEditorViewModel = viewModel {
        ProjectEditorViewModel(projectId, c.sessionRepository, c.teamRepository, c.projectRepository)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(vm) { vm.saved.collect { currentOnClose() } }

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (state.isEdit) "Настройки проекта" else "Новый проект",
                navigation = { BackButton(onClose) },
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Scaffold
        }
        if (!state.allowed) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Caption("Настраивать проекты может только капитан", maxLines = 2)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = vm::setName,
                    label = { Text("Название") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = state.description,
                    onValueChange = vm::setDescription,
                    label = { Text("Описание") },
                    minLines = 3,
                    shape = MaterialTheme.shapes.small,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                )
            }
            item {
                Column {
                    SectionHeader("Состав и роли", count = state.roles.size, modifier = Modifier.padding(top = 6.dp))
                    Caption("Например: backend, дизайн, тестирование", maxLines = 2)
                }
            }
            items(state.users, key = { it.id.value }) { user ->
                val included = user.id in state.roles
                Panel(highlighted = included) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(user.fullName, size = 32.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(user.fullName, style = MaterialTheme.typography.bodyLarge)
                            Caption("@${user.login} · гр. ${user.studyGroup.code}")
                        }
                        Checkbox(
                            checked = included,
                            onCheckedChange = { vm.setMember(user.id, it) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                                checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                    if (included) {
                        OutlinedTextField(
                            value = state.roles[user.id].orEmpty(),
                            onValueChange = { vm.setRole(user.id, it) },
                            placeholder = { Text("Роль в проекте") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                }
            }
            state.error?.let { error ->
                item { Caption("! $error", color = MaterialTheme.ext.danger, maxLines = 3) }
            }
            item {
                AppButton(
                    if (state.isEdit) "Сохранить" else "Создать проект",
                    onClick = vm::save,
                    enabled = state.canSave,
                    loading = state.saving,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
    }
}
