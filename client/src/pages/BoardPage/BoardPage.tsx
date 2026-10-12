import { useEffect, useMemo, useState, type JSX } from 'react'
import type React from 'react'
import AddTaskModal from '../../components/AddTaskModal/AddTaskModal'
import BoardElement from '../../components/BoardElement/BoardElement'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { loadTasksRequest } from '../../store/slices/tasksSlice'
import type { Task, TaskStatus } from '../../types'

type PropsType = {}

const BoardPage: React.FC<PropsType> = (): JSX.Element => {
    const dispatch = useAppDispatch()
    const { items, loading, error } = useAppSelector((state) => state.tasks)
    const user = useAppSelector((state) => state.auth.user)!
    const [drawerOpen, setDrawerOpen] = useState(false)
    const [selectedTask, setSelectedTask] = useState<Task | null>(null)
    const [query, setQuery] = useState('')

    useEffect(() => { if (!items.length) dispatch(loadTasksRequest()) }, [dispatch, items.length])
    const filtered = useMemo(() => items.filter((task) => `${task.taskName} ${task.taskDesc}`.toLowerCase().includes(query.toLowerCase())), [items, query])
    const columns: { status: TaskStatus; name: string; accent: string }[] = [
      { status: 'waiting', name: 'Ожидание', accent: '#f2b84b' },
      { status: 'working', name: 'В работе', accent: '#6c8cff' },
      { status: 'checking', name: 'На проверке', accent: '#9b7ae8' },
      { status: 'done', name: 'Выполнено', accent: '#55b895' },
    ]

    const openTask = (task: Task) => { setSelectedTask(task); setDrawerOpen(true) }
    const openCreate = () => { setSelectedTask(null); setDrawerOpen(true) }
    return (
        <main className="board-page">
            <div className="container board-page__container">
                <div className="board-page__heading">
                  <div><span className="eyebrow">Веб-продукт</span><h1>Доска задач</h1><p>Следите за прогрессом команды в одном месте</p></div>
                  {user.role === 'lead' && <button className="button" onClick={openCreate}>＋ Новая задача</button>}
                </div>
                <div className="board-page__toolbar"><label className="search">⌕<input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Поиск по задачам" /></label><div className="team-stack"><span>АЛ</span><span>МВ</span><span>ИГ</span><small>+2</small></div></div>
                {error && <div className="error-banner">{error}</div>}
                <div className="board-page__tables">
                    {loading ? <div className="loader">Загружаем доску…</div> : columns.map((column) => <BoardElement key={column.status} name={column.name} accent={column.accent} tasks={filtered.filter((task) => task.taskStatus === column.status)} onTaskClick={openTask} onAdd={column.status === 'waiting' && user.role === 'lead' ? openCreate : undefined} />)}
                </div>
            </div>
            {drawerOpen && <AddTaskModal task={selectedTask} onClose={() => setDrawerOpen(false)} />}
        </main>
    )
}

export default BoardPage
