package com.kanbanboard.mobile

import android.os.Bundle
import android.os.Build
import android.view.RoundedCorner
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.kanbanboard.mobile.data.ReminderScheduler
import com.kanbanboard.mobile.data.SharedPreferencesAppStorage
import com.kanbanboard.mobile.data.BackendClient
import com.kanbanboard.mobile.data.RemoteWorkspaceGateway
import com.kanbanboard.mobile.ui.AppViewModel
import com.kanbanboard.mobile.ui.KanbanApp
import com.kanbanboard.mobile.ui.LocalDeviceCornerShape
import com.kanbanboard.mobile.model.AppTheme
import com.kanbanboard.mobile.ui.theme.KanbanTheme

class MainActivity : ComponentActivity() {
    private val appViewModel by viewModels<AppViewModel> {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AppViewModel(storage = SharedPreferencesAppStorage(applicationContext),
                    remote = BuildConfig.API_BASE_URL.takeIf { it.isNotBlank() }
                        ?.let { RemoteWorkspaceGateway(BackendClient.create(it)) }) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReminderScheduler.ensureChannel(this)
        SharedPreferencesAppStorage(this).events().forEach { ReminderScheduler.schedule(this, it) }
        setContent {
            KanbanTheme(theme = appViewModel.uiState.theme) {
                DeviceRoundedSurface { KanbanApp(appViewModel) }
            }
        }
    }
}

@Composable
private fun DeviceRoundedSurface(content: @Composable () -> Unit) {
    val view = LocalView.current
    val orientation = LocalConfiguration.current.orientation
    val density = LocalDensity.current
    var corners by remember(orientation) { mutableStateOf(RoundedCornerShape(20.dp)) }
    LaunchedEffect(view, orientation) {
        withFrameNanos { }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.rootWindowInsets?.let { insets ->
                with(density) {
                    val fallbackRadius = 20.dp.roundToPx()
                    corners = RoundedCornerShape(
                        topStart = (insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius ?: fallbackRadius).toDp(),
                        topEnd = (insets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)?.radius ?: fallbackRadius).toDp(),
                        bottomEnd = (insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius ?: fallbackRadius).toDp(),
                        bottomStart = (insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: fallbackRadius).toDp(),
                    )
                }
            }
        }
    }
    CompositionLocalProvider(LocalDeviceCornerShape provides corners) {
        Box(Modifier.fillMaxSize().clip(corners).background(MaterialTheme.colorScheme.background)) { content() }
    }
}
