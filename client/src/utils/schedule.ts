import type { EventCategory, ScheduleEvent, User } from '../types'

export const eventCategory = (event: ScheduleEvent): EventCategory => event.category ?? (event.type === 'class' ? 'class' : event.type === 'milestone' ? 'deadline' : 'team')

export function visibleSchedule(events: ScheduleEvent[], viewer: User, owners: User[]): ScheduleEvent[] {
  return events.flatMap((event) => {
    if (eventCategory(event) !== 'personal' || event.ownerId === viewer.id) return [{ ...event }]
    const privacy = owners.find((owner) => owner.id === event.ownerId)?.eventPrivacy ?? 'busy'
    if (privacy === 'hidden') return []
    if (privacy === 'busy') return [{ ...event, title: 'Занят', description: '', location: undefined, author: '', type: 'personal', typeLabel: 'Личное событие', typeColor: '#7c8799', group: 'both' as const, reminder: 'none' as const }]
    if (privacy === 'title') return [{ ...event, description: '', location: undefined, reminder: 'none' as const }]
    return [{ ...event, location: undefined, reminder: 'none' as const }]
  })
}
