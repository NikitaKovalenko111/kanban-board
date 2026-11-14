package com.kanbanboard.mobile.feature.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kanbanboard.mobile.core.data.BoardRepository
import com.kanbanboard.mobile.core.data.ScheduleRepository
import com.kanbanboard.mobile.core.data.SessionRepository
import com.kanbanboard.mobile.core.data.TeamRepository
import com.kanbanboard.mobile.core.model.EventType
import com.kanbanboard.mobile.core.model.EventTypeId
import com.kanbanboard.mobile.core.model.EventVisibility
import com.kanbanboard.mobile.core.model.Lesson
import com.kanbanboard.mobile.core.model.LessonsOrigin
import com.kanbanboard.mobile.core.model.LessonsSnapshot
import com.kanbanboard.mobile.core.model.ReminderKeys
import com.kanbanboard.mobile.core.model.ScheduleEvent
import com.kanbanboard.mobile.core.model.Session
import com.kanbanboard.mobile.core.model.StudyGroup
import com.kanbanboard.mobile.core.model.Task
import com.kanbanboard.mobile.core.model.TaskId
import com.kanbanboard.mobile.core.model.User
import com.kanbanboard.mobile.core.model.UserId
import com.kanbanboard.mobile.core.time.Formats
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GroupFilter(val title: String, val groups: Set<StudyGroup>)

/** Что показывает лента (выпадающее меню «Моё расписание»). */
sealed interface ScheduleView {
    /** Моё полное расписание: пары моей группы, события, дедлайны; фильтр категорий работает. */
    data object Mine : ScheduleView

    /** Только пары выбранной группы (групп); фильтр категорий заблокирован. */
    data class Groups(val filter: GroupFilter) : ScheduleView

    /** Расписание участника с учётом его приватности. */
    data class Person(val id: UserId) : ScheduleView
}

/** Категории ленты. Фильтр — набор отмеченных категорий (компактное меню с галочками). */
enum class AgendaCategory(val title: String) {
    LESSONS("Пары"),
    TEAM("Команда"),
    GROUP("Группа"),
    PERSONAL("Лично"),
    DEADLINES("Дедлайны"),
}

enum class AgendaKind { LESSON, EVENT, BUSY, DEADLINE }

/** Строка ленты дня. Цвет пары берётся из темы по [lesson], события — из [type]. */
data class AgendaItem(
    val key: String,
    val kind: AgendaKind,
    val start: LocalTime,
    val end: String?,
    val title: String,
    val meta: String,
    val lesson: Lesson? = null,
    val event: ScheduleEvent? = null,
    val type: EventType? = null,
    val taskId: TaskId? = null,
    val reminders: Set<Int> = emptySet(),
)

data class ScheduleUiState(
    val loading: Boolean = true,
    val me: Session? = null,
    val members: List<User> = emptyList(),
    val person: User? = null,
    val view: ScheduleView = ScheduleView.Mine,
    val categories: Set<AgendaCategory> = AgendaCategory.entries.toSet(),
    val selectedDate: LocalDate = LocalDate.now(),
    val days: Map<LocalDate, List<AgendaItem>> = emptyMap(),
    val loadedFrom: LocalDate? = null,
    val loadedTo: LocalDate? = null,
    /** Предупреждение, если пары показаны не из ИСУ (нет связи — кэш или шаблон). null — всё в порядке. */
    val lessonsWarning: String? = null,
) {
    val isMine: Boolean get() = view is ScheduleView.Mine

    /** Режим «только пары группы»: события и дедлайны не показываются, фильтр категорий заблокирован. */
    val lessonsOnly: Boolean get() = view is ScheduleView.Groups
    fun isLoaded(date: LocalDate): Boolean =
        loadedFrom != null && loadedTo != null && !date.isBefore(loadedFrom) && !date.isAfter(loadedTo)
    fun weekStart(): LocalDate = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}

private data class Query(
    val me: Session,
    val members: List<User>,
    val person: User,
    val groups: Set<StudyGroup>,
    val lessonsOnly: Boolean,
    val from: LocalDate,
    val to: LocalDate,
)

