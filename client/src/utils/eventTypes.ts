export interface EventTypeOption { id: string; label: string; color: string; builtIn?: boolean; reminderMinutes?: number[] }

export const defaultEventTypes: EventTypeOption[] = [
  { id: 'meeting', label: 'Встреча', color: '#6577df', builtIn: true, reminderMinutes: [15] },
  { id: 'milestone', label: 'Контрольная точка', color: '#e58a58', builtIn: true, reminderMinutes: [1440] },
  { id: 'class', label: 'Занятие', color: '#42a682', builtIn: true, reminderMinutes: [15] },
  { id: 'presentation', label: 'Презентация', color: '#8b72e8', builtIn: true, reminderMinutes: [60] },
]

export const loadEventTypes = (): EventTypeOption[] => {
  const saved = localStorage.getItem('event-types')
  if (!saved) return defaultEventTypes
  try {
    return (JSON.parse(saved) as EventTypeOption[]).map((item) => ({ ...item, reminderMinutes: (item.reminderMinutes ?? defaultEventTypes.find((type) => type.id === item.id)?.reminderMinutes ?? []).slice(0, 1) }))
  } catch { return defaultEventTypes }
}

export const saveEventTypes = (types: EventTypeOption[]) => localStorage.setItem('event-types', JSON.stringify(types))
