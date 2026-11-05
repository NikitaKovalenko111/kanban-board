import { displayId } from '../../utils/displayId'
import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useProjectSelection } from '../../store/useProjectSelection'
import AddTaskModal from '../../components/AddTaskModal/AddTaskModal'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { loadTasksRequest } from '../../store/slices/tasksSlice'
import type { Task } from '../../types'

type Period = 'all' | 'week' | 'month'
type SortOrder = 'newest' | 'oldest'

const HistoryPage = () => {
  const dispatch = useAppDispatch()
  const { items, loading, error } = useAppSelector((state) => state.tasks)
  const activeProjectId = useProjectSelection('history')
  const [params] = useSearchParams()
  const query = params.get('q') ?? ''
  const [assignee, setAssignee] = useState('all')
  const [period, setPeriod] = useState<Period>('all')
  const [sortOrder, setSortOrder] = useState<SortOrder>('newest')
  const [selectedTask, setSelectedTask] = useState<Task | null>(null)

  useEffect(() => { if (!items.length) dispatch(loadTasksRequest()) }, [dispatch, items.length])

  const completed = useMemo(() => items.filter((task) => (activeProjectId === 'all' || task.projectId === activeProjectId) && task.taskStatus === 'done'), [activeProjectId, items])
  const assignees = useMemo(() => [...new Set(completed.map((task) => task.assignee))].sort(), [completed])
  const filtered = useMemo(() => {
    const now = Date.now()
    const maxAge = period === 'week' ? 7 : period === 'month' ? 30 : null
    return completed
      .filter((task) => `${task.taskName} ${task.taskDesc} ${task.assignee}`.toLowerCase().includes(query.toLowerCase()))
      .filter((task) => assignee === 'all' || task.assignee === assignee)
      .filter((task) => !maxAge || (task.completedAt && now - new Date(task.completedAt).getTime() <= maxAge * 86400000))
      .sort((a, b) => sortOrder === 'newest' ? (b.completedAt ?? '').localeCompare(a.completedAt ?? '') : (a.completedAt ?? '').localeCompare(b.completedAt ?? ''))
  }, [assignee, completed, period, query, sortOrder])

  return <main className="history-page"><div className="container"><div className="page-heading"><div><h1>История задач</h1></div><span className="page-heading__count">{completed.length} выполнено</span></div>{error && <p role="alert" className="error-banner">{error}</p>}<section className="history-filters"><select value={assignee} onChange={(event) => setAssignee(event.target.value)}><option value="all">Все исполнители</option>{assignees.map((name) => <option key={name} value={name}>{name}</option>)}</select><select value={period} onChange={(event) => setPeriod(event.target.value as Period)}><option value="all">За всё время</option><option value="week">За неделю</option><option value="month">За месяц</option></select><select value={sortOrder} onChange={(event) => setSortOrder(event.target.value as SortOrder)}><option value="newest">Сначала новые</option><option value="oldest">Сначала старые</option></select></section>{loading ? <div className="loader">Загружаем историю…</div> : <section className="history-list"><div className="history-list__head"><span>Задача</span><span>Исполнитель</span><span>Завершена</span></div>{filtered.map((task) => <button className="history-row" key={task.taskId} onClick={() => setSelectedTask(task)}><span className="history-row__task"><small>#{displayId(task.taskId)}</small><strong>{task.taskName}</strong><small>{task.taskDesc}</small></span><span>{task.assignee}</span><span>{task.completedAt ? new Date(task.completedAt).toLocaleString('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—'}</span></button>)}{!filtered.length && <div className="empty-state">По заданным фильтрам задач не найдено</div>}</section>}</div>{selectedTask && <AddTaskModal task={selectedTask} onClose={() => setSelectedTask(null)} />}</main>
}

export default HistoryPage
