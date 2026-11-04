import type { ScheduleEvent } from '../types'
import type { EventTypeOption } from './eventTypes'

export function validateReminderMinutes(minutes: number[]): number[] {
  if (!Array.isArray(minutes) || minutes.some((value) => !Number.isSafeInteger(value) || value < 0)) throw new Error('Укажите целое число минут от 0')
  if (minutes.length > 1) throw new Error('Можно задать только одно напоминание')
  return [...minutes]
}

export function eventReminderMinutes(event: ScheduleEvent, types: EventTypeOption[]): number[] {
  if (event.reminderMode === 'type') return (types.find((type) => type.id === event.type)?.reminderMinutes ?? event.reminderMinutes ?? []).slice(0, 1)
  if (event.reminderMinutes) return event.reminderMinutes.slice(0, 1)
  const legacy = { none: [], 'at-start': [0], '15-min': [15], '1-hour': [60], '1-day': [1440] }
  return [...legacy[event.reminder]]
}

export const reminderSummary = (minutes: number[]) => minutes.length ? minutes[0] === 0 ? 'В момент начала' : `За ${minutes[0]} мин.` : 'Без напоминания'
