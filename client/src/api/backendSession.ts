export interface TokenResponse {
  user_id: string
  access_token: string
  refresh_token: string
  token_type: string
}
export interface BackendIdentity { userId: string; login: string }
export interface BackendSession extends TokenResponse { login: string }

const sessionKey = 'kanban.backend.session.v1'

export function validateTokens(value: unknown): TokenResponse {
  if (!value || typeof value !== 'object') throw new Error('Сервер вернул некорректный ответ авторизации')
  const dto = value as Record<string, unknown>
  if (typeof dto.user_id !== 'string' || !dto.user_id || typeof dto.access_token !== 'string' || !dto.access_token || typeof dto.refresh_token !== 'string' || !dto.refresh_token || typeof dto.token_type !== 'string' || dto.token_type.toLowerCase() !== 'bearer') throw new Error('Сервер вернул некорректный ответ авторизации')
  return { user_id: dto.user_id, access_token: dto.access_token, refresh_token: dto.refresh_token, token_type: 'Bearer' }
}

export function readBackendSession(): BackendSession | null {
  try {
    const saved = JSON.parse(sessionStorage.getItem(sessionKey) ?? 'null')
    if (!saved || typeof saved.login !== 'string') return null
    return { ...validateTokens(saved), login: saved.login }
  } catch { sessionStorage.removeItem(sessionKey); return null }
}
export const saveBackendSession = (session: BackendSession) => sessionStorage.setItem(sessionKey, JSON.stringify(session))
export const clearBackendSession = () => sessionStorage.removeItem(sessionKey)
export const backendIdentity = (session: BackendSession): BackendIdentity => ({ userId: session.user_id, login: session.login })
