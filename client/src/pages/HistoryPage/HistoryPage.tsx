import { useEffect, useMemo, useState } from 'react'
import AddTaskModal from '../../components/AddTaskModal/AddTaskModal'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { loadTasksRequest } from '../../store/slices/tasksSlice'
import type { Task } from '../../types'

type Period = 'all' | 'week' | 'month'
type SortOrder = 'newest' | 'oldest'

const HistoryPage = () => {
  const dispatch = useAppDispatch()
  const { items, loading } = useAppSelector((state) => state.tasks)
  const [query, setQuery] = useState('')
  const [assignee, setAssignee] = useState('all')
  const [period, setPeriod] = useState<Period>('all')
  const [sortOrder, setSortOrder] = useState<SortOrder>('newest')
  const [selectedTask, setSelectedTask] = useState<Task | null>(null)

  useEffect(() => { if (!items.length) dispatch(loadTasksRequest()) }, [dispatch, items.length])

  const completed = useMemo(() => items.filter((task) => task.taskStatus === 'done'), [items])
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

  return <main className="history-page"><div className="container"><div className="page-heading"><div><span className="eyebrow">Архив</span><h1>История задач</h1></div><span className="page-heading__count">{completed.length} выполнено</span></div><section className="history-filters"><label className="search">⌕<input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Поиск по истории" /></label><select value={assignee} onChange={(event) => setAssignee(event.target.value)}><option value="all">Все исполнители</option>{assignees.map((name) => <option key={name} value={name}>{name}</option>)}</select><select value={period} onChange={(event) => setPeriod(event.target.value as Period)}><option value="all">За всё время</option><option value="week">За 7 дней</option><option value="month">За 30 дней</option></select><select value={sortOrder} onChange={(event) => setSortOrder(event.target.value as SortOrder)}><option value="newest">Сначала новые</option><option value="oldest">Сначала старые</option></select></section>{loading ? <div className="loader">Загружаем историю…</div> : <section className="history-list"><div className="history-list__head"><span>Задача</span><span>Исполнитель</span><span>Завершена</span><span>Результат</span></div>{filtered.map((task) => <button className="history-row" key={task.taskId} onClick={() => setSelectedTask(task)}><span className="history-row__task"><small>#{task.taskId}</small><strong>{task.taskName}</strong><small>{task.taskDesc}</small></span><span>{task.assignee}</span><span>{task.completedAt ? new Date(task.completedAt).toLocaleDateString('ru-RU') : '—'}</span><span>{task.resultAttachments.length} файл(а)</span></button>)}{!filtered.length && <div className="empty-state">По заданным фильтрам задач не найдено</div>}</section>}</div>{selectedTask && <AddTaskModal task={selectedTask} onClose={() => setSelectedTask(null)} />}</main>
}

export default HistoryPage
