import Icon from '../../components/Icon/Icon'
import { useEffect, useMemo, useRef, useState, type CSSProperties, type JSX, type TouchEvent } from 'react'
import type React from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import AddTaskModal from '../../components/AddTaskModal/AddTaskModal'
import BoardElement from '../../components/BoardElement/BoardElement'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { loadTasksRequest } from '../../store/slices/tasksSlice'
import { useProjectSelection } from '../../store/useProjectSelection'
import type { Task, TaskStatus } from '../../types'

type PropsType = {}

const BoardPage: React.FC<PropsType> = (): JSX.Element => {
    const dispatch = useAppDispatch()
    const navigate = useNavigate()
    const [searchParams, setSearchParams] = useSearchParams()
    const { items, loading, error } = useAppSelector((state) => state.tasks)
    const user = useAppSelector((state) => state.auth.user)!
    const activeProjectId = useProjectSelection('board')
    const [drawerOpen, setDrawerOpen] = useState(false)
    const [selectedTask, setSelectedTask] = useState<Task | null>(null)
    const query = searchParams.get('q') ?? ''
    const [activeColumn, setActiveColumn] = useState(0)
    const touchStart = useRef<{ x: number; y: number } | null>(null)
    const swiped = useRef(false)

    useEffect(() => { if (!items.length) dispatch(loadTasksRequest()) }, [dispatch, items.length])
    const notificationTaskId = Number(searchParams.get('task'))
    const notificationTask = items.find((task) => task.taskId === notificationTaskId)
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
    const handleTouchStart = (event: TouchEvent) => {
      swiped.current = false
      touchStart.current = event.touches.length === 1 ? { x: event.touches[0].clientX, y: event.touches[0].clientY } : null
    }
    const handleTouchEnd = (event: TouchEvent) => {
      if (touchStart.current === null) return
      const distance = event.changedTouches[0].clientX - touchStart.current.x
      const vertical = event.changedTouches[0].clientY - touchStart.current.y
      if (window.matchMedia('(max-width: 760px)').matches && Math.abs(distance) > 45 && Math.abs(distance) > Math.abs(vertical) * 1.3) {
        swiped.current = true
        showColumn(activeColumn + (distance < 0 ? 1 : -1))
      }
      touchStart.current = null
    }
    return (
        <main className="board-page">
            <div className="container board-page__container">
                <div className="board-page__heading">
                  <h1>Kanban-доска</h1>
                  {user.role === 'lead' && !allProjectsSelected && <button className="button" onClick={openCreate}><Icon name="plus" /> Новая задача</button>}
                </div>
                {error && <div className="error-banner">{error}</div>}
                <div className="board-slider" onTouchStart={handleTouchStart} onTouchEnd={handleTouchEnd} onTouchCancel={() => { touchStart.current = null }} onClickCapture={(event) => { if (swiped.current) { event.preventDefault(); event.stopPropagation(); swiped.current = false } }}>
                  <div className="board-page__tables">
                    <div className="board-page__track" style={{ '--slide-index': activeColumn } as CSSProperties}>
                    {loading ? <div className="loader">Загружаем доску…</div> : columns.map((column) => {
                      const columnTasks = filtered.filter((task) => task.taskStatus === column.status)
                      const visibleTasks = column.status === 'done' ? columnTasks.filter((task) => latestDoneIds.has(task.taskId)).sort((a, b) => (b.completedAt ?? '').localeCompare(a.completedAt ?? '')) : columnTasks
                      return <BoardElement key={column.status} name={column.name} accent={column.accent} tasks={visibleTasks} onTaskClick={openTask} showProject={allProjectsSelected} onHistory={column.status === 'done' ? () => navigate('/history') : undefined} />
                    })}
                    </div>
                  </div>
                  {!loading && <div className="board-slider__controls"><button onClick={() => showColumn(activeColumn - 1)} disabled={activeColumn === 0} aria-label="Предыдущая колонка"><Icon name="arrow-left" /></button><div>{columns.map((column, index) => <button key={column.status} className={index === activeColumn ? 'is-active' : ''} onClick={() => showColumn(index)} aria-label={`Открыть колонку ${column.name}`} />)}</div><button onClick={() => showColumn(activeColumn + 1)} disabled={activeColumn === columns.length - 1} aria-label="Следующая колонка"><Icon name="arrow-right" /></button></div>}
                </div>
            </div>
            {(drawerOpen || notificationTask) && <AddTaskModal task={notificationTask ?? selectedTask} onClose={() => { setDrawerOpen(false); if (notificationTaskId) setSearchParams({}, { replace: true }) }} />}
        </main>
    )
}

export default BoardPage
