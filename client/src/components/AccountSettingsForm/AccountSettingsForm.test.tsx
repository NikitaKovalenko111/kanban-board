import { configureStore } from '@reduxjs/toolkit'
import { Provider } from 'react-redux'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import auth from '../../store/slices/authSlice'
import team from '../../store/slices/teamSlice'
import { user } from '../../test/fixtures'
import { mockApi } from '../../api/mockApi'
import AccountSettingsForm from './AccountSettingsForm'

it('сохраняет логин и группу и сразу обновляет текущий профиль', async () => {
  const updated = { ...user, login: 'new-login', studentGroup: '204' as const, active: true, joinedAt: '2026-01-01' }
  const save = vi.spyOn(mockApi, 'updateAccount').mockResolvedValue(updated)
  const store = configureStore({ reducer: { auth, team }, preloadedState: { auth: { user, loading: false, error: null } } })
  render(<Provider store={store}><AccountSettingsForm user={user} /></Provider>)
  expect(screen.queryByLabelText('Имя и фамилия')).not.toBeInTheDocument()
  await userEvent.clear(screen.getByLabelText('Логин'))
  await userEvent.type(screen.getByLabelText('Логин'), 'new-login')
  await userEvent.selectOptions(screen.getByLabelText('Учебная группа'), '204')
  await userEvent.click(screen.getByRole('button', { name: 'Сохранить аккаунт' }))
  expect(save).toHaveBeenCalledWith(user.id, { login: 'new-login', studentGroup: '204' })
  expect(await screen.findByRole('status')).toHaveTextContent('Данные аккаунта сохранены')
  expect(store.getState().auth.user).toMatchObject({ login: 'new-login', studentGroup: '204', name: user.name })
})
