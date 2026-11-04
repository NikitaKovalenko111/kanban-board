import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { user } from './test/fixtures'

afterEach(() => vi.unstubAllEnvs())
it('серверный режим игнорирует mock-сессию, входит по токенам и выходит без демонстрационных данных', async () => {
  vi.resetModules(); vi.stubEnv('VITE_API_MODE', 'backend')
  localStorage.setItem('mock-session', JSON.stringify(user))
  const { authHttp } = await import('./api/backendApi')
  const requests: string[] = []
  authHttp.defaults.adapter = async config => {
    requests.push(config.url!)
    return { config, headers: {}, status: config.url === '/auth/logout' ? 204 : 200, statusText: 'OK', data: config.url === '/auth/login' ? {
      user_id: '58a77519-a67f-43be-8b46-91dce757684a', access_token: 'real-access', refresh_token: 'real-refresh', token_type: 'Bearer',
    } : undefined }
  }
  const { store } = await import('./store')
  const { default: App } = await import('./App')
  render(<Provider store={store}><MemoryRouter><App /></MemoryRouter></Provider>)
  await screen.findByRole('heading', { name: 'Вход в аккаунт' })
  expect(screen.queryByText('Мок-данные для входа')).not.toBeInTheDocument()
  expect(screen.queryByText(user.name)).not.toBeInTheDocument()
  await userEvent.type(screen.getByLabelText('Логин'), 'student')
  await userEvent.type(screen.getByLabelText('Пароль'), 'Password123')
  await userEvent.click(screen.getByRole('button', { name: 'Войти' }))
  await screen.findByRole('heading', { name: 'Раздел пока недоступен' })
  expect(store.getState().auth.user).toBeNull()
  expect(store.getState().backendAuth.identity).toEqual({ userId: '58a77519-a67f-43be-8b46-91dce757684a', login: 'student' })
  expect(JSON.stringify(store.getState())).not.toContain('real-access')
  expect(screen.queryByRole('button', { name: 'Новая задача' })).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Выйти' }))
  await screen.findByRole('heading', { name: 'Вход в аккаунт' })
  await waitFor(() => expect(requests).toEqual(['/auth/login', '/auth/logout']))
  expect(sessionStorage.getItem('kanban.backend.session.v1')).toBeNull()
})
