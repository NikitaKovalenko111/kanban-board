package com.kanbanboard.mobile.core.designsystem

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val imageCache = ConcurrentHashMap<String, ImageBitmap>()

/**
 * Загружает картинку из файла на устройстве (фото профиля, обои, вложение) в фоне,
 * с уменьшением до [maxSidePx] по большей стороне, чтобы не тратить память на огромные фото.
 * Пока картинка грузится или если файла нет — возвращает null.
 */
@Composable
fun rememberLocalImage(path: String, maxSidePx: Int = 2048): ImageBitmap? {
    val key = "$path@$maxSidePx@${File(path).lastModified()}"
    var image by remember(key) { mutableStateOf(imageCache[key]) }
    LaunchedEffect(key) {
        if (image == null) {
            image = withContext(Dispatchers.IO) { decodeSampled(path, maxSidePx) }?.also { imageCache[key] = it }
        }
    }
    return image
}

fun decodeSampled(path: String, maxSidePx: Int): ImageBitmap? {
    val file = File(path)
    if (!file.isFile) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSidePx) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(path, options)?.asImageBitmap()
}
