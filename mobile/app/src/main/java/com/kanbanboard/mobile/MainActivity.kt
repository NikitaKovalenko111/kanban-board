package com.kanbanboard.mobile

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kanbanboard.mobile.core.data.SessionRepository
import com.kanbanboard.mobile.core.data.settings.SettingsRepository
import com.kanbanboard.mobile.core.data.settings.ThemeMode
import com.kanbanboard.mobile.core.designsystem.KanbanTheme
import com.kanbanboard.mobile.core.designsystem.isDark
import com.kanbanboard.mobile.core.designsystem.rememberLocalImage
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import com.kanbanboard.mobile.di.LocalAppContainer
import com.kanbanboard.mobile.navigation.AppNavHost
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class RootState(
    val ready: Boolean = false,
    val loggedIn: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Своя картинка-обои (путь к файлу на устройстве) или null. */
    val wallpaper: String? = null,
)

class RootViewModel(sessions: SessionRepository, settings: SettingsRepository) : ViewModel() {
    val state: StateFlow<RootState> = combine(sessions.session, settings.settings) { session, prefs ->
        RootState(ready = true, loggedIn = session != null, theme = prefs.theme, wallpaper = prefs.wallpaper)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RootState())
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as KanbanApplication).container
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                val root: RootViewModel = viewModel {
                    RootViewModel(container.sessionRepository, container.settingsRepository)
                }
                val state by root.state.collectAsStateWithLifecycle()
                val dark = state.theme.isDark()

                // Иконки статус-бара следуют теме приложения, а не системной.
                DisposableEffect(dark) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                        navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    )
                    onDispose { }
                }

                KanbanTheme(state.theme) {
                    val background = MaterialTheme.colorScheme.background
                    val wallpaper = state.wallpaper?.let { rememberLocalImage(it) }
                    Box(Modifier.fillMaxSize().background(background)) {
                        if (wallpaper != null) {
                            // Обои под интерфейсом + полупрозрачная подложка цвета фона для читаемости текста.
                            Image(
                                bitmap = wallpaper,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Box(Modifier.fillMaxSize().background(background.copy(alpha = 0.78f)))
                        }
                        // С обоями фон экранов прозрачный, иначе Scaffold закроет картинку.
                        KanbanTheme(state.theme, transparentBackground = wallpaper != null) {
                            if (state.ready) AppNavHost(loggedIn = state.loggedIn)
                        }
                    }
                }
            }
        }
    }
}
