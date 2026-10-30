import type { Task, User, Project, ScheduleEvent } from '../types'

export const user: User = { id: 1, name: 'Тестовый Пользователь', login: 'tester', role: 'lead', position: 'Разработчик', projectRoles: { test: 'Разработчик' } }
export const project: Project = { id: 'test', name: 'Тестовый проект', description: '', color: '#123456', sources: [] }
export const task: Task = {
  taskId: 1, projectId: project.id, taskName: 'Подготовить макет', taskDesc: 'Описание задачи',
  taskStatus: 'waiting', attachments: [], resultAttachments: [], history: [], comments: [],
  assignee: user.name, createdAt: '2026-10-01',
}

export const scheduleEvent: ScheduleEvent = {
  id: 1, projectId: project.id, title: 'Встреча', description: '',
  startsAt: '2026-10-23T10:00:00', type: 'meeting',
  typeLabel: 'Встреча', typeColor: '#6577df', group: 'both', reminder: '15-min',
  author: user.name,
}
