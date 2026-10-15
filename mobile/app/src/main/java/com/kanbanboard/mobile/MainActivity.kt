package com.kanbanboard.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kanbanboard.mobile.ui.KanbanApp
import com.kanbanboard.mobile.ui.theme.KanbanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KanbanTheme {
                KanbanApp()
            }
        }
    }
}
