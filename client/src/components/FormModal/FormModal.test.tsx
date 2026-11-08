import { useState } from 'react'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import FormModal from './FormModal'

beforeEach(() => {
  vi.spyOn(HTMLElement.prototype, 'getClientRects').mockImplementation(function (this: HTMLElement) {
    return (this.closest('[hidden]') ? [] : [new DOMRect(0, 0, 100, 40)]) as unknown as DOMRectList
  })
})
afterEach(() => vi.restoreAllMocks())

function Windows() {
  const [open, setOpen] = useState(false)
  const [nested, setNested] = useState(false)
  const [name, setName] = useState('')
  return <><button onClick={() => setOpen(true)}>Открыть форму</button>{open && <FormModal title="Родительское окно" onClose={() => setOpen(false)}>
    <label>Название<input value={name} onChange={event => setName(event.target.value)} /></label>
    <button onClick={() => setNested(true)}>Открыть вложенное</button>
    {nested && <FormModal title="Вложенное окно" onClose={() => setNested(false)}><button>Последняя кнопка</button></FormModal>}
  </FormModal>}</>
}

it('переводит фокус в окно, удерживает Tab внутри и возвращает фокус после закрытия', async () => {
  render(<Windows />)
  const opener = screen.getByRole('button', { name: 'Открыть форму' })
  await userEvent.click(opener)
  const dialog = screen.getByRole('dialog')
  const first = within(dialog).getByRole('button', { name: 'Закрыть' })
  expect(first).toHaveFocus()
  await userEvent.keyboard('{Shift>}{Tab}{/Shift}')
  expect(screen.getByRole('button', { name: 'Открыть вложенное' })).toHaveFocus()
  await userEvent.tab()
  expect(first).toHaveFocus()
  opener.focus()
  expect(first).toHaveFocus()
  await userEvent.keyboard('{Escape}')
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  expect(opener).toHaveFocus()
})

it('сохраняет фокус при вводе и закрывает по Escape только верхнее окно', async () => {
  const originalOverflow = document.body.style.overflow
  render(<Windows />)
  await userEvent.click(screen.getByRole('button', { name: 'Открыть форму' }))
  await userEvent.type(screen.getByLabelText('Название'), 'Черновик')
  expect(screen.getByLabelText('Название')).toHaveFocus()
  const opener = screen.getByRole('button', { name: 'Открыть вложенное' })
  await userEvent.click(opener)
  expect(document.body.style.overflow).toBe('hidden')
  await userEvent.keyboard('{Escape}')
  expect(screen.queryByRole('dialog', { name: 'Вложенное окно' })).not.toBeInTheDocument()
  expect(screen.getByRole('dialog', { name: 'Родительское окно' })).toBeInTheDocument()
  expect(document.body.style.overflow).toBe('hidden')
  expect(opener).toHaveFocus()
  await userEvent.keyboard('{Escape}')
  expect(document.body.style.overflow).toBe(originalOverflow)
})
