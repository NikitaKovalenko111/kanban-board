import { useEffect } from 'react'
import { NavLink } from 'react-router-dom'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { logout } from '../../store/slices/authSlice'
import { loadTeamRequest } from '../../store/slices/teamSlice'
import UserAvatar from '../UserAvatar/UserAvatar'

interface Props { open: boolean; onClose: () => void }

const Sidebar = ({ open, onClose }: Props) => {
  const dispatch = useAppDispatch()
  const user = useAppSelector((state) => state.auth.user)!
  const team = useAppSelector((state) => state.team)
  useEffect(() => { if (!team.members.length) dispatch(loadTeamRequest()) }, [dispatch, team.members.length])
  return (
    <>
      <button className={`sidebar-backdrop ${open ? 'is-open' : ''}`} onClick={onClose} aria-label="Закрыть меню" />
      <aside className={`sidebar ${open ? 'is-open' : ''}`}>
        <div className="sidebar__top"><button className="icon-button" onClick={onClose} aria-label="Закрыть">×</button></div>
        <div className="sidebar__person"><UserAvatar user={user} size="large" /><div><strong>{user.name}</strong><small>{user.position}</small></div></div>
        <nav className="sidebar__nav">
          <NavLink to="/" onClick={onClose}>▦ <span>Доска задач</span></NavLink>
          <NavLink to="/history" onClick={onClose}>◷ <span>История задач</span></NavLink>
          <NavLink to="/members" onClick={onClose}>♧ <span>Участники</span></NavLink>
          {user.role === 'lead' && <NavLink to="/team" onClick={onClose}>♙ <span>Управление командой</span></NavLink>}
          <NavLink to="/profile" onClick={onClose}>◎ <span>Мой профиль</span></NavLink>
        </nav>
        <div className="sidebar__team"><span>Команда</span><strong>{team.settings.name || 'Команда'}</strong><small>{team.members.length || '—'} участников</small></div>
        <button className="sidebar__logout" onClick={() => dispatch(logout())}>↪ Выйти</button>
      </aside>
    </>
  )
}

export default Sidebar
