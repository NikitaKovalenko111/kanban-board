import { describe, expect, it } from 'vitest'
import { eventReminderMinutes, validateReminderMinutes } from './reminders'
import { scheduleEvent } from '../test/fixtures'
import { loadEventTypes, saveEventTypes } from './eventTypes'

describe('напоминания событий', () => {
  it('поддерживает произвольные напоминания, момент начала или отключение', () => {
    expect(validateReminderMinutes([90])).toEqual([90])
    expect(validateReminderMinutes([0])).toEqual([0])
    expect(validateReminderMinutes([])).toEqual([])
    expect(validateReminderMinutes([30, 90])).toEqual([30, 90])
    expect(() => validateReminderMinutes([30, 30])).toThrow('не должны повторяться')
  })
  it.each([[-1], [1.5], [NaN], [Infinity]])('отклоняет некорректный интервал %s', (value) => {
    expect(() => validateReminderMinutes([value])).toThrow()
  })
  it('изменение типа обновляет наследуемые напоминания, сохраняя индивидуальные', () => {
    const inherited = { ...scheduleEvent, reminderMode: 'type' as const, reminderMinutes: [15] }
    const custom = { ...scheduleEvent, reminderMode: 'custom' as const, reminderMinutes: [90] }
    const types = [{ id: scheduleEvent.type, label: 'Встреча', color: '#123456', reminderMinutes: [30] }]
    expect(eventReminderMinutes(inherited, types)).toEqual([30])
    expect(eventReminderMinutes(custom, types)).toEqual([90])
  })
  it('сохраняет интервалы типов и читает старые события', () => {
    const types = [{ id: 'custom', label: 'Встреча', color: '#123456', reminderMinutes: [90] }]
    saveEventTypes(types)
    expect(loadEventTypes()).toEqual(types)
    expect(eventReminderMinutes({ ...scheduleEvent, reminder: '1-hour' }, types)).toEqual([60])
    expect(eventReminderMinutes({ ...scheduleEvent, reminder: 'none' }, types)).toEqual([])
  })
  it('сохраняет все интервалы из ранее записанных данных', () => {
    saveEventTypes([{ id: 'custom', label: 'Тип', color: '#123456', reminderMinutes: [90, 30] }])
    expect(loadEventTypes()[0].reminderMinutes).toEqual([90, 30])
    expect(eventReminderMinutes({ ...scheduleEvent, reminderMode: 'custom', reminderMinutes: [90, 30] }, [])).toEqual([90, 30])
  })
})
