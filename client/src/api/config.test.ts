import { afterEach, expect, it, vi } from 'vitest'

afterEach(() => vi.unstubAllEnvs())
it('обычная сборка по умолчанию обращается к backend через /api', async () => {
  vi.resetModules(); vi.stubEnv('MODE', 'production'); vi.stubEnv('VITE_API_MODE', undefined)
  const config = await import('./config')
  expect(config.isBackendMode).toBe(true)
  expect(config.apiBaseUrl).toBe('/api/v1')
})
it('mock API включается только явно, тесты без настройки сохраняют mock-режим', async () => {
  vi.resetModules(); vi.stubEnv('MODE', 'development'); vi.stubEnv('VITE_API_MODE', 'mock')
  expect((await import('./config')).isBackendMode).toBe(false)
  vi.resetModules(); vi.stubEnv('MODE', 'test'); vi.stubEnv('VITE_API_MODE', undefined)
  expect((await import('./config')).isBackendMode).toBe(false)
})
it('неизвестный режим не включает демонстрационные данные по ошибке', async () => {
  vi.resetModules(); vi.stubEnv('VITE_API_MODE', 'bakend')
  await expect(import('./config')).rejects.toThrow('VITE_API_MODE')
})
it('в серверном режиме отсутствующие операции не обращаются к mock API', async () => {
  vi.resetModules(); vi.stubEnv('VITE_API_MODE', 'backend')
  const { api } = await import('./index')
  const { realApi } = await import('./realApi')
  expect(api).toBe(realApi)
  await expect(api.updateTeam({ name: '', description: '' })).rejects.toThrow('не поддерживает')

})
