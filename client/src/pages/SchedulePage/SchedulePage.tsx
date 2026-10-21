import { useEffect, useMemo, useState, type FormEvent } from 'react'
import AddTaskModal from '../../components/AddTaskModal/AddTaskModal'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { createScheduleEventRequest, loadScheduleRequest } from '../../store/slices/scheduleSlice'
import { loadTasksRequest } from '../../store/slices/tasksSlice'
import type { ScheduleEventType, Task } from '../../types'

const eventLabels: Record<ScheduleEventType, string> = { meeting: 'Встреча', milestone: 'Контрольная точка', class: 'Занятие', presentation: 'Презентация' }

interface AgendaItem { id: string; projectId: string; startsAt: string; title: string; description: string; label: string; kind: 'event' | 'deadline'; location?: string; task?: Task }

const SchedulePage = () => {
  const dispatch = useAppDispatch()
  const user = useAppSelector((state) => state.auth.user)!
  const activeProjectId = useAppSelector((state) => state.projects.activeProjectId)
  const projects = useAppSelector((state) => state.projects.items)
  const activeProject = projects.find((project) => project.id === activeProjectId)
  const allProjectsSelected = activeProjectId === 'all'
  const tasks = useAppSelector((state) => state.tasks.items)
  const { items: events, loading, saving, error } = useAppSelector((state) => state.schedule)
  const [showForm, setShowForm] = useState(false)
  const [query, setQuery] = useState('')
  const [scope, setScope] = useState<'upcoming' | 'all'>('upcoming')
  const [selectedTask, setSelectedTask] = useState<Task | null>(null)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [date, setDate] = useState('2026-10-21')
  const [time, setTime] = useState('10:00')
  const [type, setType] = useState<ScheduleEventType>('meeting')
  const [location, setLocation] = useState('')

  useEffect(() => { if (activeProjectId) dispatch(loadScheduleRequest(activeProjectId)) }, [activeProjectId, dispatch])
  useEffect(() => { if (!tasks.length) dispatch(loadTasksRequest()) }, [dispatch, tasks.length])

  const agenda = useMemo(() => {
    const taskDeadlines: AgendaItem[] = tasks.filter((task) => (allProjectsSelected || task.projectId === activeProjectId) && task.taskDeadline).map((task) => ({ id: `task-${task.taskId}`, projectId: task.projectId, startsAt: `${task.taskDeadline}T23:59:00`, title: task.taskName, description: task.taskDesc, label: 'Дедлайн задачи', kind: 'deadline', task }))
    const projectEvents: AgendaItem[] = events.map((event) => ({ id: `event-${event.id}`, projectId: event.projectId, startsAt: event.startsAt, title: event.title, description: event.description, label: eventLabels[event.type], kind: 'event', location: event.location }))
    const today = new Date('2026-10-21T00:00:00').getTime()
    return [...projectEvents, ...taskDeadlines].filter((item) => scope === 'all' || new Date(item.startsAt).getTime() >= today).filter((item) => {
      const projectName = projects.find((project) => project.id === item.projectId)?.name ?? ''
      return `${item.title} ${item.description} ${projectName}`.toLowerCase().includes(query.toLowerCase())
    }).sort((a, b) => a.startsAt.localeCompare(b.startsAt))
  }, [activeProjectId, allProjectsSelected, events, projects, query, scope, tasks])

  const groups = useMemo(() => agenda.reduce<Record<string, AgendaItem[]>>((result, item) => { const key = item.startsAt.slice(0, 10); (result[key] ??= []).push(item); return result }, {}), [agenda])
  const submit = (event: FormEvent) => {
    event.preventDefault()
    dispatch(createScheduleEventRequest({ projectId: activeProjectId, title, description, startsAt: `${date}T${time}:00`, type, location: location || undefined, author: user.name }))
    setTitle(''); setDescription(''); setLocation(''); setShowForm(false)
  }

  return <main className="schedule-page"><div className="container"><div className="schedule-page__heading"><div><span className="eyebrow">{allProjectsSelected ? 'Все проекты команды' : activeProject?.name ?? 'Проект'}</span><h1>Расписание</h1></div>{user.role === 'lead' && !allProjectsSelected && <button className="button" onClick={() => setShowForm((value) => !value)}>{showForm ? 'Закрыть' : '＋ Добавить событие'}</button>}</div>{showForm && !allProjectsSelected && <form className="schedule-form" onSubmit={submit}><div className="schedule-form__heading"><h2>Новое событие</h2><span>Проект: {activeProject?.name}</span></div><label>Название<input value={title} onChange={(event) => setTitle(event.target.value)} required /></label><label>Описание<textarea value={description} onChange={(event) => setDescription(event.target.value)} required /></label><div className="schedule-form__grid"><label>Дата<input type="date" value={date} onChange={(event) => setDate(event.target.value)} required /></label><label>Время<input type="time" value={time} onChange={(event) => setTime(event.target.value)} required /></label><label>Тип<select value={type} onChange={(event) => setType(event.target.value as ScheduleEventType)}>{Object.entries(eventLabels).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></label><label>Место<input value={location} onChange={(event) => setLocation(event.target.value)} placeholder="Необязательно" /></label></div><button className="button" disabled={saving}>Добавить</button></form>}<div className="schedule-toolbar"><label className="search">⌕<input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Поиск в расписании" /></label><div className="segmented"><button className={scope === 'upcoming' ? 'is-active' : ''} onClick={() => setScope('upcoming')}>Предстоящие</button><button className={scope === 'all' ? 'is-active' : ''} onClick={() => setScope('all')}>Все</button></div></div>{error && <div className="error-banner">{error}</div>}{loading ? <div className="loader">Загружаем расписание…</div> : <section className="agenda">{Object.entries(groups).map(([day, dayItems]) => <div className="agenda-day" key={day}><div className="agenda-day__date"><strong>{new Date(`${day}T12:00:00`).toLocaleDateString('ru-RU', { day: '2-digit' })}</strong><span>{new Date(`${day}T12:00:00`).toLocaleDateString('ru-RU', { month: 'short', weekday: 'short' })}</span></div><div className="agenda-day__items">{dayItems.map((item) => { const project = projects.find((entry) => entry.id === item.projectId); return <button className={`agenda-item agenda-item--${item.kind}`} key={item.id} onClick={() => item.task && setSelectedTask(item.task)} disabled={!item.task}><time>{new Date(item.startsAt).toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })}</time><span><span className="agenda-item__labels"><small>{item.label}</small>{allProjectsSelected && <small className="agenda-item__project"><i style={{ background: project?.color ?? '#aab1be' }} />{project?.name ?? 'Неизвестный проект'}</small>}</span><strong>{item.title}</strong><p>{item.description}</p></span><em>{item.location ?? (item.kind === 'deadline' ? 'Открыть задачу →' : '')}</em></button> })}</div></div>)}{!agenda.length && <div className="empty-state">{allProjectsSelected ? 'В общем расписании пока нет событий' : 'В расписании этого проекта пока нет событий'}</div>}</section>}</div>{selectedTask && <AddTaskModal task={selectedTask} onClose={() => setSelectedTask(null)} />}</main>
}

export default SchedulePage
