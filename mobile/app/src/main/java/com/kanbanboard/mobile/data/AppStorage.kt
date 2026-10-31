package com.kanbanboard.mobile.data

import android.content.Context
import androidx.core.content.edit
import com.kanbanboard.mobile.model.AppTheme
import com.kanbanboard.mobile.model.EventType
import com.kanbanboard.mobile.model.EventVisibility
import com.kanbanboard.mobile.model.TeamEvent
import com.kanbanboard.mobile.model.Lesson
import com.kanbanboard.mobile.model.LessonKind
import org.json.JSONArray
import org.json.JSONObject

interface AppStorage {
    fun theme(): AppTheme
    fun group(): String
    fun events(): List<TeamEvent>
    fun types(): List<EventType>
    fun lessons(group: String): List<Lesson>
    fun saveTheme(value: AppTheme)
    fun saveGroup(value: String)
    fun saveEvents(value: List<TeamEvent>)
    fun saveTypes(value: List<EventType>)
    fun saveLessons(group: String, value: List<Lesson>)
}

class MemoryAppStorage : AppStorage {
    private var selectedTheme = AppTheme.LIGHT
    private var selectedGroup = "203"
    private var savedEvents = emptyList<TeamEvent>()
    private var savedTypes = emptyList<EventType>()
    private val savedLessons = mutableMapOf<String, List<Lesson>>()
    override fun theme() = selectedTheme
    override fun group() = selectedGroup
    override fun events() = savedEvents
    override fun types() = savedTypes
    override fun lessons(group: String) = savedLessons[group].orEmpty()
    override fun saveTheme(value: AppTheme) { selectedTheme = value }
    override fun saveGroup(value: String) { selectedGroup = value }
    override fun saveEvents(value: List<TeamEvent>) { savedEvents = value }
    override fun saveTypes(value: List<EventType>) { savedTypes = value }
    override fun saveLessons(group: String, value: List<Lesson>) { savedLessons[group] = value }
}

class SharedPreferencesAppStorage(context: Context) : AppStorage {
    private val prefs = context.getSharedPreferences("kanban_mobile", Context.MODE_PRIVATE)

    override fun theme() = runCatching { AppTheme.valueOf(prefs.getString("theme", "LIGHT") ?: "LIGHT") }.getOrDefault(AppTheme.LIGHT)
    override fun group() = prefs.getString("group", "203") ?: "203"
    override fun saveTheme(value: AppTheme) { prefs.edit { putString("theme", value.name) } }
    override fun saveGroup(value: String) { prefs.edit { putString("group", value) } }

    override fun lessons(group: String): List<Lesson> = runCatching {
        val array = JSONArray(prefs.getString("lessons_$group", "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            Lesson(item.getString("date"), item.getString("time"), item.getString("title"),
                item.optString("room"), item.optString("teacher"), LessonKind.valueOf(item.getString("kind")))
        }
    }.getOrDefault(emptyList())

    override fun saveLessons(group: String, value: List<Lesson>) {
        val array = JSONArray()
        value.forEach { lesson -> array.put(JSONObject().apply {
            put("date", lesson.date); put("time", lesson.time); put("title", lesson.title)
            put("room", lesson.room); put("teacher", lesson.teacher); put("kind", lesson.kind.name)
        }) }
        prefs.edit { putString("lessons_$group", array.toString()) }
    }

    override fun events(): List<TeamEvent> = runCatching {
        val array = JSONArray(prefs.getString("events", "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val reminders = item.optJSONArray("reminders") ?: JSONArray()
            TeamEvent(
                id = item.getLong("id"),
                title = item.getString("title"),
                date = item.getString("date"),
                time = item.getString("time"),
                endTime = item.optString("endTime", runCatching {
                    java.time.LocalTime.parse(item.getString("time")).plusHours(1).toString()
                }.getOrDefault(item.getString("time"))),
                description = item.optString("description", ""),
                location = item.optString("location", ""),
                visibility = EventVisibility.valueOf(item.getString("visibility")),
                group = item.optString("group").ifBlank { null },
                typeId = item.getString("typeId"),
                reminderMinutes = (0 until reminders.length()).map { reminders.getInt(it) }.toSet(),
                ownerId = if (item.has("ownerId")) item.getLong("ownerId") else null,
                teamId = item.optLong("teamId", 1),
            )
        }
    }.getOrDefault(emptyList())

    override fun types(): List<EventType> = runCatching {
        val array = JSONArray(prefs.getString("types", "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val reminders = item.optJSONArray("defaultReminders") ?: JSONArray()
            EventType(item.getString("id"), item.getString("title"), item.getLong("color"), true,
                (0 until reminders.length()).map { reminders.getInt(it) }.toSet())
        }
    }.getOrDefault(emptyList())

    override fun saveEvents(value: List<TeamEvent>) {
        val array = JSONArray()
        value.forEach { event ->
            array.put(JSONObject().apply {
                put("id", event.id)
                put("title", event.title)
                put("date", event.date)
                put("time", event.time)
                put("endTime", event.endTime)
                put("description", event.description)
                put("location", event.location)
                put("visibility", event.visibility.name)
                put("group", event.group)
                put("typeId", event.typeId)
                put("reminders", JSONArray(event.reminderMinutes.sorted()))
                put("ownerId", event.ownerId)
                put("teamId", event.teamId)
            })
        }
        prefs.edit { putString("events", array.toString()) }
    }

    override fun saveTypes(value: List<EventType>) {
        val array = JSONArray()
        value.filter { it.custom }.forEach { type ->
            array.put(JSONObject().apply {
                put("id", type.id)
                put("title", type.title)
                put("color", type.color)
                put("defaultReminders", JSONArray(type.defaultReminders.sorted()))
            })
        }
        prefs.edit { putString("types", array.toString()) }
    }
}
