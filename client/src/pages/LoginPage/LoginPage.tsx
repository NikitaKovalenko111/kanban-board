import { useNavigate } from 'react-router-dom'
import { useAppDispatch } from '../../store/hooks'
import { login } from '../../store/slices/authSlice'
import type { UserRole } from '../../types'

const LoginPage = () => {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const enter = (role: UserRole) => { dispatch(login(role)); navigate('/') }
  return <main className="login-page"><section className="login-card"><span className="eyebrow">Командная доска</span><h1>Добро пожаловать</h1><p>Выберите пользователя для входа в демо-версию</p><div className="login-card__roles"><button onClick={() => enter('lead')}><span>АМ</span><div><strong>Алексей Морозов</strong><small>@alexey · тимлид</small></div><b>→</b></button><button onClick={() => enter('member')}><span>МВ</span><div><strong>Мария Волкова</strong><small>@maria · участник</small></div><b>→</b></button></div><small className="login-card__hint">Пароль не требуется — сейчас используются моки</small></section></main>
}
export default LoginPage
