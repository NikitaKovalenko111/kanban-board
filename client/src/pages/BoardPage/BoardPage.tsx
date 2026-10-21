import { useEffect, useMemo, useRef, useState, type CSSProperties, type JSX, type TouchEvent } from 'react'
import type React from 'react'
import { useNavigate } from 'react-router-dom'
import AddTaskModal from '../../components/AddTaskModal/AddTaskModal'
import BoardElement from '../../components/BoardElement/BoardElement'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { loadTasksRequest } from '../../store/slices/tasksSlice'
import type { Task, TaskStatus } from '../../types'

type PropsType = {}

const BoardPage: React.FC<PropsType> = (): JSX.Element => {
    const dispatch = useAppDispatch()
    const navigate = useNavigate()
    const { items, loading, error } = useAppSelector((state) => state.tasks)
    const user = useAppSelector((state) => state.auth.user)!
    const activeProjectId = useAppSelector((state) => state.projects.activeProjectId)
    const activeProject = useAppSelector((state) => state.projects.items.find((project) => project.id === state.projects.activeProjectId))
    const [drawerOpen, setDrawerOpen] = useState(false)
    const [selectedTask, setSelectedTask] = useState<Task | null>(null)
    const [query, setQuery] = useState('')
    const [activeColumn, setActiveColumn] = useState(0)
    const touchStart = useRef<number | null>(null)

    useEffect(() => { if (!items.length) dispatch(loadTasksRequest()) }, [dispatch, items.length])
    const allProjectsSelected = activeProjectId === 'all'
    const filtered = useMemo(() => items.filter((task) => (allProjectsSelected || task.projectId === activeProjectId) && `${task.taskName} ${task.taskDesc}`.toLowerCase().includes(query.toLowerCase())), [activeProjectId, allProjectsSelected, items, query])
    const latestDoneIds = useMemo(() => new Set([...items].filter((task) => (allProjectsSelected || task.projectId === activeProjectId) && task.taskStatus === 'done').sort((a, b) => (b.completedAt ?? '').localeCompare(a.completedAt ?? '')).slice(0, 5).map((task) => task.taskId)), [activeProjectId, allProjectsSelected, items])
    const columns: { status: TaskStatus; name: string; accent: string }[] = [
      { status: 'waiting', name: 'Ожидание', accent: '#f2b84b' },
      { status: 'working', name: 'В работе', accent: '#6c8cff' },
      { status: 'checking', name: 'На проверке', accent: '#9b7ae8' },
      { status: 'done', name: 'Выполнено', accent: '#55b895' },
    ]

    const openTask = (task: Task) => { setSelectedTask(task); setDrawerOpen(true) }
    const openCreate = () => { setSelectedTask(null); setDrawerOpen(true) }
    const showColumn = (index: number) => setActiveColumn(Math.max(0, Math.min(columns.length - 1, index)))
    const handleTouchStart = (event: TouchEvent) => { touchStart.current = event.touches[0].clientX }
    const handleTouchEnd = (event: TouchEvent) => {
      if (touchStart.current === null) return
      const distance = event.changedTouches[0].clientX - touchStart.current
      if (Math.abs(distance) > 45) showColumn(activeColumn + (distance > 0 ? 1 : -1))
      touchStart.current = null
    }
    return (
        <main className="board-page">
            <div className="container board-page__container">
                <div className="board-page__heading">
                  <div><span className="eyebrow">{allProjectsSelected ? 'Все проекты команды' : activeProject?.name ?? 'Проект'}</span><h1>Доска задач</h1></div>
                  {user.role === 'lead' && !allProjectsSelected && <button className="button" onClick={openCreate}>＋ Новая задача</button>}
                </div>
                <div className="board-page__toolbar"><label className="search">⌕<input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Поиск по задачам" /></label><div className="team-stack"><span>АЛ</span><span>МВ</span><span>ИГ</span><small>+2</small></div></div>
                {error && <div className="error-banner">{error}</div>}
                <div className="board-slider" onTouchStart={handleTouchStart} onTouchEnd={handleTouchEnd}>
                  <div className="board-page__tables">
                    <div className="board-page__track" style={{ '--slide-index': activeColumn } as CSSProperties}>
                    {loading ? <div className="loader">Загружаем доску…</div> : columns.map((column) => {
                      const columnTasks = filtered.filter((task) => task.taskStatus === column.status)
                      const visibleTasks = column.status === 'done' ? columnTasks.filter((task) => latestDoneIds.has(task.taskId)).sort((a, b) => (b.completedAt ?? '').localeCompare(a.completedAt ?? '')) : columnTasks
                      return <BoardElement key={column.status} name={column.name} accent={column.accent} tasks={visibleTasks} onTaskClick={openTask} showProject={allProjectsSelected} onAdd={column.status === 'waiting' && user.role === 'lead' && !allProjectsSelected ? openCreate : undefined} onHistory={column.status === 'done' ? () => navigate('/history') : undefined} />
                    })}
                    </div>
                  </div>
                  {!loading && <div className="board-slider__controls"><button onClick={() => showColumn(activeColumn - 1)} disabled={activeColumn === 0} aria-label="Предыдущая колонка">←</button><div>{columns.map((column, index) => <button key={column.status} className={index === activeColumn ? 'is-active' : ''} onClick={() => showColumn(index)} aria-label={`Открыть колонку ${column.name}`} />)}</div><button onClick={() => showColumn(activeColumn + 1)} disabled={activeColumn === columns.length - 1} aria-label="Следующая колонка">→</button></div>}
                </div>
            </div>
            {drawerOpen && <AddTaskModal task={selectedTask} onClose={() => setDrawerOpen(false)} />}
        </main>
    )
}

export default BoardPage
