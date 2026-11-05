package com.kanbanboard.mobile.feature.files

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kanbanboard.mobile.core.designsystem.AppButton
import com.kanbanboard.mobile.core.designsystem.AppButtonKind
import com.kanbanboard.mobile.core.designsystem.AppTopBar
import com.kanbanboard.mobile.core.designsystem.BackButton
import com.kanbanboard.mobile.core.designsystem.EmptyState
import com.kanbanboard.mobile.core.designsystem.rememberLocalImage
import com.kanbanboard.mobile.core.files.FileStore
import com.kanbanboard.mobile.di.LocalAppContainer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TEXT_PREVIEW_LIMIT = 200 * 1024
private const val PDF_PAGE_LIMIT = 50

private enum class Preview { IMAGE, PDF, TEXT, OTHER }

private fun previewOf(name: String): Preview = when (name.substringAfterLast('.', "").lowercase()) {
    "png", "jpg", "jpeg", "webp", "gif", "bmp", "heic" -> Preview.IMAGE
    "pdf" -> Preview.PDF
    "txt", "md", "csv", "json", "xml", "log", "kt", "java", "yml", "yaml", "html", "css", "js", "ts" -> Preview.TEXT
    else -> Preview.OTHER
}

/**
 * Просмотр файла внутри приложения: изображения, PDF (постранично), текст.
 * Остальные типы открываются во внешнем приложении через FileProvider.
 * «Сохранить» кладёт копию в выбранную папку, в подпапку с именем проекта.
 */
@Composable
fun FileViewerRouteScreen(fileRef: String, name: String, projectName: String, onBack: () -> Unit) {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val settings by c.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    val load by produceState<Pair<Boolean, String?>>(false to null, fileRef) {
        value = true to runCatching { c.resolveFile(fileRef, name) }.getOrNull()
    }
    val resolvedRef = load.second
    val file = remember(resolvedRef) { resolvedRef?.let(c.fileStore::file)?.takeIf { it.exists() } }

    fun save(tree: Uri) {
        scope.launch {
            runCatching { c.fileStore.exportToFolder(resolvedRef ?: fileRef, tree, projectName) }
                .onSuccess { snackbar.showSnackbar("Сохранено: $projectName/$it") }
                .onFailure { snackbar.showSnackbar(it.message ?: "Не удалось сохранить файл") }
        }
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            scope.launch { c.settingsRepository.setSaveFolder(uri.toString()) }
            save(uri)
        }
    }

    fun openExternally() {
        val f = file ?: return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, FileStore.mimeType(f.name))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(Intent.createChooser(intent, name))
        } catch (_: ActivityNotFoundException) {
            scope.launch { snackbar.showSnackbar("Нет приложения для этого типа файла") }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            AppTopBar(
                title = name,
                navigation = { BackButton(onBack) },
                actions = {
                    if (file != null) {
                        IconButton(onClick = ::openExternally) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Открыть в другом приложении")
                        }
                        IconButton(onClick = {
                            val folder = settings?.saveFolder?.let(Uri::parse)
                            if (folder != null) save(folder) else pickFolder.launch(null)
                        }) {
                            Icon(Icons.Outlined.Download, contentDescription = "Сохранить")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        if (!load.first) {
            Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        if (file == null) {
            EmptyState(
                Icons.Outlined.InsertDriveFile,
                "Файла нет на устройстве",
                modifier,
                "Не удалось загрузить файл.",
            )
            return@Scaffold
        }
        when (previewOf(file.name)) {
            Preview.IMAGE -> ImagePreview(file, modifier)
            Preview.PDF -> PdfPreview(file, modifier)
            Preview.TEXT -> TextPreview(file, modifier)
            Preview.OTHER -> Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyState(
                    Icons.Outlined.InsertDriveFile,
                    "Предпросмотр недоступен",
                    Modifier.weight(1f),
                    "${file.name} · ${com.kanbanboard.mobile.core.time.Formats.fileSize(file.length())}",
                )
                AppButton(
                    "Открыть в другом приложении",
                    onClick = ::openExternally,
                    kind = AppButtonKind.SECONDARY,
                    icon = Icons.AutoMirrored.Outlined.OpenInNew,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ImagePreview(file: File, modifier: Modifier) {
    val image = rememberLocalImage(file.path)
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest), contentAlignment = Alignment.Center) {
        if (image == null) {
            CircularProgressIndicator()
        } else {
            Image(image, contentDescription = file.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun TextPreview(file: File, modifier: Modifier) {
    val text by produceState<String?>(null, file) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                file.inputStream().use { input ->
                    val bytes = input.readNBytesCompat(TEXT_PREVIEW_LIMIT)
                    val body = String(bytes, Charsets.UTF_8)
                    if (file.length() > TEXT_PREVIEW_LIMIT) "$body\n\n… файл обрезан для предпросмотра" else body
                }
            }.getOrElse { "Не удалось прочитать файл" }
        }
    }
    val content = text
    if (content == null) {
        Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    SelectionContainer(modifier) {
        Text(
            content,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(16.dp),
        )
    }
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val buffer = ByteArray(limit)
    var total = 0
    while (total < limit) {
        val read = read(buffer, total, limit - total)
        if (read < 0) break
        total += read
    }
    return buffer.copyOf(total)
}

@Composable
private fun PdfPreview(file: File, modifier: Modifier) {
    val pages by produceState<List<Bitmap>?>(null, file) {
        value = withContext(Dispatchers.IO) { renderPdf(file) }
    }
    val list = pages
    when {
        list == null -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        list.isEmpty() -> EmptyState(Icons.Outlined.InsertDriveFile, "Не удалось открыть PDF", modifier)
        else -> LazyColumn(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(list) { bitmap ->
                Image(
                    bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small),
                )
            }
        }
    }
}

/** Рендер первых [PDF_PAGE_LIMIT] страниц в ширину ~1080 px. */
private fun renderPdf(file: File): List<Bitmap> = runCatching {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        PdfRenderer(fd).use { renderer ->
            (0 until minOf(renderer.pageCount, PDF_PAGE_LIMIT)).map { index ->
                renderer.openPage(index).use { page ->
                    val width = 1080
                    val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                        bitmap.eraseColor(AndroidColor.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }
    }
}.getOrDefault(emptyList())
