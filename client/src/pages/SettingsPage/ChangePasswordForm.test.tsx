import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { mockApi } from '../../api/mockApi'
import ChangePasswordForm from './ChangePasswordForm'

const fillForm = async (confirmation = 'updated123') => {
  const actor = userEvent.setup()
  await actor.type(screen.getByLabelText('Текущий пароль'), 'current123')
  await actor.type(screen.getByLabelText('Новый пароль'), 'updated123')
  await actor.type(screen.getByLabelText('Подтвердите новый пароль'), confirmation)
  await actor.click(screen.getByRole('button', { name: 'Изменить пароль' }))
}

describe('смена пароля в настройках', () => {
  it('проверяет совпадение подтверждения до обращения к API', async () => {
    const change = vi.spyOn(mockApi, 'changePassword').mockResolvedValue({ success: true })
    render(<ChangePasswordForm />)
    await fillForm('different123')
    expect(screen.getByRole('alert')).toHaveTextContent('Новые пароли не совпадают')
    expect(change).not.toHaveBeenCalled()
  })

  it('передаёт текущий и новый пароль, очищает поля после сохранения', async () => {
    const change = vi.spyOn(mockApi, 'changePassword').mockResolvedValue({ success: true })
    render(<ChangePasswordForm />)
    await fillForm()
    expect(change).toHaveBeenCalledWith({ currentPassword: 'current123', newPassword: 'updated123' })
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Пароль успешно изменён'))
    expect(screen.getByLabelText('Текущий пароль')).toHaveValue('')
    expect(screen.getByLabelText('Новый пароль')).toHaveValue('')
    expect(screen.getByLabelText('Подтвердите новый пароль')).toHaveValue('')
  })

  it('показывает ошибку API и сохраняет данные для повторной попытки', async () => {
    vi.spyOn(mockApi, 'changePassword').mockRejectedValue(new Error('Текущий пароль указан неверно'))
    render(<ChangePasswordForm />)
    await fillForm()
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Текущий пароль указан неверно'))
    expect(screen.getByLabelText('Новый пароль')).toHaveValue('updated123')
    expect(screen.getByRole('button', { name: 'Изменить пароль' })).toBeEnabled()
  })
})
