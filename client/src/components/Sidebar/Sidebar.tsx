import { useEffect, useState } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { logout } from '../../store/slices/authSlice'
import UserAvatar from '../UserAvatar/UserAvatar'
import NotificationsModal, { type NotificationItem } from './NotificationsModal'
import { loadTasksRequest } from '../../store/slices/tasksSlice'
import { loadScheduleRequest } from '../../store/slices/scheduleSlice'

interface Props { open: boolean; onClose: () => void }

const Sidebar = ({ open, onClose }: Props) => {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const user = useAppSelector((state) => state.auth.user)!
  const activeProjectId = useAppSelector((state) => state.projects.activeProjectId)
  const tasks = useAppSelector((state) => state.tasks.items)
  const events = useAppSelector((state) => state.schedule.items)
  const [notificationsOpen, setNotificationsOpen] = useState(false)
  const [readIds, setReadIds] = useState<string[]>(() => {
    try { return JSON.parse(localStorage.getItem(`notification-reads-${user.id}`) ?? '[]') } catch { return [] }
  })
  useEffect(() => { dispatch(loadTasksRequest()); dispatch(loadScheduleRequest('all')) }, [dispatch])
  const notifications: NotificationItem[] = [
    ...tasks.filter((task) => user.role === 'lead' || task.assignee === user.name).flatMap((task) => [
      ...task.comments.filter((comment) => comment.author !== user.name).map((comment) => ({ id: comment.id, title: `Комментарий к задаче #${task.taskId}`, text: `${comment.author}: ${comment.text}`, createdAt: comment.createdAt, href: `/?task=${task.taskId}` })),
      ...task.history.filter((event) => event.author !== user.name).map((event) => ({ id: event.id, title: event.type === 'revision' ? 'Задача возвращена на доработку' : 'Изменён статус задачи', text: `${task.taskName}${event.comment ? ': ' + event.comment : ''}`, createdAt: event.createdAt, href: `/?task=${task.taskId}` })),
    ]),
    ...events.filter((event) => new Date(event.startsAt) >= new Date()).map((event) => ({ id: `schedule-${event.id}`, title: 'Предстоящее событие', text: event.title, createdAt: event.startsAt, href: `/schedule?date=${event.startsAt.slice(0, 10)}` })),
  ].sort((a, b) => b.createdAt.localeCompare(a.createdAt))
  const markRead = (ids: string[]) => {
    const next = [...new Set([...readIds, ...ids])]
    setReadIds(next)
    localStorage.setItem(`notification-reads-${user.id}`, JSON.stringify(next))
  }
  const unreadCount = notifications.filter((item) => !readIds.includes(item.id)).length
  const projectRole = activeProjectId === 'all' ? (user.role === 'lead' ? 'Капитан' : 'Участник') : user.projectRoles?.[activeProjectId] ?? 'Участник проекта'
  return (
    <>
      <button className={`sidebar-backdrop ${open ? 'is-open' : ''}`} onClick={onClose} aria-label="Закрыть меню" />
      <aside className={`sidebar ${open ? 'is-open' : ''}`}>
        <div className="sidebar__top"><button className="icon-button" onClick={onClose} aria-label="Закрыть">×</button></div>
        <div className="sidebar__person"><UserAvatar user={user} size="large" /><div><strong>{user.name}</strong><small>{projectRole}</small></div></div>
        <button className="sidebar__notifications" type="button" onClick={() => setNotificationsOpen(true)}><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 9a6 6 0 0 1 12 0c0 7 3 7 3 7H3s3 0 3-7ZM10 20h4" /></svg><span>Уведомления</span>{unreadCount > 0 && <b>{unreadCount}</b>}</button>
        <nav className="sidebar__nav">
          <NavLink to="/" onClick={onClose}>▦ <span>Доска задач</span></NavLink>
          <NavLink to="/schedule" onClick={onClose}>▤ <span>Расписание</span></NavLink>
          <NavLink to="/history" onClick={onClose}>◷ <span>История задач</span></NavLink>
          <NavLink to="/members" onClick={onClose}>♧ <span>Участники</span></NavLink>
          {user.role === 'lead' && <NavLink to="/team" onClick={onClose}>♙ <span>Управление доступом</span></NavLink>}
          <NavLink to="/projects" onClick={onClose}>◇ <span>Проекты</span></NavLink>
          <NavLink to="/profile" onClick={onClose}>◎ <span>Мой профиль</span></NavLink>
          <NavLink to="/settings" onClick={onClose}>⚙ <span>Настройки</span></NavLink>
        </nav>
        <button className="sidebar__logout" onClick={() => dispatch(logout())}>↪ Выйти</button>
      </aside>
      {notificationsOpen && <NotificationsModal items={notifications} readIds={readIds} onRead={(id) => markRead([id])} onReadAll={() => markRead(notifications.map((item) => item.id))} onClose={() => setNotificationsOpen(false)} onOpen={(href) => { setNotificationsOpen(false); onClose(); navigate(href) }} />}
    </>
  )
}

export default Sidebar
