import { useEffect, useState } from 'react'
import { isBackendMode } from '../../api/config'
import { realApi } from '../../api/realApi'
import type { NotificationDto } from '../../api/contracts'

export function useServerNotifications(open: boolean) {
  const [items, setItems] = useState<NotificationDto[]>([])
  const [error, setError] = useState('')
  useEffect(() => {
    if (!isBackendMode) return
    let cancelled = false
    let loading = false
    const load = async () => {
      if (loading) return
      loading = true
      try {
        const result = await realApi.notifications()
        if (!cancelled) { setItems(result); setError('') }
      } catch (reason) { if (!cancelled) setError(reason instanceof Error ? reason.message : 'Не удалось загрузить уведомления') }
      finally { loading = false }
    }
    void load()
    const timer = window.setInterval(() => void load(), 30000)
    const focus = () => void load()
    window.addEventListener('focus', focus)
    return () => { cancelled = true; window.clearInterval(timer); window.removeEventListener('focus', focus) }
  }, [open])
  const readAll = async () => {
    try { await realApi.readNotifications(); setItems(await realApi.notifications()); setError('') }
    catch (reason) { setError(reason instanceof Error ? reason.message : 'Не удалось прочитать уведомления') }
  }
  const readOne = async (id: string) => {
    try { await realApi.readNotification(id); setItems(current => current.map(item => item.id === id ? { ...item, read: true } : item)); setError(''); return true }
    catch (reason) { setError(reason instanceof Error ? reason.message : 'Не удалось прочитать уведомление'); return false }
  }
  return { items: items.map(notificationItem), readIds: items.filter(item => item.read).map(item => item.id), error, readAll, readOne }
}

export function notificationItem(item: NotificationDto) {
  const href = item.taskId ? `/?task=${encodeURIComponent(item.taskId)}`
    : item.eventId ? `/schedule?event=${encodeURIComponent(item.eventId)}`
      : item.lessonId ? `/schedule?lesson=${encodeURIComponent(item.lessonId)}` : '/schedule'
  return { id: item.id, title: item.title, text: item.text, createdAt: item.at, href }
}
