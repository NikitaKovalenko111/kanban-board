import type { Task, TaskStatusUpdate, User } from '../types'

export const isAssignedTo = (task: Task, user: User): boolean => task.assigneeId !== undefined ? task.assigneeId === user.id : task.assignee === user.name

export function validateTaskTransition(task: Task, update: TaskStatusUpdate, actor: User): void {
  if (update.resultAttachments?.length && update.status !== 'checking') throw new Error('Результат можно загрузить только при сдаче своей задачи')
  if (task.taskStatus === 'waiting' && update.status === 'working') {
    const assignedElsewhere = task.assignee !== 'Не назначен' && !isAssignedTo(task, actor)
    if (assignedElsewhere && !(actor.role === 'lead' && update.assignee === actor.name)) throw new Error('Задача назначена другому участнику')
    if (update.assignee && update.assignee !== actor.name) throw new Error('Можно взять задачу только на себя')
    return
  }
  if (task.taskStatus === 'working' && update.status === 'checking') {
    if (!isAssignedTo(task, actor) || (update.assignee && update.assignee !== task.assignee)) throw new Error('Отправить результат может только исполнитель')
    if (!update.resultAttachments?.length) throw new Error('Прикрепите результат работы')
    return
  }
  if (actor.role === 'lead' && task.taskStatus === 'checking' && update.status === 'done') return
  if (actor.role === 'lead' && task.taskStatus === 'checking' && update.status === 'working' && update.revisionComment?.trim()) return
  if (actor.role === 'lead' && task.taskStatus === 'done' && update.status === 'working') return
  throw new Error('Недопустимое изменение статуса задачи')
}
