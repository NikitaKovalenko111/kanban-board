import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { authHttp, backendApi, backendHttp, backendSessionExpiredEvent, refreshBackendSession } from './backendApi'
import { clearBackendSession, readBackendSession, saveBackendSession } from './backendSession'

const tokens = { user_id: '8c57db56-a1e6-459d-9e92-4d164299e134', access_token: 'access-1', refresh_token: 'refresh-1', token_type: 'Bearer' }
const originalAuthAdapter = authHttp.defaults.adapter
const originalApiAdapter = backendHttp.defaults.adapter
const ok = (config: InternalAxiosRequestConfig, data: unknown = {}, status = 200): AxiosResponse => ({ data, config, status, statusText: 'OK', headers: {} })
function fail(config: InternalAxiosRequestConfig, status: number, code: string): never {
  throw new AxiosError('Request failed', 'ERR_BAD_RESPONSE', config, undefined, { ...ok(config, { error: code, message: 'Backend error' }), status })
}
const seed = () => saveBackendSession({ ...tokens, login: 'student' })
beforeEach(() => { sessionStorage.clear(); localStorage.clear() })
afterEach(() => { authHttp.defaults.adapter = originalAuthAdapter; backendHttp.defaults.adapter = originalApiAdapter; sessionStorage.clear() })

