package com.kanbanboard.mobile.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kanbanboard.mobile.core.data.settings.ThemeMode
import com.kanbanboard.mobile.core.model.LessonKind
import com.kanbanboard.mobile.core.model.TaskPriority

/*
 * Тема приложения по Material Design 3: индиго-палитра (как в веб-клиенте), стандартные
 * цветовые роли M3 и типографическая шкала M3. Экраны берут цвета только из
 * MaterialTheme.colorScheme и MaterialTheme.ext — без Color(0x…) в коде экранов.
 *
 * Тёмная тема нейтральная (серо-чёрная, без зелёного и синего оттенка), AMOLED — чистый #000000.
 */

private val Indigo = Color(0xFF6576DF)

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E4FF),
    onPrimaryContainer = Color(0xFF1A2478),
    secondary = Color(0xFF5B5D72),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E1F9),
    onSecondaryContainer = Color(0xFF181A2C),
    tertiary = Color(0xFF8B5A2B),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFF7F8FC),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE3E2EC),
    onSurfaceVariant = Color(0xFF5E6070),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F2F8),
    surfaceContainer = Color(0xFFEBECF3),
    surfaceContainerHigh = Color(0xFFE5E6EE),
    surfaceContainerHighest = Color(0xFFDFE0E8),
    outline = Color(0xFF777987),
    outlineVariant = Color(0xFFC7C7D3),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

/** Нейтральные серые поверхности без цветового оттенка (замечание спринта 3). */
private val DarkColors = darkColorScheme(
    primary = Color(0xFFBCC3FF),
    onPrimary = Color(0xFF1F2A8A),
    primaryContainer = Color(0xFF3A47A8),
    onPrimaryContainer = Color(0xFFE0E4FF),
    secondary = Color(0xFFC6C5DD),
    onSecondary = Color(0xFF2E2F42),
    secondaryContainer = Color(0xFF45465A),
    onSecondaryContainer = Color(0xFFE2E1F9),
    tertiary = Color(0xFFF1BB8A),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE6E6E6),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE6E6E6),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFC4C4C8),
    surfaceContainerLowest = Color(0xFF0C0C0C),
    surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF1E1E1E),
    surfaceContainerHigh = Color(0xFF282828),
    surfaceContainerHighest = Color(0xFF333333),
    outline = Color(0xFF8E8E93),
    outlineVariant = Color(0xFF3A3A3C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** MOB-07: чистый #000000; карточки отделяются едва заметным серым. */
private val AmoledColors: ColorScheme = DarkColors.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0E0E0E),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222),
    surfaceVariant = Color(0xFF1A1A1A),
)

/** Цвета, которых нет среди ролей M3: статусы и виды занятий. */
@Immutable
data class AppColors(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val neutral: Color,
    /** Пары: лекции — индиго, практики — оранжевые, лабораторные — зелёные. */
    val lecture: Color,
    val practice: Color,
    val lab: Color,
)

private val LightExt = AppColors(
    success = Color(0xFF2E7D4F),
    warning = Color(0xFFB0640C),
    danger = Color(0xFFBA1A1A),
    neutral = Color(0xFF6E7080),
    lecture = Indigo,
    practice = Color(0xFFD9822B),
    lab = Color(0xFF2E9E78),
)

private val DarkExt = AppColors(
    success = Color(0xFF81C995),
    warning = Color(0xFFF2B36B),
    danger = Color(0xFFFFB4AB),
    neutral = Color(0xFFA0A0A8),
    lecture = Color(0xFFBCC3FF),
    practice = Color(0xFFF2B36B),
    lab = Color(0xFF7DD3B0),
)

private val LocalAppColors = staticCompositionLocalOf { LightExt }

/** Дополнительные цвета приложения: MaterialTheme.ext.success и т. д. */
val MaterialTheme.ext: AppColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current

/** Скругления по шкале M3. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val base = Typography()

/** Шкала M3 без изменений размеров; заголовкам чуть больше веса. */
private val AppTypography = base.copy(
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK, ThemeMode.AMOLED -> true
}

/**
 * [transparentBackground] — фон экранов прозрачный (под ними нарисованы обои, см. MainActivity).
 */
@Composable
fun KanbanTheme(mode: ThemeMode, transparentBackground: Boolean = false, content: @Composable () -> Unit) {
    val dark = mode.isDark()
    val base = when {
        mode == ThemeMode.AMOLED -> AmoledColors
        dark -> DarkColors
        else -> LightColors
    }
    val colors = if (transparentBackground) base.copy(background = Color.Transparent) else base
    CompositionLocalProvider(LocalAppColors provides if (dark) DarkExt else LightExt) {
        MaterialTheme(colorScheme = colors, shapes = AppShapes, typography = AppTypography, content = content)
    }
}

@Composable
@ReadOnlyComposable
fun TaskPriority.color(): Color = when (this) {
    TaskPriority.LOW -> MaterialTheme.ext.neutral
    TaskPriority.NORMAL -> MaterialTheme.colorScheme.primary
    TaskPriority.HIGH -> MaterialTheme.ext.danger
}

@Composable
@ReadOnlyComposable
fun LessonKind.color(): Color = when (this) {
    LessonKind.LECTURE -> MaterialTheme.ext.lecture
    LessonKind.PRACTICE -> MaterialTheme.ext.practice
    LessonKind.LAB -> MaterialTheme.ext.lab
}

/** Палитра цветов для колонок доски и типов событий: 24 цвета, различимых в обеих темах. */
val AppPalette: List<Long> = listOf(
    0xFF6576DF, 0xFF8B6AC8, 0xFFB15BC4, 0xFFEC4899, 0xFFC65F68, 0xFFEF4444,
    0xFFF97316, 0xFFD9822B, 0xFFEAB308, 0xFF84CC16, 0xFF58A779, 0xFF2E9E78,
    0xFF14B8A6, 0xFF06B6D4, 0xFF3B82F6, 0xFF2563EB, 0xFF6366F1, 0xFF7C3AED,
    0xFFA16207, 0xFF78716C, 0xFF8A94A6, 0xFF0F766E, 0xFF9F1239, 0xFF475569,
)
