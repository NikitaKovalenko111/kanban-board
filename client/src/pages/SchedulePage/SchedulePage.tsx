import { isBackendMode } from '../../api/config'
import { realApi } from '../../api/realApi'
import type { EventTypeOption } from '../../utils/eventTypes'
import type { GroupDto } from '../../api/contracts'
import Icon from '../../components/Icon/Icon'
import { useEffect, useId, useMemo, useRef, useState, type FormEvent } from 'react'
import FormModal from '../../components/FormModal/FormModal'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { createScheduleEventRequest, loadScheduleRequest, updateEventRemindersRequest } from '../../store/slices/scheduleSlice'
import type { EventCategory, EventReminders, ScheduleEvent } from '../../types'
import { loadEventTypes } from '../../utils/eventTypes'
import SearchField from '../../components/SearchField/SearchField'
import { useProjectSelection } from '../../store/useProjectSelection'
import { loadTeamRequest } from '../../store/slices/teamSlice'
import { eventCategory, eventFilterCategory, type ScheduleFilterCategory } from '../../utils/schedule'
import { useSearchParams } from 'react-router-dom'
import EventReminderEditor from '../../components/ReminderEditor/EventReminderEditor'
import { eventReminderMinutes, validateReminderMinutes } from '../../utils/reminders'
import { useDaySlider } from './useDaySlider'

const categoryOptions: { value: ScheduleFilterCategory; label: string }[] = [
  { value: 'class', label: 'Пары' },
  { value: 'team', label: 'Командные' },
  { value: 'group', label: 'Групповые' },
  { value: 'personal', label: 'Личные' },
]

const localDate = (day: Date) => `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, '0')}-${String(day.getDate()).padStart(2, '0')}`
const formatTime = (value: string) => new Date(value).toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })

