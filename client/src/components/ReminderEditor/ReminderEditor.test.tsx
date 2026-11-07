import { useState } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it } from 'vitest'
import EventReminderEditor from './EventReminderEditor'
import type { EventReminders } from '../../types'

function Form() {
  const [value, setValue] = useState<EventReminders>({ reminderMode: 'type', reminderMinutes: [15] })
  return <><EventReminderEditor defaults={[30]} value={value} onChange={setValue} /><output data-testid="value">{JSON.stringify(value)}</output></>
}

it('позволяет заменить напоминания типа произвольным интервалом и вернуть наследование', async () => {
  render(<Form />)
  expect(screen.getByText('За 30 мин.')).toBeInTheDocument()
  await userEvent.click(screen.getByRole('checkbox'))
  const input = screen.getByRole('spinbutton')
  await userEvent.clear(input)
  await userEvent.type(input, '90')
  expect(screen.getByTestId('value')).toHaveTextContent('"reminderMinutes":[90]')
  expect(screen.getAllByRole('spinbutton')).toHaveLength(1)
  await userEvent.click(screen.getByRole('button', { name: 'Добавить напоминание' }))
  expect(screen.getAllByRole('spinbutton')).toHaveLength(2)
  expect(screen.getByTestId('value')).toHaveTextContent('"reminderMinutes":[90,15]')
  await userEvent.click(screen.getByRole('checkbox', { name: 'Напомнить о событии' }))
  expect(screen.queryByRole('spinbutton')).not.toBeInTheDocument()
  expect(screen.getByTestId('value')).toHaveTextContent('"reminderMinutes":[]')
  await userEvent.click(screen.getByRole('checkbox', { name: 'Использовать напоминание типа' }))
  expect(screen.queryByRole('spinbutton')).not.toBeInTheDocument()
  expect(screen.getByText('За 30 мин.')).toBeInTheDocument()
})
