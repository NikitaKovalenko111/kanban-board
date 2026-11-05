export interface TokenResponse {
  user_id: string
  access_token: string
  refresh_token: string
  token_type: string
}
export interface BackendIdentity { userId: string; login: string }
export interface BackendSession extends TokenResponse { login: string }

const sessionKey = 'kanban.backend.session.v1'

export function validateTokens(value: unknown, fallbackUserId?: string): TokenResponse {
  if (!value || typeof value !== 'object') throw new Error('Сервер вернул некорректный ответ авторизации')
  const dto = value as Record<string, unknown>
  const userId = typeof dto.user === 'object' && dto.user ? (dto.user as Record<string, unknown>).id : fallbackUserId
  if (typeof userId !== 'string' || !userId || typeof dto.accessToken !== 'string' || !dto.accessToken || typeof dto.refreshToken !== 'string' || !dto.refreshToken || typeof dto.expiresIn !== 'number' || dto.expiresIn <= 0) throw new Error('Сервер вернул некорректный ответ авторизации')
  return { user_id: userId, access_token: dto.accessToken, refresh_token: dto.refreshToken, token_type: 'Bearer' }
}

export function readBackendSession(): BackendSession | null {
  try {
    const saved = JSON.parse(sessionStorage.getItem(sessionKey) ?? 'null')
    if (!saved || typeof saved.login !== 'string') return null
    if (typeof saved.user_id !== 'string' || typeof saved.access_token !== 'string' || typeof saved.refresh_token !== 'string' || !saved.access_token || !saved.refresh_token) return null
    return saved as BackendSession
  } catch { sessionStorage.removeItem(sessionKey); return null }
}
export const saveBackendSession = (session: BackendSession) => sessionStorage.setItem(sessionKey, JSON.stringify(session))
export const clearBackendSession = () => sessionStorage.removeItem(sessionKey)
export const backendIdentity = (session: BackendSession): BackendIdentity => ({ userId: session.user_id, login: session.login })
