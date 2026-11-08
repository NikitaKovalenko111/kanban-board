import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { notificationItem, useServerNotifications } from './useServerNotifications'
import { realApi } from '../../api/realApi'
import type { NotificationDto } from '../../api/contracts'

vi.mock('../../api/config', () => ({ isBackendMode: true, apiBaseUrl: '/api/v1' }))
afterEach(() => vi.restoreAllMocks())
const item: NotificationDto = { id: 'notice', kind: 'LESSON', title: 'Пара', text: 'Скоро начало', at: '2026-11-05T23:45:00Z', read: false, lessonId: 'lesson-id' }

it('строит независимые ссылки для TASK, EVENT и LESSON с необязательными ID', () => {
  expect(notificationItem(item).href).toContain('lesson=lesson-id')
  expect(notificationItem(item).href).toBe('/schedule?lesson=lesson-id')
  expect(notificationItem({ ...item, lessonId: undefined, eventId: 'event-id', kind: 'EVENT' }).href).toBe('/schedule?event=event-id')
  expect(notificationItem({ ...item, lessonId: undefined, taskId: 'task-id', kind: 'STATUS' }).href).toBe('/?task=task-id')
})
it('не отмечает уведомления при открытии и вызывает read-all только явно', async () => {
  vi.spyOn(realApi, 'notifications').mockResolvedValue([item])
  const read = vi.spyOn(realApi, 'readNotifications').mockResolvedValue()
  const { result } = renderHook(() => useServerNotifications(true))
  await waitFor(() => expect(result.current.items).toHaveLength(1))
  expect(read).not.toHaveBeenCalled()
  expect(result.current.readIds).toEqual([])
  vi.mocked(realApi.notifications).mockResolvedValue([{ ...item, read: true }])
  await act(() => result.current.readAll())
  expect(read).toHaveBeenCalledOnce()
  expect(result.current.readIds).toEqual(['notice'])
})
it('подтверждает индивидуальное прочтение на сервере и сохраняет непрочитанное состояние при ошибке', async () => {
  vi.spyOn(realApi, 'notifications').mockResolvedValue([item])
  const read = vi.spyOn(realApi, 'readNotification').mockRejectedValueOnce(new Error('Нет связи')).mockResolvedValueOnce()
  const { result } = renderHook(() => useServerNotifications(true))
  await waitFor(() => expect(result.current.items).toHaveLength(1))
  await act(() => result.current.readOne(item.id))
  expect(result.current.readIds).toEqual([])
  expect(result.current.error).toBe('Нет связи')
  await act(() => result.current.readOne(item.id))
  expect(read).toHaveBeenCalledWith(item.id)
  expect(result.current.readIds).toEqual([item.id])
})
