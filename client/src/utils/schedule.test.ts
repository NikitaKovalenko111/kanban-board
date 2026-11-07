import { expect, it } from 'vitest'
import { eventCategory, eventFilterCategory } from './schedule'
import { scheduleEvent } from '../test/fixtures'

it('выделяет пары в отдельный фильтр, сохраняя исходную категорию события', () => {
  const lesson = { ...scheduleEvent, type: 'class', category: 'group' as const }
  expect(eventFilterCategory(lesson)).toBe('class')
  expect(eventCategory(lesson)).toBe('group')
  expect(eventFilterCategory({ ...scheduleEvent, category: 'group' })).toBe('group')
})

it('сохраняет личную категорию, даже если выбран тип занятия', () => {
  expect(eventFilterCategory({ ...scheduleEvent, type: 'class', category: 'personal' })).toBe('personal')
})
