import type { Task, User, Project } from '../types'

export const user: User = { id: 1, name: 'Тестовый Пользователь', login: 'tester', role: 'lead', position: 'Разработчик' }
export const project: Project = { id: 'test', name: 'Тестовый проект', description: '', color: '#123456', sources: [] }
export const task: Task = {
  taskId: 1, projectId: project.id, taskName: 'Подготовить макет', taskDesc: 'Описание задачи',
  taskStatus: 'waiting', attachments: [], resultAttachments: [], history: [],
  assignee: user.name, createdAt: '2026-10-01',
}
