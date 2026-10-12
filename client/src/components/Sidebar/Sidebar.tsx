import { NavLink } from 'react-router-dom'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { logout } from '../../store/slices/authSlice'

interface Props { open: boolean; onClose: () => void }

const Sidebar = ({ open, onClose }: Props) => {
  const dispatch = useAppDispatch()
  const user = useAppSelector((state) => state.auth.user)!
  return (
    <>
      <button className={`sidebar-backdrop ${open ? 'is-open' : ''}`} onClick={onClose} aria-label="Закрыть меню" />
      <aside className={`sidebar ${open ? 'is-open' : ''}`}>
        <div className="sidebar__top"><button className="icon-button" onClick={onClose} aria-label="Закрыть">×</button></div>
        <div className="sidebar__person"><span className="avatar avatar--large">{user.name.split(' ').map((part) => part[0]).join('')}</span><div><strong>{user.name}</strong><small>{user.position}</small></div></div>
        <nav className="sidebar__nav">
          <NavLink to="/" onClick={onClose}>▦ <span>Доска задач</span></NavLink>
          <NavLink to="/profile" onClick={onClose}>◎ <span>Мой профиль</span></NavLink>
        </nav>
        <div className="sidebar__team"><span>Команда</span><strong>Веб-продукт</strong><small>5 участников</small></div>
        <button className="sidebar__logout" onClick={() => dispatch(logout())}>↪ Выйти</button>
      </aside>
    </>
  )
}

export default Sidebar
