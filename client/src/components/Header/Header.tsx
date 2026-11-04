import type { JSX } from 'react'
import type React from 'react'
import { useNavigate } from 'react-router-dom'
import { useAppSelector } from '../../store/hooks'
import UserAvatar from '../UserAvatar/UserAvatar'

type PropsType = { onMenu: () => void }

const Header: React.FC<PropsType> = ({ onMenu }): JSX.Element => {
    const user = useAppSelector((state) => state.auth.user)!
    const navigate = useNavigate()
    return (
        <header className="header">
            <div className="container header__container">
                <button className="icon-button header__menu" onClick={onMenu} aria-label="Открыть меню"><svg className="ui-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h16" /></svg></button>
                <div className="header__right">
                    <span className="header__role">{user.role === 'lead' ? 'Капитан' : 'Участник'}</span>
                    <button className="header__user" onClick={() => navigate('/profile')}>
                        <UserAvatar user={user} />
                        <span className="header__user-copy"><strong>{user.name}</strong><small>@{user.login}</small></span>
                    </button>
                </div>
            </div>
        </header>
    )
}

export default Header
