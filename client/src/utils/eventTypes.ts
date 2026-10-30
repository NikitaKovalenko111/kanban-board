export interface EventTypeOption { id: string; label: string; color: string; builtIn?: boolean }

export const defaultEventTypes: EventTypeOption[] = [
  { id: 'meeting', label: 'Встреча', color: '#6577df', builtIn: true },
  { id: 'milestone', label: 'Контрольная точка', color: '#e58a58', builtIn: true },
  { id: 'class', label: 'Занятие', color: '#42a682', builtIn: true },
  { id: 'presentation', label: 'Презентация', color: '#8b72e8', builtIn: true },
]

export const loadEventTypes = (): EventTypeOption[] => {
  const saved = localStorage.getItem('event-types')
  if (!saved) return defaultEventTypes
  try { return JSON.parse(saved) as EventTypeOption[] } catch { return defaultEventTypes }
}

export const saveEventTypes = (types: EventTypeOption[]) => localStorage.setItem('event-types', JSON.stringify(types))
