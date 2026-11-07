package com.kanbanboard.mobile.core.data.schedule

import android.content.Context
import com.kanbanboard.mobile.core.model.Lesson
import com.kanbanboard.mobile.core.model.LessonKind
import com.kanbanboard.mobile.core.model.LessonsSnapshot
import com.kanbanboard.mobile.core.model.LessonsOrigin as Source
import com.kanbanboard.mobile.core.model.StudyGroup
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** Откуда берутся пары. Экраны получают их через ScheduleRepository и не знают об источнике. */
interface LessonsSource {
    fun lessons(group: StudyGroup, from: LocalDate, to: LocalDate): Flow<LessonsSnapshot>
}

/**
 * Пары с сайта ИСУ УУНиТ (https://isu.uust.ru/class_schedule/) с кэшем на устройстве.
 *
 * Порядок: сразу отдаём кэш (если есть), затем загружаем страницу ИСУ и разбираем её
 * [IsuScheduleParser]. Успех — сохраняем в кэш и отдаём как [Source.ISU]. Ошибка сети или
 * неразобранная страница — остаёмся на кэше ([Source.CACHE]), а если кэша нет — пар нет
 * ([Source.UNAVAILABLE]), и экран показывает предупреждение. Выдуманных пар приложение не показывает.
 * Сеть дёргается не чаще раза в [REFRESH_INTERVAL] на группу.
 */
class IsuLessonsSource(
    context: Context,
    private val config: IsuConfig = IsuConfig(),
    private val clock: Clock = Clock.systemDefaultZone(),
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    /** Пример пар на случай, когда нет ни сети, ни кэша. Задаётся только в мок-версии. */
    private val example: ((StudyGroup, LocalDate, LocalDate) -> List<Lesson>)? = null,
) : LessonsSource {

    private val cacheDir = File(context.applicationContext.filesDir, "isu").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }
    private val lastAttempt = ConcurrentHashMap<StudyGroup, Instant>()
    private val locks = ConcurrentHashMap<StudyGroup, Mutex>()

    override fun lessons(group: StudyGroup, from: LocalDate, to: LocalDate): Flow<LessonsSnapshot> = flow {
        val cached = readCache(group)
        if (cached != null) emit(cached.snapshot(from, to, Source.CACHE))

        val fresh = locks.getOrPut(group) { Mutex() }.withLock { refreshIfDue(group) }
        when {
            fresh != null -> emit(fresh.snapshot(from, to, Source.ISU))
            cached == null -> emit(
                example?.let { LessonsSnapshot(it(group, from, to), null, Source.EXAMPLE) }
                    ?: LessonsSnapshot(emptyList(), null, Source.UNAVAILABLE),
            )
        }
    }.flowOn(Dispatchers.IO)

    /** Загружает и кэширует расписание; null — не удалось или ещё рано повторять запрос. */
    private fun refreshIfDue(group: StudyGroup): CachedLessons? {
        val now = clock.instant()
        val previous = lastAttempt[group]
        if (previous != null && Duration.between(previous, now) < REFRESH_INTERVAL) {
            // Недавно уже пробовали: если тогда получилось, свежие данные лежат в кэше.
            return readCache(group)?.takeIf { Duration.between(Instant.ofEpochMilli(it.updatedAtMillis), now) < REFRESH_INTERVAL }
        }
        lastAttempt[group] = now
        return try {
            val html = download(config.url(group))
            val lessons = IsuScheduleParser.parse(html, group, LocalDate.now(clock))
            if (lessons.isEmpty()) {
                null // страница не распознана — не затираем рабочий кэш пустым
            } else {
                CachedLessons(now.toEpochMilli(), lessons.map(CachedLesson::from)).also { writeCache(group, it) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun download(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "KodVSapoge-Android/1.0")
            .header("Accept-Language", "ru")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("ИСУ ответила ${response.code}")
            return response.body?.string() ?: throw IOException("Пустой ответ ИСУ")
        }
    }

    private fun cacheFile(group: StudyGroup) = File(cacheDir, "${group.code}.json")

    private fun readCache(group: StudyGroup): CachedLessons? = try {
        cacheFile(group).takeIf { it.isFile }?.readText()?.let { json.decodeFromString(CachedLessons.serializer(), it) }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        null // повреждённый кэш просто игнорируем
    }

    private fun writeCache(group: StudyGroup, data: CachedLessons) {
        val target = cacheFile(group)
        val temp = File(cacheDir, "${group.code}.json.tmp")
        temp.writeText(json.encodeToString(CachedLessons.serializer(), data))
        if (!temp.renameTo(target)) {
            target.delete()
            temp.renameTo(target)
        }
    }

    private fun CachedLessons.snapshot(from: LocalDate, to: LocalDate, source: Source) = LessonsSnapshot(
        lessons = lessons.map { it.toLesson() }.filter { !it.date.isBefore(from) && !it.date.isAfter(to) },
        updatedAt = Instant.ofEpochMilli(updatedAtMillis),
        source = source,
    )

    private companion object {
        val REFRESH_INTERVAL: Duration = Duration.ofMinutes(30)
    }
}

/**
 * Адреса страниц ИСУ. Полные названия групп и формат ссылки задаются в app/build.gradle.kts
 * (свойства isuGroup203, isuGroup204, isuUrlTemplate) — их нужно сверить с сайтом.
 */
data class IsuConfig(
    val urlTemplate: String = com.kanbanboard.mobile.BuildConfig.ISU_URL_TEMPLATE,
    val groupNames: Map<StudyGroup, String> = mapOf(
        StudyGroup.G203 to com.kanbanboard.mobile.BuildConfig.ISU_GROUP_203,
        StudyGroup.G204 to com.kanbanboard.mobile.BuildConfig.ISU_GROUP_204,
    ),
) {
    fun url(group: StudyGroup): String =
        urlTemplate.replace("{group}", java.net.URLEncoder.encode(groupNames.getValue(group), "UTF-8"))
}

@Serializable
private data class CachedLessons(val updatedAtMillis: Long, val lessons: List<CachedLesson>)

@Serializable
private data class CachedLesson(
    val id: String,
    val group: String,
    val date: String,
    val start: String,
    val end: String,
    val title: String,
    val room: String,
    val teacher: String,
    val kind: String,
) {
    fun toLesson() = Lesson(
        id = id,
        group = StudyGroup.fromCode(group) ?: StudyGroup.G203,
        date = LocalDate.parse(date),
        start = LocalTime.parse(start),
        end = LocalTime.parse(end),
        title = title,
        room = room,
        teacher = teacher,
        kind = LessonKind.entries.firstOrNull { it.name == kind } ?: LessonKind.LECTURE,
    )

    companion object {
        fun from(lesson: Lesson) = CachedLesson(
            lesson.id, lesson.group.code, lesson.date.toString(), lesson.start.toString(), lesson.end.toString(),
            lesson.title, lesson.room, lesson.teacher, lesson.kind.name,
        )
    }
}
