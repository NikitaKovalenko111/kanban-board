package com.kanbanboard.mobile.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview

/*
 * Общие компоненты. Внутри — стандартные компоненты Material 3 (Card, FilterChip, Button,
 * TopAppBar…), снаружи — небольшой API, чтобы экраны выглядели одинаково и не дублировали код.
 */

// ---------- Текст ----------

/** Второстепенная подпись (bodySmall, onSurfaceVariant): даты, счётчики, метаданные. */
@Composable
fun Caption(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines: Int = 1,
) {
    Text(
        text,
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.bodySmall,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

// ---------- Карточки ----------

/**
 * Карточка M3. Обычная — заполненная (Card), выделенная [highlighted] — с рамкой цвета primary
 * (моя задача, активный проект). Кликабельная, если передан [onClick].
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val body: @Composable ColumnScope.() -> Unit = { Column(Modifier.padding(contentPadding), content = content) }
    if (highlighted) {
        val border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary))
        if (onClick != null) {
            OutlinedCard(onClick = onClick, modifier = modifier, border = border, content = body)
        } else {
            OutlinedCard(modifier = modifier, border = border, content = body)
        }
    } else {
        val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        if (onClick != null) {
            Card(onClick = onClick, modifier = modifier, colors = colors, content = body)
        } else {
            Card(modifier = modifier, colors = colors, content = body)
        }
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, color = MaterialTheme.colorScheme.outlineVariant)
}

/** Строка «Подпись — значение» для карточек-описаний. */
@Composable
fun SpecRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(120.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, modifier = Modifier.weight(1f))
    }
}

// ---------- Метки и чипы ----------

/** Некликабельная метка (статус, приоритет, роль): labelMedium на тонированном фоне. */
@Composable
fun Tag(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    filled: Boolean = false,
    dot: Boolean = false,
) {
    val content = if (filled) MaterialTheme.colorScheme.onPrimary else color
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(if (filled) MaterialTheme.colorScheme.primary else color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(content))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = content, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** Цветная точка (колонка, тип события). */
@Composable
fun ColorDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

/** Чип-переключатель — стандартный FilterChip M3 (с галочкой у выбранного). */
@Composable
fun AppChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dotColor: Color? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, maxLines = 1) },
        modifier = modifier,
        leadingIcon = dotColor?.let { color -> { ColorDot(color) } },
        colors = FilterChipDefaults.filterChipColors(),
    )
}

// ---------- Кнопки ----------

enum class AppButtonKind { PRIMARY, SECONDARY, GHOST, DANGER }

/** Кнопки M3: PRIMARY — Button, SECONDARY — OutlinedButton, GHOST — TextButton, DANGER — OutlinedButton цвета error. */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: AppButtonKind = AppButtonKind.PRIMARY,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    val label: @Composable RowScope.() -> Unit = {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, maxLines = 1)
        }
    }
    when (kind) {
        AppButtonKind.PRIMARY ->
            Button(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled && !loading, content = label)
        AppButtonKind.SECONDARY ->
            OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled && !loading, content = label)
        AppButtonKind.DANGER -> OutlinedButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 48.dp),
            enabled = enabled && !loading,
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            content = label,
        )
        AppButtonKind.GHOST ->
            TextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled && !loading, content = label)
    }
}

@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
    }
}

// ---------- Каркас экранов ----------

/**
 * Верхняя панель — стандартный TopAppBar M3. Прозрачная, чтобы под ней были видны обои.
 * [titleContent] заменяет заголовок (например, переключатель проекта на доске).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    titleContent: (@Composable () -> Unit)? = null,
) {
    TopAppBar(
        title = {
            if (titleContent != null) titleContent()
            else Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        modifier = modifier,
        navigationIcon = { navigation?.invoke() },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
    )
}

/** Заголовок секции (titleSmall) со счётчиком и действием справа. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text("$count", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    text: String? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (text != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---------- Люди ----------

/** Круглый аватар: фото профиля (если есть) или инициалы на primaryContainer. */
@Composable
fun UserAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    highlighted: Boolean = false,
    photo: String? = null,
) {
    val image = photo?.let { rememberLocalImage(it, maxSidePx = 256) }
    if (image != null) {
        androidx.compose.foundation.Image(
            bitmap = image,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape),
        )
        return
    }
    val initials = name.split(' ').filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    // Привязываем буквы к диаметру, чтобы системный масштаб шрифта не обрезал инициалы.
    val initialsSize = with(LocalDensity.current) { (size * 0.4f).toSp() }
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                initials,
                style = TextStyle(
                    fontSize = initialsSize,
                    fontWeight = FontWeight.Medium,
                    lineHeight = initialsSize,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
                ),
                maxLines = 1,
                softWrap = false,
                color = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Preview(name = "Аватары", showBackground = true)
@Preview(name = "Аватары — шрифт 200%", showBackground = true, fontScale = 2f)
@Composable
private fun UserAvatarPreview() {
    MaterialTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar("Самир Миргазямов", size = 20.dp)
            UserAvatar("Никита Коваленко", size = 24.dp)
            UserAvatar("Ю", size = 28.dp)
            UserAvatar("", size = 40.dp)
            UserAvatar("Самир Миргазямов", size = 64.dp, highlighted = true)
        }
    }
}
