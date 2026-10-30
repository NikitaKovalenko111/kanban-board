import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAppDispatch } from '../../store/hooks'
import { useAppSelector } from '../../store/hooks'
import { clearLoginError, loginRequest } from '../../store/slices/authSlice'

const LoginPage = () => {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const { user, loading, error } = useAppSelector((state) => state.auth)
  const [login, setLogin] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)

  useEffect(() => { if (user) navigate('/', { replace: true }) }, [navigate, user])
  useEffect(() => () => { dispatch(clearLoginError()) }, [dispatch])

  const submit = (event: FormEvent) => {
    event.preventDefault()
    dispatch(loginRequest({ login, password }))
  }

  return <main className="login-page"><section className="login-card"><span className="eyebrow">Командная доска</span><h1>Вход в аккаунт</h1><p>Введите логин и пароль, выданные тимлидом</p><form className="login-card__form" onSubmit={submit}><label>Логин<input autoFocus autoComplete="username" value={login} onChange={(event) => setLogin(event.target.value)} placeholder="Ваш логин" required /></label><label>Пароль<div className="password-field"><input autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} type={showPassword ? 'text' : 'password'} placeholder="Ваш пароль" required /><button type="button" onClick={() => setShowPassword((value) => !value)} aria-label={showPassword ? 'Скрыть пароль' : 'Показать пароль'} title={showPassword ? 'Скрыть пароль' : 'Показать пароль'}>{showPassword ? <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 3l18 18M10.6 10.7a2 2 0 0 0 2.7 2.7M9.5 5.3A9.8 9.8 0 0 1 12 5c5.5 0 9 7 9 7a16 16 0 0 1-2.1 3M6.2 6.2C4.2 7.6 3 10 3 12c0 0 3.5 7 9 7 1 0 2-.2 2.8-.5" /></svg> : <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 12s3.5-7 9-7 9 7 9 7-3.5 7-9 7-9-7-9-7Z" /><circle cx="12" cy="12" r="3" /></svg>}</button></div></label>{error && <div className="login-card__error">{error}</div>}<button className="button login-card__submit" type="submit" disabled={loading}>{loading ? 'Входим…' : 'Войти'}</button></form><div className="login-card__credentials"><strong>Мок-данные для входа</strong><span>Тимлид: alexey / lead123</span><span>Участник: maria / member123</span></div></section></main>
}
export default LoginPage
