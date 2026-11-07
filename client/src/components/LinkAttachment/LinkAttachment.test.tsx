import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import FormModal from '../FormModal/FormModal'
import LinkAttachment from './LinkAttachment'

it('добавляет ссылку во вложенном окне без отправки родительской формы', async () => {
  const onAdd = vi.fn()
  const submitParent = vi.fn(event => event.preventDefault())
  render(<FormModal title="Новый проект" onClose={vi.fn()}><form onSubmit={submitParent}><LinkAttachment onAdd={onAdd} /></form></FormModal>)
  await userEvent.click(screen.getByRole('button', { name: 'Добавить ссылку' }))
  const dialog = screen.getByRole('dialog', { name: 'Добавить ссылку' })
  await userEvent.type(within(dialog).getByLabelText('Ссылка'), 'https://example.com/spec')
  await userEvent.type(within(dialog).getByLabelText('Название ссылки'), 'ТЗ')
  await userEvent.click(within(dialog).getByRole('button', { name: 'Прикрепить ссылку' }))
  expect(onAdd).toHaveBeenCalledWith(expect.objectContaining({ url: 'https://example.com/spec', name: 'ТЗ' }))
  expect(submitParent).not.toHaveBeenCalled()
  expect(screen.queryByRole('dialog', { name: 'Добавить ссылку' })).not.toBeInTheDocument()
  expect(screen.getByRole('dialog', { name: 'Новый проект' })).toBeInTheDocument()
})

it('Escape закрывает только окно ссылки и сбрасывает черновик', async () => {
  const closeParent = vi.fn()
  render(<FormModal title="Новый проект" onClose={closeParent}><LinkAttachment onAdd={vi.fn()} /></FormModal>)
  await userEvent.click(screen.getByRole('button', { name: 'Добавить ссылку' }))
  await userEvent.type(screen.getByLabelText('Ссылка'), 'https://example.com/draft')
  await userEvent.keyboard('{Escape}')
  expect(closeParent).not.toHaveBeenCalled()
  expect(screen.queryByRole('dialog', { name: 'Добавить ссылку' })).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Добавить ссылку' }))
  expect(screen.getByLabelText('Ссылка')).toHaveValue('')
})
