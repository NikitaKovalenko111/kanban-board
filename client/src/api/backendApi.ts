import axios, { type InternalAxiosRequestConfig } from 'axios'
import type { LoginCredentials } from '../types'
import { apiBaseUrl } from './config'
import { backendIdentity, clearBackendSession, readBackendSession, saveBackendSession, validateTokens, type BackendIdentity, type BackendSession } from './backendSession'

export const backendSessionExpiredEvent = 'kanban:backend-session-expired'
// Auth endpoints use JSON token pairs, not cookies. A separate client avoids refresh loops.
export const authHttp = axios.create({ baseURL: apiBaseUrl, timeout: 15000 })
export const backendHttp = axios.create({ baseURL: apiBaseUrl, timeout: 15000 })
const messages: Record<string, string> = {
  invalid_credentials: 'Неверный логин или пароль',
  invalid_refresh_token: 'Сессия истекла. Войдите снова',
  invalid_access_token: 'Сессия истекла. Войдите снова',
  invalid_request: 'Сервер не принял данные запроса',
  request_timeout: 'Сервер не успел ответить. Повторите попытку',
  login_failed: 'Не удалось войти. Повторите попытку позже',
  refresh_failed: 'Не удалось обновить сессию. Повторите попытку позже',
  logout_failed: 'Не удалось завершить серверную сессию',
}
export function backendError(error: unknown): Error {
  if (!axios.isAxiosError(error)) return error instanceof Error ? error : new Error('Не удалось выполнить запрос')
  const body = error.response?.data
  const code = body && typeof body === 'object' ? body.error : undefined
  if (typeof code === 'string' && messages[code]) return new Error(messages[code])
  if (error.response?.status === 401) return new Error('Сессия истекла. Войдите снова')
  if (error.response?.status === 404) return new Error('Запрошенный API не найден на сервере')
  if (error.response?.status === 405) return new Error('Сервер не поддерживает эту операцию')
  if (error.response?.status && error.response.status >= 500) return new Error('Сервер временно недоступен. Повторите попытку позже')
  if (error.code === 'ECONNABORTED') return new Error('Сервер не успел ответить. Повторите попытку')
  if (!error.response) return new Error('Не удалось связаться с сервером. Проверьте подключение')
  return new Error('Сервер отклонил запрос')
}
function expireSession() {
  clearBackendSession()
  window.dispatchEvent(new Event(backendSessionExpiredEvent))
}
let refreshFlight: Promise<BackendSession> | null = null

export function refreshBackendSession(): Promise<BackendSession> {
  if (refreshFlight) return refreshFlight
  const previous = readBackendSession()
  if (!previous) return Promise.reject(new Error('Войдите в аккаунт'))
  refreshFlight = (async () => {
    try {
      const { data } = await authHttp.post('/auth/refresh', { refresh_token: previous.refresh_token })
      const tokens = validateTokens(data)
      if (tokens.user_id !== previous.user_id) throw new Error('Сервер вернул сессию другого пользователя')
      if (readBackendSession()?.refresh_token !== previous.refresh_token) throw new Error('Сессия была изменена. Повторите вход')
      const session = { ...tokens, login: previous.login }
      saveBackendSession(session)
      return session
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 401 && readBackendSession()?.refresh_token === previous.refresh_token) expireSession()
      throw backendError(error)
    } finally { refreshFlight = null }
  })()
  return refreshFlight
}

backendHttp.interceptors.request.use((config) => {
  const session = readBackendSession()
  if (session) config.headers.set('Authorization', `Bearer ${session.access_token}`)
  return config
})
backendHttp.interceptors.response.use(response => response, async (error: unknown) => {
  const config = axios.isAxiosError(error) ? error.config as (InternalAxiosRequestConfig & { retried?: boolean }) | undefined : undefined
  if (axios.isAxiosError(error) && error.response?.status === 401 && config && !config.retried && readBackendSession()) {
    config.retried = true
    // Another request may already have rotated the token while this response was in flight.
    if (config.headers.get('Authorization') === `Bearer ${readBackendSession()!.access_token}`) await refreshBackendSession()
    return backendHttp.request(config)
  }
  if (axios.isAxiosError(error) && error.response?.status === 401 && config?.retried) expireSession()
  throw backendError(error)
})

export const backendApi = {
  async login(credentials: LoginCredentials): Promise<BackendIdentity> {
    clearBackendSession()
    try {
      const { data } = await authHttp.post('/auth/login', { login: credentials.login.trim(), password: credentials.password })
      const session = { ...validateTokens(data), login: credentials.login.trim() }
      saveBackendSession(session)
      return backendIdentity(session)
    } catch (error) { throw backendError(error) }
  },
  async restore(): Promise<BackendIdentity | null> {
    if (!readBackendSession()) return null
    return backendIdentity(await refreshBackendSession())
  },
  async logout(): Promise<void> {
    // If a rotation is already running, revoke its replacement token.
    if (refreshFlight) { try { await refreshFlight } catch { /* Local logout still completes. */ } }
    const session = readBackendSession()
    clearBackendSession()
    if (!session) return
    try { await authHttp.post('/auth/logout', { refresh_token: session.refresh_token }) }
    catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 401) return
      throw new Error(`Вы вышли на этом устройстве. ${backendError(error).message}`)
    }
  },
  async health(): Promise<void> {
    try { await authHttp.get('/system/health') }
    catch (error) { throw backendError(error) }
  },
}
