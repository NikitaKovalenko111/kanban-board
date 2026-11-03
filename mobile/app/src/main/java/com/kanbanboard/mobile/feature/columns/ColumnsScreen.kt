package com.kanbanboard.mobile.feature.columns

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kanbanboard.mobile.core.data.BoardRepository
import com.kanbanboard.mobile.core.designsystem.AppButton
import com.kanbanboard.mobile.core.designsystem.AppButtonKind
import com.kanbanboard.mobile.core.designsystem.AppPalette
import com.kanbanboard.mobile.core.designsystem.AppTopBar
import com.kanbanboard.mobile.core.designsystem.BackButton
import com.kanbanboard.mobile.core.designsystem.Panel
import com.kanbanboard.mobile.core.model.ColumnDraft
import com.kanbanboard.mobile.core.model.ColumnId
import com.kanbanboard.mobile.core.model.ColumnKind
import com.kanbanboard.mobile.core.model.ProjectId
import com.kanbanboard.mobile.di.LocalAppContainer
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Колонка в редакторе; key стабилен для списка даже у новых колонок без id. */
data class EditableColumn(val key: String, val id: ColumnId?, val title: String, val color: Long)

data class ColumnsUiState(
    val loading: Boolean = true,
    val columns: List<EditableColumn> = emptyList(),
    val saving: Boolean = false,
    val error: String? = null,
)

/**
 * Настройка колонок (спринт 3): у колонки только название и цвет; можно добавлять свои этапы
 * («Pull request»), менять порядок стрелками и удалять. Роль колонки задаёт её место:
 * первая — новые задачи, предпоследняя — проверка, последняя — выполненные.
 */
class ColumnsViewModel(private val projectId: ProjectId, private val board: BoardRepository) : ViewModel() {
    private val _state = MutableStateFlow(ColumnsUiState())
    val state: StateFlow<ColumnsUiState> = _state.asStateFlow()
    private val _saved = Channel<Unit>(Channel.CONFLATED)
    val saved: Flow<Unit> = _saved.receiveAsFlow()

    init {
        viewModelScope.launch {
            val columns = board.columns(projectId).first()
            _state.update { s ->
                s.copy(loading = false, columns = columns.map { EditableColumn(it.id.value, it.id, it.title, it.color) })
            }
        }
    }

    private fun edit(transform: (MutableList<EditableColumn>) -> Unit) = _state.update { s ->
        s.copy(columns = s.columns.toMutableList().also(transform), error = null)
    }

    fun rename(key: String, title: String) = edit { list ->
        val i = list.indexOfFirst { it.key == key }
        if (i >= 0) list[i] = list[i].copy(title = title.take(40))
    }

    fun recolor(key: String, color: Long) = edit { list ->
        val i = list.indexOfFirst { it.key == key }
        if (i >= 0) list[i] = list[i].copy(color = color)
    }

    fun move(key: String, delta: Int) = edit { list ->
        val i = list.indexOfFirst { it.key == key }
        val j = i + delta
        if (i >= 0 && j in list.indices) list.add(j, list.removeAt(i))
    }

    fun remove(key: String) = edit { list -> list.removeAll { it.key == key } }

    /** Новый этап добавляется перед «проверкой», чтобы не сдвинуть роли последних колонок. */
    fun add() = edit { list ->
        val index = (list.size - 2).coerceAtLeast(0)
        list.add(index, EditableColumn(UUID.randomUUID().toString(), null, "", AppPalette[index % AppPalette.size]))
    }

    fun save() {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            board.saveColumns(projectId, _state.value.columns.map { ColumnDraft(it.id, it.title, it.color) })
                .onSuccess { _saved.trySend(Unit) }
                .onFailure { e -> _state.update { it.copy(saving = false, error = e.message) } }
        }
    }
}

@Composable
fun ColumnsRouteScreen(projectId: ProjectId, onClose: () -> Unit) {
    val c = LocalAppContainer.current
    val vm: ColumnsViewModel = viewModel { ColumnsViewModel(projectId, c.boardRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(vm) { vm.saved.collect { currentOnClose() } }
    ColumnsScreen(state, vm, onClose)
}

@Composable
private fun ColumnsScreen(state: ColumnsUiState, vm: ColumnsViewModel, onClose: () -> Unit) {
    var colorFor by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Колонки доски",
                navigation = { BackButton(onClose) },
                actions = {
                    TextButton(onClick = vm::save, enabled = !state.loading && !state.saving) { Text("Сохранить") }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(state.columns, key = { _, item -> item.key }) { index, column ->
                Panel(contentPadding = PaddingValues(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Нажатие на кружок — выбор цвета колонки.
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable { colorFor = column.key }
                                .semantics { contentDescription = "Цвет колонки" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(24.dp).clip(CircleShape).background(Color(column.color)))
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = column.title,
                            onValueChange = { vm.rename(column.key, it) },
                            placeholder = { Text("Название") },
                            singleLine = true,
                            supportingText = roleHint(index, state.columns.size)?.let { hint -> { Text(hint) } },
                            modifier = Modifier.weight(1f),
                        )
                        Column {
                            IconButton(onClick = { vm.move(column.key, -1) }, enabled = index > 0) {
                                Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Выше")
                            }
                            IconButton(onClick = { vm.move(column.key, +1) }, enabled = index < state.columns.lastIndex) {
                                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Ниже")
                            }
                        }
                        IconButton(onClick = { vm.remove(column.key) }, enabled = state.columns.size > ColumnKind.MIN_COLUMNS) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Удалить колонку")
                        }
                    }
                }
            }
            item {
                AppButton(
                    "Добавить этап",
                    onClick = vm::add,
                    kind = AppButtonKind.SECONDARY,
                    icon = Icons.Filled.Add,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            state.error?.let { error ->
                item { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }

    colorFor?.let { key ->
        val column = state.columns.firstOrNull { it.key == key }
        if (column == null) {
            colorFor = null
        } else {
            ColorPickerDialog(
                selected = column.color,
                onDismiss = { colorFor = null },
                onPick = { vm.recolor(key, it); colorFor = null },
            )
        }
    }
}

/** Подпись под первой и последними колонками: их роль в процессе. */
private fun roleHint(index: Int, count: Int): String? = when (ColumnKind.forPosition(index, count)) {
    ColumnKind.BACKLOG -> "Новые задачи"
    ColumnKind.REVIEW -> "Проверка капитаном"
    ColumnKind.DONE -> "Выполненные"
    ColumnKind.IN_PROGRESS -> null
}

/** Выбор цвета из общей палитры приложения. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(selected: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Цвет") },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppPalette.forEachIndexed { index, option ->
                    val isSelected = option == selected
                    val swatch = Color(option)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .then(
                                if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier,
                            )
                            .semantics {
                                contentDescription = "Цвет ${index + 1}"
                                this.selected = isSelected
                            }
                            .clickable { onPick(option) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = if (swatch.luminance() > 0.5f) Color.Black else Color.White,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
    )
}
