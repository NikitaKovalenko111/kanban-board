package com.kanbanboard.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AppBackground = Color(0xFFF5F7FB)
val AppSurface = Color(0xFFFFFFFF)
val AppText = Color(0xFF20242C)
val AppMuted = Color(0xFF77808F)
val AppPrimary = Color(0xFF6576DF)
val AppPrimaryDark = Color(0xFF5363C7)
val AppBorder = Color(0xFFE5E9F0)
val AppDanger = Color(0xFFC65F68)
val AppSuccess = Color(0xFF58A779)

private val LightColors = lightColorScheme(
    primary = AppPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF0FF),
    onPrimaryContainer = Color(0xFF5261C0),
    background = AppBackground,
    onBackground = AppText,
    surface = AppSurface,
    onSurface = AppText,
    surfaceVariant = Color(0xFFF0F2F8),
    onSurfaceVariant = AppMuted,
    outline = AppBorder,
    error = AppDanger,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAEB7FF),
    background = Color(0xFF171920),
    surface = Color(0xFF20232B),
    onSurface = Color(0xFFF2F3F7),
)

@Composable
fun KanbanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
