import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { user } from './test/fixtures'

afterEach(() => vi.unstubAllEnvs())
it('серверный режим использует реальную роль и полный интерфейс, игнорирует mock-сессию', async () => {
  vi.resetModules(); vi.stubEnv('VITE_API_MODE', 'backend')
  localStorage.setItem('mock-session', JSON.stringify(user))
  const profile = { id: '58a77519-a67f-43be-8b46-91dce757684a', fullName: 'Серверный капитан', username: 'student', studentGroup: '203', imageUrl: null, role: 'CAPTAIN', schedulePrivacy: 'BUSY', joinedAt: '2026-11-05T10:00:00Z', active: true }
  const { authHttp, backendHttp } = await import('./api/backendApi')
  const requests: string[] = []
  authHttp.defaults.adapter = async config => { requests.push(config.url!); return { config, headers: {}, status: config.url === '/auth/logout' ? 204 : 200, statusText: 'OK', data: config.url === '/auth/login' ? { accessToken: 'real-access', refreshToken: 'real-refresh', expiresIn: 60, user: profile } : undefined } }
  backendHttp.defaults.adapter = async config => ({ config, headers: {}, status: 200, statusText: 'OK', data: config.url === '/me' ? profile : config.url === '/users' ? [profile] : config.url === '/team' ? { name: 'Команда', description: '' } : [] })
  const { store } = await import('./store')
  const { default: App } = await import('./App')
  render(<Provider store={store}><MemoryRouter><App /></MemoryRouter></Provider>)
  await screen.findByRole('heading', { name: 'Вход в аккаунт' })
  expect(screen.queryByText('Мок-данные для входа')).not.toBeInTheDocument()
  await userEvent.type(screen.getByLabelText('Логин'), 'student')
  await userEvent.type(screen.getByLabelText('Пароль'), 'Password123')
  await userEvent.click(screen.getByRole('button', { name: 'Войти' }))
  await screen.findByRole('heading', { name: 'Kanban-доска' })
  expect(store.getState().auth.user).toMatchObject({ id: profile.id, name: profile.fullName, role: 'lead' })
  expect(JSON.stringify(store.getState())).not.toContain('real-access')
  expect(screen.queryByText(user.name)).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Выйти' }))
  await screen.findByRole('heading', { name: 'Вход в аккаунт' })
  await waitFor(() => expect(requests).toEqual(['/auth/login', '/auth/logout']))
  expect(sessionStorage.getItem('kanban.backend.session.v1')).toBeNull()
  expect(store.getState().tasks.items).toEqual([])
})
