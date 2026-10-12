import axios, { type AxiosRequestConfig, type AxiosResponse } from 'axios'
import type { Attachment, NewTask, Task, TaskStatus } from '../types'

let tasks: Task[] = [
  { taskId: 101, taskName: 'Собрать референсы для лендинга', taskDesc: 'Подобрать визуальные примеры первого экрана и карточек преимуществ.', taskStatus: 'waiting', taskDeadline: '2026-10-16', attachments: [{ id: 'a1', name: 'brief.pdf', size: 840000 }], resultAttachments: [], assignee: 'Мария Волкова', createdAt: '2026-10-09' },
  { taskId: 102, taskName: 'Подготовить UI-kit', taskDesc: 'Собрать состояния кнопок, полей и базовую типографику в единую систему.', taskStatus: 'working', taskDeadline: '2026-10-14', attachments: [], resultAttachments: [], assignee: 'Илья Горин', createdAt: '2026-10-08' },
  { taskId: 103, taskName: 'Проверить адаптив', taskDesc: 'Проверить основные экраны на ширинах 1440, 1024, 768 и 375 px.', taskStatus: 'checking', taskDeadline: '2026-10-13', attachments: [{ id: 'a2', name: 'checklist.xlsx', size: 256000 }], resultAttachments: [{ id: 'r1', name: 'adaptive-report.pdf', size: 610000 }], assignee: 'Анна Лукина', createdAt: '2026-10-07' },
  { taskId: 104, taskName: 'Настроить структуру проекта', taskDesc: 'Создать базовые компоненты и подготовить маршрутизацию.', taskStatus: 'done', attachments: [], resultAttachments: [{ id: 'r2', name: 'project-source.zip', size: 2480000 }], assignee: 'Илья Горин', createdAt: '2026-10-05' },
  { taskId: 105, taskName: 'Написать тексты для профиля', taskDesc: 'Подготовить короткие подписи для роли, команды и контактных данных.', taskStatus: 'waiting', attachments: [], resultAttachments: [], assignee: 'Анна Лукина', createdAt: '2026-10-10' },
]

const client = axios.create({ baseURL: '/mock-api' })

function response<T>(config: AxiosRequestConfig, data: T, status = 200): AxiosResponse<T> {
  return { data, status, statusText: 'OK', headers: {}, config: config as AxiosResponse<T>['config'] }
}

const cloneTask = (task: Task): Task => ({
  ...task,
  attachments: task.attachments.map((attachment) => ({ ...attachment })),
  resultAttachments: task.resultAttachments.map((attachment) => ({ ...attachment })),
})

client.defaults.adapter = async (config) => {
  await new Promise((resolve) => setTimeout(resolve, 280))
  if (config.url === '/tasks' && config.method === 'get') return response(config, tasks.map(cloneTask))
  if (config.url === '/tasks' && config.method === 'post') {
    const payload = JSON.parse(config.data as string) as NewTask
    const task: Task = { ...payload, resultAttachments: [], taskId: Date.now(), taskStatus: 'waiting', assignee: 'Не назначен', createdAt: new Date().toISOString().slice(0, 10) }
    tasks = [task, ...tasks]
    return response(config, cloneTask(task), 201)
  }
  if (config.method === 'patch' && config.url?.startsWith('/tasks/')) {
    const id = Number(config.url.split('/').pop())
    const payload = JSON.parse(config.data as string) as { status: TaskStatus; revisionComment?: string; resultAttachments?: Attachment[] }
    const taskIndex = tasks.findIndex((item) => item.taskId === id)
    if (taskIndex < 0) throw new Error('Задача не найдена')
    const updatedTask: Task = {
      ...tasks[taskIndex],
      taskStatus: payload.status,
      revisionComment: payload.revisionComment,
      resultAttachments: payload.resultAttachments ?? tasks[taskIndex].resultAttachments,
    }
    tasks = tasks.map((task, index) => index === taskIndex ? updatedTask : task)
    return response(config, cloneTask(updatedTask))
  }
  throw new Error('Неизвестный mock-запрос')
}

export const mockApi = {
  getTasks: async () => (await client.get<Task[]>('/tasks')).data,
  createTask: async (task: NewTask) => (await client.post<Task>('/tasks', task)).data,
  updateTaskStatus: async (taskId: number, status: TaskStatus, revisionComment?: string, resultAttachments?: Attachment[]) => (await client.patch<Task>(`/tasks/${taskId}`, { status, revisionComment, resultAttachments })).data,
}