describe('существующий backend: авторизация', () => {
  it('отправляет login/password, принимает snake_case DTO и сохраняет UUID без фиктивной роли', async () => {
    const adapter = vi.fn(async (config: InternalAxiosRequestConfig) => {
      expect(config.url).toBe('/auth/login')
      expect(JSON.parse(config.data)).toEqual({ login: 'student', password: ' Password123 ' })
      expect(config.headers.get('Authorization')).toBeUndefined()
      return ok(config, tokens)
    })
    authHttp.defaults.adapter = adapter
    expect(await backendApi.login({ login: ' student ', password: ' Password123 ' })).toEqual({ userId: tokens.user_id, login: 'student' })
    expect(readBackendSession()).toEqual({ ...tokens, login: 'student' })
    expect(localStorage.getItem('mock-session')).toBeNull()
  })

  it('переводит invalid_credentials и не создаёт сессию при неверном пароле', async () => {
    seed()
    authHttp.defaults.adapter = async config => fail(config, 401, 'invalid_credentials')
    await expect(backendApi.login({ login: 'student', password: 'wrong' })).rejects.toThrow('Неверный логин или пароль')
    expect(readBackendSession()).toBeNull()
  })

  it('отклоняет ответ без токенов и неподдерживаемый token_type', async () => {
    for (const data of [{ user_id: tokens.user_id }, { ...tokens, token_type: 'Cookie' }]) {
      authHttp.defaults.adapter = async config => ok(config, data)
      await expect(backendApi.login({ login: 'student', password: 'Password123' })).rejects.toThrow('некорректный ответ')
      expect(readBackendSession()).toBeNull()
    }
  })

  it('восстанавливает сессию через refresh и сохраняет оба новых токена', async () => {
    seed()
    authHttp.defaults.adapter = async config => {
      expect(config.url).toBe('/auth/refresh')
      expect(JSON.parse(config.data)).toEqual({ refresh_token: 'refresh-1' })
      return ok(config, { ...tokens, access_token: 'access-2', refresh_token: 'refresh-2' })
    }
    expect(await backendApi.restore()).toEqual({ userId: tokens.user_id, login: 'student' })
    expect(readBackendSession()).toMatchObject({ access_token: 'access-2', refresh_token: 'refresh-2' })
  })

  it('не делает запросов восстановления без сохранённой сессии', async () => {
    const adapter = vi.fn()
    authHttp.defaults.adapter = adapter
    expect(await backendApi.restore()).toBeNull()
    expect(adapter).not.toHaveBeenCalled()
  })

  it('объединяет параллельные 401 в одно обновление и повторяет запросы с новым Bearer', async () => {
    seed()
    let release!: () => void
    const gate = new Promise<void>(resolve => { release = resolve })
    const refresh = vi.fn(async (config: InternalAxiosRequestConfig) => {
      await gate
      return ok(config, { ...tokens, access_token: 'access-2', refresh_token: 'refresh-2' })
    })
    authHttp.defaults.adapter = refresh
    const apiAdapter = vi.fn(async (config: InternalAxiosRequestConfig) => {
      if (config.headers.get('Authorization') === 'Bearer access-1') fail(config, 401, 'invalid_access_token')
      expect(config.headers.get('Authorization')).toBe('Bearer access-2')
      return ok(config, { success: true })
    })
    backendHttp.defaults.adapter = apiAdapter
    // These synthetic protected paths exercise the transport; they are not advertised as server endpoints.
    const first = backendHttp.get('/test-protected-a')
    const second = backendHttp.get('/test-protected-b')
    await vi.waitFor(() => expect(refresh).toHaveBeenCalledOnce())
    release()
    await expect(Promise.all([first, second])).resolves.toHaveLength(2)
    expect(refresh).toHaveBeenCalledOnce()
    expect(apiAdapter).toHaveBeenCalledTimes(4)
  })

  it('не зацикливается при истёкшем refresh, очищает сессию и уведомляет приложение', async () => {
    seed()
    const expired = vi.fn()
    window.addEventListener(backendSessionExpiredEvent, expired)
    const refresh = vi.fn(async (config: InternalAxiosRequestConfig) => fail(config, 401, 'invalid_refresh_token'))
    authHttp.defaults.adapter = refresh
    backendHttp.defaults.adapter = async config => fail(config, 401, 'invalid_access_token')
    try {
      await expect(backendHttp.get('/test-protected')).rejects.toThrow('Сессия истекла')
      expect(readBackendSession()).toBeNull()
      expect(refresh).toHaveBeenCalledOnce()
      expect(expired).toHaveBeenCalledOnce()
    } finally { window.removeEventListener(backendSessionExpiredEvent, expired) }
  })

  it('после одного повторного 401 возвращает пользователя к входу', async () => {
    seed()
    authHttp.defaults.adapter = async config => ok(config, { ...tokens, access_token: 'access-2', refresh_token: 'refresh-2' })
    const adapter = vi.fn(async (config: InternalAxiosRequestConfig) => fail(config, 401, 'invalid_access_token'))
    backendHttp.defaults.adapter = adapter
    await expect(backendHttp.get('/test-protected')).rejects.toThrow('Сессия истекла')
    expect(adapter).toHaveBeenCalledTimes(2)
    expect(readBackendSession()).toBeNull()
  })

  it('сетевая ошибка refresh не уничтожает действующую сессию', async () => {
    seed()
    authHttp.defaults.adapter = async config => { throw new AxiosError('Offline', 'ERR_NETWORK', config) }
    await expect(refreshBackendSession()).rejects.toThrow('Не удалось связаться')
    expect(readBackendSession()?.refresh_token).toBe('refresh-1')
  })

  it('не восстанавливает сессию после локального выхода во время refresh', async () => {
    seed()
    let release!: () => void
    const gate = new Promise<void>(resolve => { release = resolve })
    authHttp.defaults.adapter = async config => { await gate; return ok(config, { ...tokens, refresh_token: 'refresh-2' }) }
    const pending = refreshBackendSession()
    clearBackendSession()
    release()
    await expect(pending).rejects.toThrow('Сессия была изменена')
    expect(readBackendSession()).toBeNull()
  })

  it('отклоняет подмену user_id при refresh', async () => {
    seed()
    authHttp.defaults.adapter = async config => ok(config, { ...tokens, user_id: 'another-account' })
    await expect(refreshBackendSession()).rejects.toThrow('другого пользователя')
    expect(readBackendSession()?.user_id).toBe(tokens.user_id)
  })

  it('отзывает актуальный refresh_token через logout и очищает локальную сессию', async () => {
    seed()
    authHttp.defaults.adapter = async config => {
      expect(config.url).toBe('/auth/logout')
      expect(JSON.parse(config.data)).toEqual({ refresh_token: 'refresh-1' })
      return ok(config, undefined, 204)
    }
    await backendApi.logout()
    expect(readBackendSession()).toBeNull()
  })

  it('при сетевой ошибке выхода очищает токены и сообщает, что сервер не подтвердил выход', async () => {
    seed()
    authHttp.defaults.adapter = async config => { throw new AxiosError('Offline', 'ERR_NETWORK', config) }
    await expect(backendApi.logout()).rejects.toThrow('Вы вышли на этом устройстве')
    expect(readBackendSession()).toBeNull()
  })

  it('запрашивает реальный /system/health и принимает пустой HTTP 200', async () => {
    authHttp.defaults.adapter = async config => { expect(config.url).toBe('/system/health'); return ok(config, '') }
    await expect(backendApi.health()).resolves.toBeUndefined()
  })
})
