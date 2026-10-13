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

  return <main className="login-page"><section className="login-card"><span className="eyebrow">Командная доска</span><h1>Вход в аккаунт</h1><p>Введите логин и пароль, выданные тимлидом</p><form className="login-card__form" onSubmit={submit}><label>Логин<input autoFocus autoComplete="username" value={login} onChange={(event) => setLogin(event.target.value)} placeholder="Ваш логин" required /></label><label>Пароль<div className="password-field"><input autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} type={showPassword ? 'text' : 'password'} placeholder="Ваш пароль" required /><button type="button" onClick={() => setShowPassword((value) => !value)}>{showPassword ? 'Скрыть' : 'Показать'}</button></div></label>{error && <div className="login-card__error">{error}</div>}<button className="button login-card__submit" type="submit" disabled={loading}>{loading ? 'Входим…' : 'Войти'}</button></form><div className="login-card__credentials"><strong>Мок-данные для входа</strong><span>Тимлид: alexey / lead123</span><span>Участник: maria / member123</span></div></section></main>
}
export default LoginPage
