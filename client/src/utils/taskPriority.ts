import type { TaskPriority } from '../types'

export const priorityLabels: Record<TaskPriority, string> = { low: 'Низкий', medium: 'Обычный', high: 'Высокий' }
export function validatePriority(priority: unknown): TaskPriority {
  if (priority === undefined) return 'medium'
  if (priority !== 'low' && priority !== 'medium' && priority !== 'high') throw new Error('Выберите приоритет задачи')
  return priority
}
