import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import FormModal from '../../components/FormModal/FormModal'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { createScheduleEventRequest, loadScheduleRequest } from '../../store/slices/scheduleSlice'
import type { ReminderScenario, StudyGroup } from '../../types'
import { loadEventTypes } from '../../utils/eventTypes'
import { useSearchParams } from 'react-router-dom'

const reminderLabels: Record<ReminderScenario, string> = { none: 'Без напоминания', 'at-start': 'В момент начала', '15-min': 'За 15 минут', '1-hour': 'За 1 час', '1-day': 'За 1 день' }
const localDate = (day: Date) => `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, '0')}-${String(day.getDate()).padStart(2, '0')}`
const formatTime = (value: string) => new Date(value).toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })

const SchedulePage = () => {
  const dispatch = useAppDispatch()
  const [searchParams] = useSearchParams()
  const user = useAppSelector((state) => state.auth.user)!
  const projects = useAppSelector((state) => state.projects.items)
  const activeProjectId = useAppSelector((state) => state.projects.activeProjectId)
  const { items: events, loading, saving, error } = useAppSelector((state) => state.schedule)
  const [showForm, setShowForm] = useState(false)
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
  const [groupFilter, setGroupFilter] = useState<StudyGroup>('both')
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [date, setDate] = useState(() => localDate(new Date()))
  const [time, setTime] = useState('10:00')
  const [endTime, setEndTime] = useState('')
  const [eventTypes] = useState(loadEventTypes)
  const [type, setType] = useState(eventTypes[0]?.id ?? '')
  const [group, setGroup] = useState<StudyGroup>('both')
  const [reminder, setReminder] = useState<ReminderScenario>('15-min')
  const [eventProjectId, setEventProjectId] = useState(activeProjectId === 'all' ? '' : activeProjectId)
  const [location, setLocation] = useState('')

  useEffect(() => { dispatch(loadScheduleRequest('all')) }, [dispatch])
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
  const visibleDays = visibleMode === 'week' ? weekDays : [selectedDate]
  const filteredEvents = events.filter((event) => {
    const projectName = projects.find((project) => project.id === event.projectId)?.name ?? ''
    return (groupFilter === 'both' || event.group === 'both' || event.group === groupFilter)
      && `${event.title} ${event.description} ${projectName}`.toLowerCase().includes(query.toLowerCase())
  }).sort((a, b) => a.startsAt.localeCompare(b.startsAt))

  const movePeriod = (direction: number) => {
    const next = new Date(`${selectedDate}T12:00:00`)
    next.setDate(next.getDate() + direction * (visibleMode === 'week' ? 7 : 1))
    setSelectedDate(localDate(next))
  }
  const submit = (event: FormEvent) => {
    event.preventDefault()
    const selectedType = eventTypes.find((item) => item.id === type)
    if (!selectedType) return
    dispatch(createScheduleEventRequest({
      projectId: eventProjectId, title: title.trim(), description: description.trim(),
      startsAt: `${date}T${time}:00`, endsAt: endTime ? `${date}T${endTime}:00` : undefined,
      type, typeLabel: selectedType.label, typeColor: selectedType.color,
      group, reminder, location: location.trim() || undefined, author: user.name,
    }))
    setSelectedDate(date)
    setTitle(''); setDescription(''); setLocation(''); setEndTime(''); setShowForm(false)
  }

  return <main className="schedule-page"><div className="container" ref={scheduleRef}>
    <div className="schedule-page__heading">
      <div><span className="eyebrow">Все проекты команды</span><h1>Расписание</h1></div>
      {user.role === 'lead' && <button className="button" onClick={() => { setDate(selectedDate); setShowForm(true) }}>＋ Добавить событие</button>}
    </div>
    <section className="schedule-controls">
      <div className="schedule-period">
        <button onClick={() => movePeriod(-1)} aria-label="Предыдущий период">←</button>
        <label><span>{visibleMode === 'week' ? 'Неделя' : 'День'}</span><input type="date" required value={selectedDate} onChange={(event) => { if (event.target.value) setSelectedDate(event.target.value) }} /></label>
        <button onClick={() => movePeriod(1)} aria-label="Следующий период">→</button>
        <button className="schedule-today" onClick={() => setSelectedDate(localDate(new Date()))}>Сегодня</button>
      </div>
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
      <label className="search">⌕<input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Поиск событий" /></label>
      <div className="schedule-toolbar__filters"><select value={groupFilter} onChange={(event) => setGroupFilter(event.target.value as StudyGroup)} aria-label="Учебная группа">
        <option value="both">Обе группы</option><option value="203">Группа 203</option><option value="204">Группа 204</option>
      </select></div>
    </div>
    <nav className="schedule-days" aria-label="Выбор дня недели">
      {weekDays.map((day) => <button key={day} className={day === selectedDate ? 'is-active' : ''} aria-pressed={day === selectedDate} onClick={() => setSelectedDate(day)}>
        <strong>{new Date(`${day}T12:00:00`).toLocaleDateString('ru-RU', { weekday: 'short' })}</strong>
        <span>{new Date(`${day}T12:00:00`).toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' })}</span>
      </button>)}
    </nav>
    {error && <div className="error-banner">{error}</div>}
    {loading ? <div className="loader">Загружаем расписание…</div> : <section className={layout === 'rows' ? 'agenda' : `schedule-grid schedule-grid--${visibleMode}`} aria-label={visibleMode === 'week' ? 'События недели' : 'События дня'}>
      {visibleDays.map((day) => {
        const dayEvents = filteredEvents.filter((event) => event.startsAt.slice(0, 10) === day)
        return <section className={`${layout === 'rows' ? 'agenda-day' : 'schedule-day'} ${day === selectedDate ? 'is-selected' : ''}`} key={day}>
          {layout === 'rows' ? <div className="agenda-day__date"><strong>{new Date(`${day}T12:00:00`).toLocaleDateString('ru-RU', { day: '2-digit' })}</strong><span>{new Date(`${day}T12:00:00`).toLocaleDateString('ru-RU', { month: 'short', weekday: 'long' })}</span></div> : <h2>{new Date(`${day}T12:00:00`).toLocaleDateString('ru-RU', { day: 'numeric', month: 'long' })}</h2>}
          <div className={layout === 'rows' ? 'agenda-day__items' : 'schedule-day__events'}>{dayEvents.map((event) => {
            const eventType = eventTypes.find((item) => item.id === event.type)
            const project = projects.find((item) => item.id === event.projectId)
            if (layout === 'rows') return <article className="agenda-item" style={{ borderLeftColor: eventType?.color ?? event.typeColor }} key={event.id}>
              <time dateTime={event.startsAt}>{formatTime(event.startsAt)}{event.endsAt && <small>— {formatTime(event.endsAt)}</small>}</time>
              <span><span className="agenda-item__labels">
                <small style={{ color: eventType?.color ?? event.typeColor }}>{eventType?.label ?? event.typeLabel}</small>
                <small className="agenda-item__project"><i style={{ background: project?.color }} />{project?.name ?? 'Проект'}</small>
                <small className="agenda-item__group">{event.group === 'both' ? 'Группы 203 и 204' : `Группа ${event.group}`}</small>
              </span><strong>{event.title}</strong><p>{event.description}</p></span>
              {event.location && <em>{event.location}</em>}
            </article>
            return <article className="schedule-event" key={event.id}>
              <time dateTime={event.startsAt}>{formatTime(event.startsAt)}{event.endsAt && ` — ${formatTime(event.endsAt)}`}</time>
              <div className="schedule-event__card" style={{ borderLeftColor: eventType?.color ?? event.typeColor }}>
                <span className="schedule-event__type" style={{ color: eventType?.color ?? event.typeColor }}>{eventType?.label ?? event.typeLabel}</span>
                <h3>{event.title}</h3><p>{event.description}</p>
                {event.location && <span>{event.location}</span>}
                <span className="schedule-event__project"><i style={{ background: project?.color }} />{project?.name ?? 'Проект'}</span>
                <small>{event.group === 'both' ? 'Группы 203 и 204' : `Группа ${event.group}`}</small>
              </div>
            </article>
          })}{!dayEvents.length && <p className={layout === 'rows' ? 'agenda-day__empty' : 'schedule-day__empty'}>Нет событий</p>}</div>
        </section>
      })}
    </section>}
  </div>
  {showForm && <FormModal title="Новое событие" onClose={() => setShowForm(false)}>
    <form className="schedule-form schedule-form--modal" onSubmit={submit}>
      <label>Название<input value={title} onChange={(event) => setTitle(event.target.value)} required /></label>
      <label>Описание<textarea value={description} onChange={(event) => setDescription(event.target.value)} required /></label>
      <div className="schedule-form__grid">
        <label>Проект<select value={eventProjectId} onChange={(event) => setEventProjectId(event.target.value)} required>{projects.map((project) => <option value={project.id} key={project.id}>{project.name}</option>)}</select></label>
        <label>Дата<input type="date" value={date} onChange={(event) => setDate(event.target.value)} required /></label>
        <label>Начало<input type="time" value={time} onChange={(event) => setTime(event.target.value)} required /></label>
        <label><span>Окончание <small>(необязательно)</small></span><input type="time" value={endTime} onChange={(event) => setEndTime(event.target.value)} min={time} /></label>
        <label>Тип<select value={type} onChange={(event) => setType(event.target.value)} required>{eventTypes.map((item) => <option value={item.id} key={item.id}>{item.label}</option>)}</select></label>
        <label>Учебная группа<select value={group} onChange={(event) => setGroup(event.target.value as StudyGroup)}><option value="203">Группа 203</option><option value="204">Группа 204</option><option value="both">Обе группы</option></select></label>
        <label>Напоминание<select value={reminder} onChange={(event) => setReminder(event.target.value as ReminderScenario)}>{Object.entries(reminderLabels).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></label>
        <label>Место<input value={location} onChange={(event) => setLocation(event.target.value)} placeholder="Необязательно" /></label>
      </div>
      <button className="button" disabled={saving || !eventProjectId || !type}>Добавить</button>
    </form>
  </FormModal>}</main>
}

export default SchedulePage
