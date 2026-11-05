package com.kanbanboard.mobile.core.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.kanbanboard.mobile.core.model.AppError
import com.kanbanboard.mobile.core.model.PickedFile
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Локальное хранилище файлов приложения.
 *
 * В демо-режиме это и есть «сервер файлов»: выбранный пользователем файл копируется в
 * память приложения, а в задачу/проект записывается ссылка на него ([PickedFile.fileRef]).
 * После подключения Attachment API сюда же будут скачиваться файлы с сервера для просмотра.
 *
 * Ссылка (fileRef) — путь относительно каталога `files/store` внутри приложения.
 */
class FileStore(context: Context) {
    private val app = context.applicationContext
    private val root = File(app.filesDir, "store").apply { mkdirs() }

    fun file(fileRef: String): File? {
        val file = File(root, fileRef).canonicalFile
        // Защита от выхода за пределы хранилища через «../» в ссылке.
        if (!file.path.startsWith(root.canonicalPath) || !file.isFile) return null
        return file
    }

    /** Копирует файл, выбранный через системный диалог, в хранилище. Лимит — [maxBytes]. */
    suspend fun import(uri: Uri, maxBytes: Long = MAX_FILE_BYTES): PickedFile = withContext(Dispatchers.IO) {
        val resolver = app.contentResolver
        var name = "file"
        var declaredSize = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) declaredSize = cursor.getLong(sizeIndex)
            }
        }
        if (declaredSize > maxBytes) throw AppError.Validation("Файл больше ${maxBytes / (1024 * 1024)} МБ")
        val safeName = sanitize(name)
        val folder = UUID.randomUUID().toString()
        val target = File(File(root, folder).apply { mkdirs() }, safeName)
        var copied = 0L
        try {
            resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        copied += read
                        if (copied > maxBytes) throw AppError.Validation("Файл больше ${maxBytes / (1024 * 1024)} МБ")
                        output.write(buffer, 0, read)
                    }
                }
            } ?: throw AppError.Validation("Не удалось открыть файл")
        } catch (e: Exception) {
            target.parentFile?.deleteRecursively()
            throw e
        }
        PickedFile(safeName, copied, "$folder/$safeName")
    }

    /** Кладёт текстовый файл в хранилище (данные мок-версии); возвращает (ссылка, размер). */
    fun writeText(name: String, content: String): Pair<String, Long> {
        val safeName = sanitize(name)
        val folder = UUID.randomUUID().toString()
        val target = File(File(root, folder).apply { mkdirs() }, safeName)
        target.writeText(content)
        return "$folder/$safeName" to target.length()
    }

    fun delete(fileRef: String) {
        file(fileRef)?.parentFile?.takeIf { it.parentFile?.canonicalPath == root.canonicalPath }?.deleteRecursively()
    }

    /** Удаляет файл по абсолютному пути (фото профиля, обои), если он лежит в хранилище приложения. */
    fun deleteByPath(path: String) {
        val file = File(path).canonicalFile
        if (file.path.startsWith(root.canonicalPath + File.separator)) delete(file.relativeTo(root.canonicalFile).path)
    }

    /**
     * Сохраняет файл в папку, выбранную через SAF: внутри неё создаётся (или берётся)
     * подпапка с названием проекта, файл кладётся туда. Возвращает итоговое имя файла.
     */
    suspend fun exportToFolder(fileRef: String, treeUri: Uri, projectName: String): String = withContext(Dispatchers.IO) {
        val source = file(fileRef) ?: throw AppError.NotFound("Файл не найден на устройстве")
        val tree = DocumentFile.fromTreeUri(app, treeUri)
            ?.takeIf { it.canWrite() }
            ?: throw AppError.Forbidden("Нет доступа к папке. Выберите папку заново")
        val folderName = sanitize(projectName).ifBlank { "Без проекта" }
        val projectDir = tree.findFile(folderName)?.takeIf { it.isDirectory }
            ?: tree.createDirectory(folderName)
            ?: throw AppError.Validation("Не удалось создать папку «$folderName»")
        val name = uniqueName(projectDir, source.name)
        val target = projectDir.createFile(mimeType(source.name), name)
            ?: throw AppError.Validation("Не удалось создать файл")
        app.contentResolver.openOutputStream(target.uri)?.use { output ->
            source.inputStream().use { it.copyTo(output) }
        } ?: throw AppError.Validation("Не удалось записать файл")
        "$folderName/${target.name ?: name}"
    }

    private fun uniqueName(dir: DocumentFile, name: String): String {
        if (dir.findFile(name) == null) return name
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var index = 2
        while (dir.findFile("$base ($index)$ext") != null) index++
        return "$base ($index)$ext"
    }

    companion object {
        const val MAX_FILE_BYTES: Long = 10L * 1024 * 1024

        fun sanitize(name: String): String =
            name.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_").trim().take(120).ifBlank { "file" }

        fun mimeType(name: String): String =
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
                ?: "application/octet-stream"
    }
}