private data class Loaded(
    val query: Query,
    val lessons: List<Lesson>,
    val lessonsUpdated: LessonsSnapshot?,
    val events: List<ScheduleEvent>,
    val tasks: List<Task>,
    val reminders: Map<String, Set<Int>>,
    val types: Map<EventTypeId, EventType>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModel(
    sessions: SessionRepository,
    team: TeamRepository,
    private val schedule: ScheduleRepository,
    board: BoardRepository,
) : ViewModel() {

    /** Выбор в меню «Моё расписание»; живёт во ViewModel и не сбрасывается при свайпе дней (MOB-09). */
    private val view = MutableStateFlow<ScheduleView>(ScheduleView.Mine)
    private val categories = MutableStateFlow(AgendaCategory.entries.toSet())
    private val selectedDate = MutableStateFlow(LocalDate.now())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    /** Окно загрузки: неделя выбранного дня ± неделя, чтобы свайп к соседним дням не ждал данных. */
    private val window: Flow<Pair<LocalDate, LocalDate>> = selectedDate
        .map { date ->
            val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            monday.minusWeeks(1) to monday.plusWeeks(2).minusDays(1)
        }
        .distinctUntilChanged()

    private val query: Flow<Query?> = combine(
        sessions.session,
        team.members,
        view,
        window,
    ) { me, members, selected, (from, to) ->
        if (me == null) return@combine null
        val users = members.map { it.user }.filter { it.isActive }
        when (selected) {
            ScheduleView.Mine -> Query(me, users, me.user, setOf(me.user.studyGroup), lessonsOnly = false, from, to)
            is ScheduleView.Groups -> Query(me, users, me.user, selected.filter.groups, lessonsOnly = true, from, to)
            is ScheduleView.Person -> {
                // Участника могли заблокировать или удалить — тогда возвращаемся к своему расписанию.
                val person = users.firstOrNull { it.id == selected.id } ?: me.user
                Query(me, users, person, setOf(person.studyGroup), lessonsOnly = false, from, to)
            }
        }
    }.distinctUntilChanged()

    private val loaded: Flow<Loaded?> = query.flatMapLatest { q ->
        if (q == null) return@flatMapLatest flowOf<Loaded?>(null)
        val zone = ZoneId.systemDefault()
        val lessonFlows = q.groups.sortedBy { it.code }.map { schedule.lessons(it, q.from, q.to) }
        val lessonsFlow: Flow<List<LessonsSnapshot>> =
            if (lessonFlows.isEmpty()) flowOf(emptyList()) else combine(lessonFlows) { it.toList() }
        // В режиме «только пары группы» события и дедлайны не нужны.
        val eventsFlow = if (q.lessonsOnly) {
            flowOf(emptyList())
        } else {
            schedule.events(q.person.id, q.groups, q.from.atStartOfDay(zone).toInstant(), q.to.plusDays(1).atStartOfDay(zone).toInstant())
        }
        val tasksFlow = if (q.lessonsOnly) flowOf(emptyList()) else board.assignedTasks(q.person.id)
        combine(
            lessonsFlow,
            eventsFlow,
            tasksFlow,
            schedule.myReminders,
            schedule.eventTypes,
        ) { snapshots, events, tasks, reminders, types ->
            Loaded(
                query = q,
                lessons = snapshots.flatMap { it.lessons },
                lessonsUpdated = snapshots.firstOrNull(),
                events = events,
                tasks = tasks,
                reminders = reminders,
                types = types.associateBy { it.id },
            )
        }
    }

    val state: StateFlow<ScheduleUiState> = combine(loaded, view, categories, selectedDate) { data, selectedView, aFilter, date ->
        if (data == null) {
            ScheduleUiState(loading = true, categories = aFilter, selectedDate = date)
        } else {
            val q = data.query
            ScheduleUiState(
                loading = false,
                me = q.me,
                members = q.members,
                person = q.person,
                view = if (selectedView is ScheduleView.Person && q.person.id == q.me.user.id) ScheduleView.Mine else selectedView,
                categories = aFilter,
                selectedDate = date,
                days = buildDays(data, if (q.lessonsOnly) setOf(AgendaCategory.LESSONS) else aFilter),
                loadedFrom = q.from,
                loadedTo = q.to,
                lessonsWarning = data.lessonsUpdated?.let { snapshot ->
                    when (snapshot.source) {
                        LessonsOrigin.ISU -> null
                        LessonsOrigin.CACHE ->
                            "Нет связи с ИСУ — показано сохранённое расписание" +
                                (snapshot.updatedAt?.let { " от ${Formats.dateTime(it)}" } ?: "")
                        LessonsOrigin.UNAVAILABLE -> "Не удалось получить пары с ИСУ — расписание появится, когда сайт станет доступен"
                        LessonsOrigin.EXAMPLE -> "Нет связи с ИСУ — показан пример расписания"
                    }
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleUiState())

    private fun buildDays(data: Loaded, filter: Set<AgendaCategory>): Map<LocalDate, List<AgendaItem>> {
        val zone = ZoneId.systemDefault()
        val q = data.query
        val names = q.members.associate { it.id to it.fullName }
        val showGroup = q.groups.size > 1
        val items = mutableListOf<Pair<LocalDate, AgendaItem>>()

        if (AgendaCategory.LESSONS in filter) {
            data.lessons.forEach { lesson ->
                val key = ReminderKeys.lesson(lesson.id)
                items += lesson.date to AgendaItem(
                    key = key,
                    kind = AgendaKind.LESSON,
                    start = lesson.start,
                    end = Formats.time(lesson.end),
                    title = lesson.title,
                    meta = listOfNotNull(
                        lesson.kind.title,
                        lesson.room.ifBlank { null },
                        if (showGroup) "Группа ${lesson.group.code}" else null,
                    ).joinToString(" · "),
                    lesson = lesson,
                    reminders = data.reminders[key].orEmpty(),
                )
            }
        }

        data.events.forEach { event ->
            val wanted = when (event.visibility) {
                EventVisibility.TEAM -> AgendaCategory.TEAM in filter
                EventVisibility.GROUP -> AgendaCategory.GROUP in filter
                EventVisibility.PERSONAL -> AgendaCategory.PERSONAL in filter
            }
            if (!wanted) return@forEach
            val start = event.startsAt.atZone(zone)
            val end = event.endsAt?.atZone(zone)
            val endLabel = when {
                end == null -> null
                end.toLocalDate() == start.toLocalDate() -> Formats.time(end.toLocalTime())
                else -> Formats.dateTime(end.toInstant())
            }
            val key = ReminderKeys.event(event.id)
            val owner = names[event.ownerId] ?: "Участник"
            items += start.toLocalDate() to AgendaItem(
                key = key,
                kind = if (event.masked) AgendaKind.BUSY else AgendaKind.EVENT,
                start = start.toLocalTime(),
                end = endLabel,
                title = if (event.masked) "$owner занят" else event.title,
                meta = if (event.masked) {
                    "Личное событие"
                } else {
                    listOfNotNull(
                        data.types[event.typeId]?.title,
                        when (event.visibility) {
                            EventVisibility.TEAM -> "Команда"
                            EventVisibility.GROUP -> "Группа ${event.group?.code}"
                            EventVisibility.PERSONAL -> "Лично"
                        },
                        event.location.ifBlank { null },
                    ).joinToString(" · ")
                },
                event = event,
                type = data.types[event.typeId],
                reminders = data.reminders[key].orEmpty(),
            )
        }

        if (AgendaCategory.DEADLINES in filter) {
            data.tasks.forEach { task ->
                val due = task.dueAt?.atZone(zone) ?: return@forEach
                items += due.toLocalDate() to AgendaItem(
                    key = "task:${task.id.value}",
                    kind = AgendaKind.DEADLINE,
                    start = due.toLocalTime(),
                    end = null,
                    title = task.title,
                    meta = "Дедлайн задачи · ${task.assigneeId?.let { names[it] } ?: "исполнитель"}",
                    taskId = task.id,
                )
            }
        }

        return items.groupBy({ it.first }, { it.second })
            .mapValues { (_, list) -> list.sortedWith(compareBy<AgendaItem>({ it.start }, { it.kind.ordinal })) }
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun selectView(selected: ScheduleView) {
        view.value = selected
    }

    fun toggleCategory(category: AgendaCategory) {
        val current = categories.value
        // Хотя бы одна категория остаётся отмеченной, иначе лента всегда пуста.
        val next = if (category in current) current - category else current + category
        if (next.isNotEmpty()) categories.value = next
    }

    fun setReminders(itemKey: String, minutes: Set<Int>) {
        viewModelScope.launch {
            schedule.setMyReminders(itemKey, minutes)
                .onSuccess {
                    _messages.trySend(if (minutes.isEmpty()) "Напоминания убраны" else "Напоминания сохранены")
                }
                .onFailure { _messages.trySend(it.message ?: "Не удалось сохранить напоминания") }
        }
    }
}
