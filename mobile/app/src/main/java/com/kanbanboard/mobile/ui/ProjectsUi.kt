package com.kanbanboard.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kanbanboard.mobile.model.Project
import com.kanbanboard.mobile.model.User
import com.kanbanboard.mobile.model.UserRole

@Composable
fun ProjectsScreen(projects: List<Project>, user: User?, selectedProjectId: Long,
    onProjectClick: (Long) -> Unit, modifier: Modifier = Modifier) {
    val mine = projects.filter { user != null && user.id in it.memberIds }
    val others = projects.filterNot { user != null && user.id in it.memberIds }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Мои проекты", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (mine.isEmpty()) item { Text("Пока нет проектов, в которых вы участвуете") }
        items(mine, key = { "mine-${it.id}" }) { project -> ProjectRow(project, project.id == selectedProjectId) { onProjectClick(project.id) } }
        item { Text("Все остальные проекты", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 14.dp)) }
        if (others.isEmpty()) item { Text("Других проектов в команде нет") }
        items(others, key = { "other-${it.id}" }) { project -> ProjectRow(project, false) { onProjectClick(project.id) } }
    }
}

@Composable
private fun ProjectRow(project: Project, selected: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(project.name, fontWeight = FontWeight.Bold)
            Text(project.description.ifBlank { "Описание пока не добавлено" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (selected) "Активный проект" else "Открыть проект →", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun ProjectDetailsScreen(project: Project?, teamMembers: List<User>, currentUser: User?,
    onBack: () -> Unit, onSelect: (Long) -> Unit, onSave: (Long, String, String) -> Unit,
    onSetMember: (Long, Long, Boolean) -> Unit) {
    Scaffold(topBar = { SimpleTopBar(project?.name ?: "Проект", onBack) }) { padding ->
        if (project == null) {
            Text("Проект не найден", Modifier.padding(padding).padding(20.dp))
        } else {
            var name by rememberSaveable(project.id) { mutableStateOf(project.name) }
            var description by rememberSaveable(project.id) { mutableStateOf(project.description) }
            var showSettings by rememberSaveable(project.id) { mutableStateOf(false) }
            val lead = currentUser?.role == UserRole.LEAD
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item { Text(project.description, style = MaterialTheme.typography.bodyLarge) }
                item { Button(onClick = { onSelect(project.id) }, modifier = Modifier.fillMaxWidth()) { Text("Открыть доску проекта") } }
                if (lead) {
                    item { OutlinedButton(onClick = { showSettings = !showSettings }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (showSettings) "Скрыть настройки проекта" else "Настройки проекта")
                    } }
                    if (showSettings) {
                        item { Text("Настройки проекта", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                        item { OutlinedTextField(name, { name = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth()) }
                        item { OutlinedTextField(description, { description = it }, label = { Text("Описание") }, modifier = Modifier.fillMaxWidth(), minLines = 3) }
                        item { OutlinedButton(onClick = { onSave(project.id, name, description) }, enabled = name.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()) { Text("Сохранить изменения") } }
                        item { Text("Участники проекта", style = MaterialTheme.typography.titleMedium) }
                        items(teamMembers, key = { it.id }) { member ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(member.name)
                                    Text(member.position, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = member.id in project.memberIds,
                                    onCheckedChange = { onSetMember(project.id, member.id, it) })
                            }
                        }
                    }
                }
            }
        }
    }
}
