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
        if (open) await realApi.readNotifications()
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
  return { items: items.map(item => ({ id: item.id, title: item.title, text: item.text, createdAt: item.at, href: item.taskId ? `/?task=${encodeURIComponent(item.taskId)}` : item.eventId ? `/schedule?event=${encodeURIComponent(item.eventId)}&date=${item.at.slice(0, 10)}` : '/schedule' })), readIds: items.filter(item => item.read).map(item => item.id), error }
}
