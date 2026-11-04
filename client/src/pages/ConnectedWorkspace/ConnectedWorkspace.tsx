import { useEffect, useState } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import Icon from '../../components/Icon/Icon'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { backendLogoutRequest } from '../../store/slices/backendAuthSlice'

const sections = [
  { path: '/', label: 'Kanban-доска', description: 'Задачи появятся здесь, когда сервер будет поддерживать работу с досками.' },
  { path: '/schedule', label: 'Расписание', description: 'События появятся здесь после подключения расписания на сервере.' },
  { path: '/history', label: 'История задач', description: 'Архив появится здесь после подключения задач и истории на сервере.' },
  { path: '/team', label: 'Участники и доступ', description: 'Список участников и управление доступом пока недоступны на сервере.' },
  { path: '/projects', label: 'Проекты', description: 'Проекты и их файлы пока недоступны на сервере.' },
  { path: '/profile', label: 'Мой профиль', description: 'Сервер подтвердил вход, но пока не предоставляет данные профиля и роли в команде.' },
  { path: '/settings', label: 'Настройки', description: 'Изменение пароля и настроек аккаунта пока недоступно на сервере.' },
]

export default function ConnectedWorkspace() {
  const { identity, signingOut } = useAppSelector((state) => state.backendAuth)
  const dispatch = useAppDispatch()
  const { pathname } = useLocation()
  const [open, setOpen] = useState(false)
  const [dark, setDark] = useState(() => localStorage.getItem('theme') === 'dark')
  useEffect(() => { const theme = dark ? 'dark' : 'light'; document.documentElement.dataset.theme = theme; localStorage.setItem('theme', theme) }, [dark])
  const section = sections.find((item) => item.path === pathname || (item.path !== '/' && pathname.startsWith(`${item.path}/`))) ?? sections[0]
  if (!identity) return null

  return <div className="connected-workspace">
    <header className="connected-workspace__mobile"><button type="button" className="icon-button" aria-label="Открыть меню" aria-expanded={open} onClick={() => setOpen(true)}><svg className="ui-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h16" /></svg></button><span>@{identity.login}</span></header>
    {open && <button className="sidebar-backdrop is-open" aria-label="Закрыть меню" onClick={() => setOpen(false)} />}
    <aside className={`connected-workspace__sidebar ${open ? 'is-open' : ''}`}>
      <button type="button" className="icon-button connected-workspace__close" aria-label="Закрыть меню" onClick={() => setOpen(false)}><Icon name="close" /></button>
      <NavLink className="connected-workspace__identity" to="/profile" onClick={() => setOpen(false)}><span className="avatar">{identity.login.slice(0, 2).toUpperCase()}</span><strong>@{identity.login}</strong></NavLink>
      <nav aria-label="Основные разделы">{sections.map((item) => <NavLink end={item.path === '/'} key={item.path} to={item.path} onClick={() => setOpen(false)}>{item.label}</NavLink>)}</nav>
      <button className="button button--secondary" disabled={signingOut} onClick={() => dispatch(backendLogoutRequest())}><Icon name="arrow-left" />{signingOut ? 'Выходим…' : 'Выйти'}</button>
    </aside>
    <main className="connected-workspace__main"><h1>{section.label}</h1>{section.path === '/settings' && <section className="panel theme-panel"><h2>Внешний вид</h2><label><input type="checkbox" checked={dark} onChange={(event) => setDark(event.target.checked)} />Тёмный режим</label></section>}<section className="panel connected-workspace__notice"><h2>Раздел пока недоступен</h2><p>{section.description}</p><p className="muted">Вход выполнен. Данные появятся после подключения этого раздела.</p>{section.path === '/profile' && <dl><dt>Логин при входе</dt><dd>{identity.login}</dd><dt>ID аккаунта</dt><dd>{identity.userId}</dd></dl>}</section></main>
  </div>
}
