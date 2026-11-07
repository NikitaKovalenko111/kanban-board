import { configureStore } from '@reduxjs/toolkit'
import { Provider } from 'react-redux'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import auth from '../../store/slices/authSlice'
import team from '../../store/slices/teamSlice'
import { user } from '../../test/fixtures'
import { realApi } from '../../api/realApi'
import AccountSettingsForm from './AccountSettingsForm'

vi.mock('../../api/config', () => ({ isBackendMode: true, apiBaseUrl: '/api/v1' }))
afterEach(() => vi.restoreAllMocks())
it('сохраняет серверную группу, оставляя неподдерживаемую смену логина недоступной', async () => {
  vi.spyOn(realApi, 'getGroups').mockResolvedValue([{ id: 'group-id', groupCode: 'ТОП-204Б', name: 'ТОП-204Б' }])
  const current = { ...user, id: 'uuid', studentGroup: 'ТОП-203Б' }
  const updated = { ...current, studentGroup: 'ТОП-204Б', active: true, joinedAt: '2026-01-01' }
  const save = vi.spyOn(realApi, 'updateAccount').mockResolvedValue(updated)
  const store = configureStore({ reducer: { auth, team }, preloadedState: { auth: { user: current, loading: false, error: null } } })
  render(<Provider store={store}><AccountSettingsForm user={current} /></Provider>)
  expect(screen.getByLabelText('Логин')).toHaveAttribute('readonly')
  await screen.findByRole('option', { name: 'ТОП-204Б' })
  await userEvent.selectOptions(screen.getByLabelText('Учебная группа'), 'ТОП-204Б')
  await userEvent.click(screen.getByRole('button', { name: 'Сохранить аккаунт' }))
  expect(save).toHaveBeenCalledWith('uuid', { login: current.login, studentGroup: 'ТОП-204Б' })
  expect(await screen.findByRole('status')).toHaveTextContent('Данные аккаунта сохранены')
  expect(store.getState().auth.user?.studentGroup).toBe('ТОП-204Б')
})