const SchedulePage = () => {
  const dispatch = useAppDispatch()
  const [searchParams] = useSearchParams()
  const user = useAppSelector((state) => state.auth.user)!
  const projects = useAppSelector((state) => state.projects.items)
  const activeProjectId = useProjectSelection('schedule')
  const { items: events, loading, saving, error, sourceMessage } = useAppSelector((state) => state.schedule)
  const [showForm, setShowForm] = useState(false)
  const [filtersOpen, setFiltersOpen] = useState(false)
  const filtersId = useId()
  const [query, setQuery] = useState('')
  const [viewMode, setViewMode] = useState<'day' | 'week'>('week')
  const [layout, setLayout] = useState<'columns' | 'rows'>('columns')
  const [wideLayout, setWideLayout] = useState(false)
  const scheduleRef = useRef<HTMLDivElement>(null)
  const visibleMode = wideLayout ? viewMode : 'day'
  const [selectedDate, setSelectedDate] = useState(() => {
    const requested = searchParams.get('date')
    return requested && /^\d{4}-\d{2}-\d{2}$/.test(requested) && !Number.isNaN(Date.parse(requested)) ? requested : localDate(new Date())
  })
  const members = useAppSelector((state) => state.team.members)
  const [scope, setScope] = useState('mine')
  const [categoryFilters, setCategoryFilters] = useState<ScheduleFilterCategory[]>(() => categoryOptions.map(option => option.value))
  const [category, setCategory] = useState<EventCategory>('team')
  const selectedOwnerId = scope.startsWith('user:') ? (isBackendMode ? scope.slice(5) : Number(scope.slice(5))) : user.id
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [date, setDate] = useState(() => localDate(new Date()))
  const [time, setTime] = useState('10:00')
  const [endTime, setEndTime] = useState('')
  const [eventTypes, setEventTypes] = useState<EventTypeOption[]>(() => isBackendMode ? [] : loadEventTypes())
  const [groups, setGroups] = useState<GroupDto[]>([])
  const [eventGroup, setEventGroup] = useState(user.studentGroup ?? '')
  const [type, setType] = useState(eventTypes[0]?.id ?? '')
  const [reminders, setReminders] = useState<EventReminders>({ reminderMode: 'type', reminderMinutes: eventTypes[0]?.reminderMinutes ?? [] })
  const [reminderEvent, setReminderEvent] = useState<ScheduleEvent | null>(null)
  const [reminderError, setReminderError] = useState('')
  const [reminderSubmitted, setReminderSubmitted] = useState(false)
  const [eventSubmitted, setEventSubmitted] = useState(false)
  const [eventProjectId, setEventProjectId] = useState(activeProjectId === 'all' ? '' : activeProjectId)
  const [location, setLocation] = useState('')
  const slider = useDaySlider(selectedDate, setSelectedDate, `${layout}-${loading}`)

  useEffect(() => { if (isBackendMode) { void Promise.all([realApi.getEventTypes(), realApi.getGroups()]).then(([types, values]) => { setEventTypes(types); setGroups(values) }).catch(reason => setReminderError(reason.message)) } }, [])
  useEffect(() => { if (!type && eventTypes.length) setType(eventTypes[0].id) }, [type, eventTypes])
  useEffect(() => { dispatch(loadTeamRequest()) }, [dispatch])
  useEffect(() => {
    const container = scheduleRef.current
    if (!container) return
    const observer = new ResizeObserver(([entry]) => setWideLayout(entry.contentRect.width >= 1200))
    observer.observe(container)
    return () => observer.disconnect()
  }, [])
  useEffect(() => {
    const requested = searchParams.get('date')
    if (requested && /^\d{4}-\d{2}-\d{2}$/.test(requested) && !Number.isNaN(Date.parse(requested))) setSelectedDate(requested)
  }, [searchParams])
  const linkedEventId = searchParams.get('event')
  const linkedLessonId = searchParams.get('lesson')
  const linkedNotificationAt = searchParams.get('at')
  useEffect(() => {
    if (!isBackendMode || !linkedLessonId || !linkedNotificationAt) return
    let active = true
    setScope('mine')
    void realApi.getLessonDate(linkedLessonId, linkedNotificationAt).then(date => { if (active) setSelectedDate(localDate(new Date(date))) }).catch(reason => { if (active) setReminderError(reason.message) })
    return () => { active = false }
  }, [linkedLessonId, linkedNotificationAt])
  useEffect(() => { if (isBackendMode && linkedEventId) { let active = true; void realApi.getScheduleEventTarget(linkedEventId).then(event => { if (active) { setSelectedDate(localDate(new Date(event.startsAt))); setScope(event.visibility === 'PERSONAL' && event.ownerId !== user.id ? `user:${event.ownerId}` : 'mine'); setCategoryFilters(categoryOptions.map(option => option.value)); setQuery('') } }).catch(reason => { if (active) setReminderError(reason.message) }); return () => { active = false } } }, [linkedEventId, user.id])
  useEffect(() => { if (!eventProjectId && projects.length) setEventProjectId(projects[0].id) }, [eventProjectId, projects])

  const weekDays = useMemo(() => {
    const monday = new Date(`${selectedDate}T12:00:00`)
    monday.setDate(monday.getDate() - ((monday.getDay() + 6) % 7))
    return Array.from({ length: 7 }, (_, index) => {
      const day = new Date(monday)
      day.setDate(monday.getDate() + index)
      return localDate(day)
    })
  }, [selectedDate])
  const visibleDays = slider.mobile ? slider.days : visibleMode === 'week' ? weekDays : [selectedDate]
  const rangeFrom = weekDays[0]
  const rangeTo = weekDays[6]
  useEffect(() => { if (isBackendMode) dispatch(loadScheduleRequest({ projectId: 'all', personId: selectedOwnerId, groups: scope.startsWith('group:') ? (scope === 'group:both' ? groups.map(item => item.groupCode) : [scope.slice(6)]) : undefined, from: rangeFrom, to: rangeTo })); else dispatch(loadScheduleRequest(activeProjectId)) }, [dispatch, activeProjectId, user.eventPrivacy, selectedOwnerId, scope, rangeFrom, rangeTo, groups])
  const filteredEvents = events.filter((event) => {
    const projectName = projects.find((project) => project.id === event.projectId)?.name ?? ''
    const personal = eventCategory(event) === 'personal'
    const group = scope.startsWith('group:') ? scope.slice(6) : 'both'
    const owner = members.find((member) => member.id === selectedOwnerId) ?? user
    const matchesOwner = personal ? event.ownerId === selectedOwnerId : !!(event.projectId && owner.projectRoles[event.projectId]) || owner.role === 'lead'
    return (isBackendMode || personal || activeProjectId === 'all' || event.projectId === activeProjectId)
      && categoryFilters.includes(eventFilterCategory(event))
      && (isBackendMode || (scope.startsWith('group:') ? !personal && (!event.group || group === 'both' || event.group === 'both' || event.group === group) : matchesOwner))
      && `${event.title} ${event.description} ${projectName}`.toLowerCase().includes(query.toLowerCase())
  }).sort((a, b) => a.startsAt.localeCompare(b.startsAt))

  const movePeriod = (direction: number) => {
    if (slider.mobile) { slider.move(direction); return }
    const next = new Date(`${selectedDate}T12:00:00`)
    next.setDate(next.getDate() + direction * (visibleMode === 'week' ? 7 : 1))
    setSelectedDate(localDate(next))
  }
  useEffect(() => {
    if (reminderSubmitted && !saving && !error) { setReminderEvent(null); setReminderSubmitted(false) }
  }, [reminderSubmitted, saving, error])
  useEffect(() => {
    if (!eventSubmitted || saving) return
    setEventSubmitted(false)
    if (error) return
    setShowForm(false); setSelectedDate(date)
    setTitle(''); setDescription(''); setLocation(''); setEndTime('')
  }, [eventSubmitted, saving, error, date])
  const editableReminders = (event: ScheduleEvent) => isBackendMode ? !event.masked && !event.partial : event.ownerId === user.id || (user.role === 'lead' && eventCategory(event) !== 'personal')
  const openReminders = (event: ScheduleEvent) => {
    setReminderEvent(event); setReminderError(''); setReminderSubmitted(false)
    setReminders({ reminderMode: event.reminderMode ?? 'custom', reminderMinutes: eventReminderMinutes(event, eventTypes) })
  }
  const reminderButton = (event: ScheduleEvent) => editableReminders(event) && <button type="button" className="schedule-reminder-button" aria-label={`Напоминания: ${event.title}`} onClick={() => openReminders(event)}>Напоминания</button>
  const selectedDefaults = eventTypes.find((item) => item.id === (reminderEvent?.type ?? type))?.reminderMinutes ?? []
  const submitReminders = (event: FormEvent) => {
    event.preventDefault()
    if (!reminderEvent) return
    try {
      const reminderMinutes = validateReminderMinutes(reminders.reminderMode === 'type' ? selectedDefaults : reminders.reminderMinutes)
      dispatch(updateEventRemindersRequest({ id: reminderEvent.id, ...reminders, reminderMinutes }))
      setReminderSubmitted(true)
    } catch (reason) { setReminderError((reason as Error).message) }
  }
  const submit = (event: FormEvent) => {
    event.preventDefault()
    const selectedType = eventTypes.find((item) => item.id === type)
    if (!selectedType || (!isBackendMode && category !== 'personal' && !eventProjectId)) return
    if (!title.trim() || !description.trim()) { setReminderError('Укажите название и описание события'); return }
    let reminderMinutes: number[]
    try { reminderMinutes = validateReminderMinutes(reminders.reminderMode === 'type' ? selectedDefaults : reminders.reminderMinutes) } catch (reason) { setReminderError((reason as Error).message); return }
    dispatch(createScheduleEventRequest({
      ...(category === 'personal' ? {} : { projectId: eventProjectId }), title: title.trim(), description: description.trim(),
      startsAt: `${date}T${time}:00`, endsAt: endTime ? `${date}T${endTime}:00` : undefined,
      type, typeLabel: selectedType.label, typeColor: selectedType.color,
      ...(isBackendMode && category === 'group' ? { group: eventGroup } : {}), reminder: 'none', ...reminders, reminderMinutes, category, ownerId: user.id, location: location.trim() || undefined, author: user.name,
    }))
    setReminderError(''); setEventSubmitted(true)
  }

  return <main className="schedule-page"><div className="container" ref={scheduleRef}>
    <div className="schedule-page__heading">
      <h1>Расписание</h1>
      {<button className="button" onClick={() => { setDate(selectedDate); setEventSubmitted(false); setReminderEvent(null); setReminderError(''); setReminders({ reminderMode: 'type', reminderMinutes: eventTypes.find((item) => item.id === type)?.reminderMinutes ?? [] }); setCategory(user.role === 'lead' ? 'team' : 'personal'); setShowForm(true) }}><Icon name="plus" /> Добавить событие</button>}
    </div>
    {slider.mobile && <button type="button" className="schedule-filters-toggle" aria-expanded={filtersOpen} aria-controls={filtersId} onClick={() => setFiltersOpen(open => !open)}><span>Фильтры и настройки</span><Icon name="arrow-down" /></button>}
    <div id={filtersId} className="schedule-options" hidden={slider.mobile && !filtersOpen}>
    <section className="schedule-controls">
      <div className="schedule-period">
        <button onClick={() => movePeriod(-1)} aria-label="Предыдущий период"><Icon name="arrow-left" /></button>
        <label><span>{visibleMode === 'week' ? 'Неделя' : 'День'}</span><input type="date" required value={selectedDate} onChange={(event) => { if (event.target.value) setSelectedDate(event.target.value) }} /></label>
        <button onClick={() => movePeriod(1)} aria-label="Следующий период"><Icon name="arrow-right" /></button>
        <button className="schedule-today" onClick={() => setSelectedDate(localDate(new Date()))}>Сегодня</button>
      </div>
      <SearchField value={query} onChange={setQuery} placeholder="Поиск событий" />
      <div className="schedule-controls__views">
        {wideLayout && <div className="segmented" aria-label="Период расписания">
          <button aria-pressed={visibleMode === 'day'} className={visibleMode === 'day' ? 'is-active' : ''} onClick={() => setViewMode('day')}>1 день</button>
          <button aria-pressed={visibleMode === 'week'} className={visibleMode === 'week' ? 'is-active' : ''} onClick={() => setViewMode('week')}>Неделя</button>
        </div>}
        <div className="segmented" aria-label="Вид расписания">
          <button aria-pressed={layout === 'columns'} className={layout === 'columns' ? 'is-active' : ''} onClick={() => setLayout('columns')}>Карточки</button>
          <button aria-pressed={layout === 'rows'} className={layout === 'rows' ? 'is-active' : ''} onClick={() => setLayout('rows')}>В строку</button>
        </div>
      </div>
    </section>
    <div className="schedule-toolbar">
      <select aria-label="Расписание участника или группы" value={scope} onChange={(event) => setScope(event.target.value)}>
        <option value="mine">Моё расписание</option>
        <optgroup label="Учебные группы">{isBackendMode ? <>{groups.map(group => <option key={group.id} value={`group:${group.groupCode}`}>{group.name}</option>)}<option value="group:both">Все группы</option></> : <><option value="group:203">Группа 203</option><option value="group:204">Группа 204</option><option value="group:both">Обе группы</option></>}</optgroup>
        <optgroup label="Участники">{members.filter((member) => member.active && member.id !== user.id).map((member) => <option value={`user:${member.id}`} key={member.id}>{member.name}</option>)}</optgroup>
      </select>
      <fieldset className="schedule-category-filters">
        <legend>Категории событий</legend>
        <div className="schedule-category-filters__options">
          <label className={categoryFilters.length === categoryOptions.length ? 'is-selected' : ''}>
            <input type="checkbox" checked={categoryFilters.length === categoryOptions.length} ref={input => { if (input) input.indeterminate = categoryFilters.length > 0 && categoryFilters.length < categoryOptions.length }} onChange={event => setCategoryFilters(event.target.checked ? categoryOptions.map(option => option.value) : [])} />Все
          </label>
          {categoryOptions.map(option => <label key={option.value} className={categoryFilters.includes(option.value) ? 'is-selected' : ''}>
            <input type="checkbox" checked={categoryFilters.includes(option.value)} onChange={event => { const checked = event.target.checked; setCategoryFilters(current => checked ? [...current, option.value] : current.filter(value => value !== option.value)) }} />{option.label}
          </label>)}
        </div>
      </fieldset>
    </div>
    </div>
    {(visibleMode === 'day' || layout === 'rows') && <nav className="schedule-days" aria-label="Выбор дня недели">
      {weekDays.map((day) => <button key={day} className={day === selectedDate ? 'is-active' : ''} aria-pressed={day === selectedDate} onClick={() => { setSelectedDate(day); if (visibleMode === 'week' && layout === 'rows') document.getElementById(`schedule-day-${day}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' }) }}>
        <strong>{new Date(`${day}T12:00:00`).toLocaleDateString('ru-RU', { weekday: 'short' })}</strong>
        <span>{new Date(`${day}T12:00:00`).toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' })}</span>
      </button>)}
    </nav>}
    {sourceMessage && <p role="status" className="muted">{sourceMessage}</p>}
    {!showForm && !reminderEvent && (error || reminderError) && <div className="error-banner" role="alert">{error || reminderError}</div>}
    {loading ? <div className="loader">Загружаем расписание…</div> : <div ref={slider.viewportRef} className={slider.className} style={slider.viewportStyle} {...slider.handlers}><section className={`${layout === 'rows' ? 'agenda' : `schedule-grid schedule-grid--${visibleMode}`} ${slider.mobile ? 'schedule-slider__track' : ''}`} style={slider.trackStyle} onTransitionEnd={slider.onTransitionEnd} aria-label={visibleMode === 'week' ? 'События недели' : 'События дня'}>
      {visibleDays.map((day) => {
        const dayEvents = filteredEvents.filter((event) => localDate(new Date(event.startsAt)) === day)
        return <section className={`${layout === 'rows' ? 'agenda-day' : 'schedule-day'} ${day === selectedDate ? 'is-selected' : ''}`} aria-hidden={slider.mobile && day !== selectedDate ? true : undefined} key={day} id={`schedule-day-${day}`}>
          {layout === 'rows' ? <div className="agenda-day__date"><strong>{new Date(`${day}T12:00:00`).toLocaleDateString('ru-RU', { day: '2-digit' })}</strong><span>{new Date(`${day}T12:00:00`).toLocaleDateString('ru-RU', slider.mobile ? { month: 'short' } : { month: 'short', weekday: 'long' })}</span></div> : <h2>{visibleMode === 'week' && <span>{new Date(`${day}T12:00:00`).toLocaleDateString('ru-RU', { weekday: 'long' })}</span>}<span>{new Date(`${day}T12:00:00`).toLocaleDateString('ru-RU', { day: 'numeric', month: 'long' })}</span></h2>}
          <div className={layout === 'rows' ? 'agenda-day__items' : 'schedule-day__events'}>{dayEvents.map((event) => {
            const eventType = eventTypes.find((item) => item.id === event.type)
            const project = projects.find((item) => item.id === event.projectId)
            if (layout === 'rows') return <article className="agenda-item" style={{ borderLeftColor: eventType?.color ?? event.typeColor }} key={event.id}>
              <time dateTime={event.startsAt}>{formatTime(event.startsAt)}{event.endsAt && <small>— {formatTime(event.endsAt)}</small>}</time>
              <span><span className="agenda-item__labels">
                <small>{eventType?.label ?? event.typeLabel}</small>
                {!isBackendMode && eventCategory(event) !== 'personal' && <small className="agenda-item__project"><i style={{ background: project?.color }} />{project?.name ?? 'Проект'}</small>}
                {event.group && eventCategory(event) !== 'personal' && <small className="agenda-item__group">{event.group === 'both' ? 'Группы 203 и 204' : `Группа ${event.group}`}</small>}
              </span><strong>{event.title}</strong><p>{event.description}</p>{reminderButton(event)}</span>
              {event.location && <em>{event.location}</em>}
            </article>
            return <article className="schedule-event" key={event.id}>
              <time dateTime={event.startsAt}>{formatTime(event.startsAt)}{event.endsAt && ` — ${formatTime(event.endsAt)}`}</time>
              <div className="schedule-event__card" style={{ borderLeftColor: eventType?.color ?? event.typeColor }}>
                <span className="schedule-event__type">{eventType?.label ?? event.typeLabel}</span>
                <h3>{event.title}</h3><p>{event.description}</p>
                {event.location && <span>{event.location}</span>}
                {!isBackendMode && eventCategory(event) !== 'personal' && <span className="schedule-event__project"><i style={{ background: project?.color }} />{project?.name ?? 'Проект'}</span>}
                {event.group && eventCategory(event) !== 'personal' && <small>{event.group === 'both' ? 'Группы 203 и 204' : `Группа ${event.group}`}</small>}
                {reminderButton(event)}
              </div>
            </article>
          })}{!dayEvents.length && <p className={layout === 'rows' ? 'agenda-day__empty' : 'schedule-day__empty'}>Нет событий</p>}</div>
        </section>
      })}
    </section></div>}
  </div>
  {showForm && <FormModal title="Новое событие" onClose={() => { if (!saving) { setShowForm(false); setEventSubmitted(false) } }}>
    <form className="schedule-form schedule-form--modal" onSubmit={submit}>
      <label>Название<input value={title} onChange={(event) => setTitle(event.target.value)} required /></label>
      <label>Описание<textarea value={description} onChange={(event) => setDescription(event.target.value)} required /></label>
      <div className="schedule-form__grid">
        {!isBackendMode && category !== 'personal' && <label>Проект<select value={eventProjectId} onChange={(event) => setEventProjectId(event.target.value)} required>{projects.map((project) => <option value={project.id} key={project.id}>{project.name}</option>)}</select></label>}
        <label>Дата<input type="date" value={date} onChange={(event) => setDate(event.target.value)} required /></label>
        <label>Начало<input type="time" value={time} onChange={(event) => setTime(event.target.value)} required /></label>
        <label><span>Окончание <small>(необязательно)</small></span><input type="time" value={endTime} onChange={(event) => setEndTime(event.target.value)} min={time} /></label>
        <label>Категория<select value={category} onChange={(event) => setCategory(event.target.value as EventCategory)}><option value="personal">Личные</option><option value="team">Командные</option><option value="group">Групповые</option></select></label>
        {isBackendMode && category === 'group' && <label>Учебная группа<select required value={eventGroup} onChange={event => setEventGroup(event.target.value)}><option value="">Выберите группу</option>{groups.map(group => <option key={group.id} value={group.groupCode}>{group.name}</option>)}</select></label>}
        <label>Тип<select value={type} onChange={(event) => setType(event.target.value)} required>{eventTypes.map((item) => <option value={item.id} key={item.id}>{item.label}</option>)}</select></label>
        <label>Место<input value={location} onChange={(event) => setLocation(event.target.value)} placeholder="Необязательно" /></label>
      </div>
      <EventReminderEditor value={reminders} defaults={selectedDefaults} onChange={setReminders} />
      {(reminderError || error) && <p className="error-banner" role="alert">{reminderError || error}</p>}
      <button className="button" disabled={saving || (!isBackendMode && category !== 'personal' && !eventProjectId) || !type}>Добавить</button>
    </form>
  </FormModal>}
  {reminderEvent && <FormModal title="Напоминания события" onClose={() => setReminderEvent(null)}><form className="management-form" onSubmit={submitReminders}><h3>{reminderEvent.title}</h3><EventReminderEditor value={reminders} defaults={selectedDefaults} onChange={setReminders} />{(reminderError || error) && <p className="error-banner" role="alert">{reminderError || error}</p>}<button className="button" disabled={saving}>Сохранить</button></form></FormModal>}</main>
}

export default SchedulePage
