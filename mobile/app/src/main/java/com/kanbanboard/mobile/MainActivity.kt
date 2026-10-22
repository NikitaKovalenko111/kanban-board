package com.kanbanboard.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kanbanboard.mobile.data.ReminderScheduler
import com.kanbanboard.mobile.data.SharedPreferencesAppStorage
import com.kanbanboard.mobile.ui.AppViewModel
import com.kanbanboard.mobile.ui.KanbanApp
import com.kanbanboard.mobile.model.AppTheme
import com.kanbanboard.mobile.ui.theme.KanbanTheme

class MainActivity : ComponentActivity() {
    private val appViewModel by viewModels<AppViewModel> {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AppViewModel(storage = SharedPreferencesAppStorage(applicationContext)) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReminderScheduler.ensureChannel(this)
        SharedPreferencesAppStorage(this).events().forEach { ReminderScheduler.schedule(this, it) }
        setContent {
            KanbanTheme(darkTheme = appViewModel.uiState.theme == AppTheme.DARK) {
                KanbanApp(appViewModel)
            }
        }
    }
}
