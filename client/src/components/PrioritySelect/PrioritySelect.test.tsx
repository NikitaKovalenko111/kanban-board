import { useState } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import type { TaskPriority } from '../../types'
import FormModal from '../FormModal/FormModal'
import PrioritySelect from './PrioritySelect'

function Field() {
  const [value, setValue] = useState<TaskPriority>('medium')
  return <PrioritySelect value={value} onChange={setValue} />
}

it('показывает собственный список и сохраняет выбранный приоритет', async () => {
  render(<Field />)
  const trigger = screen.getByRole('combobox', { name: 'Приоритет' })
  await userEvent.click(trigger)
  expect(screen.getAllByRole('option')).toHaveLength(3)
  expect(screen.getByRole('option', { name: 'Обычный' })).toHaveAttribute('aria-selected', 'true')
  await userEvent.click(screen.getByRole('option', { name: 'Высокий' }))
  expect(trigger).toHaveTextContent('Высокий')
  expect(trigger).toHaveFocus()
  expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
})

it('поддерживает выбор клавиатурой без отправки формы', async () => {
  const submit = vi.fn(event => event.preventDefault())
  render(<form onSubmit={submit}><Field /></form>)
  await userEvent.tab()
  await userEvent.keyboard('{ArrowDown}{End}{Enter}')
  expect(screen.getByRole('combobox')).toHaveTextContent('Высокий')
  expect(submit).not.toHaveBeenCalled()
  await userEvent.keyboard('{ArrowUp}{Home}{Enter}')
  expect(screen.getByRole('combobox')).toHaveTextContent('Низкий')
})

it('закрывает список по Escape, сохраняя окно задачи открытым', async () => {
  const close = vi.fn()
  render(<FormModal title="Задача" onClose={close}><Field /></FormModal>)
  await userEvent.click(screen.getByRole('combobox'))
  await userEvent.keyboard('{ArrowDown}{Escape}')
  expect(close).not.toHaveBeenCalled()
  expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
  expect(screen.getByRole('combobox')).toHaveTextContent('Обычный')
})

it('закрывает меню при нажатии снаружи и не открывает заблокированное поле', async () => {
  render(<><Field /><PrioritySelect value="low" disabled onChange={vi.fn()} /><button>Снаружи</button></>)
  const [enabled, disabled] = screen.getAllByRole('combobox')
  await userEvent.click(enabled)
  await userEvent.click(screen.getByRole('button', { name: 'Снаружи' }))
  expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
  await userEvent.click(disabled)
  expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
})
