import axios, { type AxiosRequestConfig, type AxiosResponse } from 'axios'
import type { Attachment, LoginCredentials, NewTask, NewTeamMember, Task, TaskHistoryEvent, TaskStatus, TeamMember, TeamSettings, User } from '../types'

interface Account extends TeamMember { password: string }

let teamSettings: TeamSettings = {
  name: 'Веб-продукт',
  description: 'Команда разработки внутренних и клиентских веб-продуктов.',
}

let accounts: Account[] = [
  { id: 1, name: 'Алексей Морозов', login: 'alexey', password: 'lead123', role: 'lead', position: 'Тимлид', active: true, joinedAt: '2026-04-12' },
  { id: 2, name: 'Мария Волкова', login: 'maria', password: 'member123', role: 'member', position: 'Дизайнер', active: true, joinedAt: '2026-05-08' },
  { id: 3, name: 'Илья Горин', login: 'ilya', password: 'member123', role: 'member', position: 'Frontend-разработчик', active: true, joinedAt: '2026-06-03' },
  { id: 4, name: 'Анна Лукина', login: 'anna', password: 'member123', role: 'member', position: 'QA-инженер', active: true, joinedAt: '2026-07-21' },
  { id: 5, name: 'Олег Миронов', login: 'oleg', password: 'member123', role: 'member', position: 'Backend-разработчик', active: false, joinedAt: '2026-08-15' },
]

const taskSeeds: Omit<Task, 'history'>[] = [
  { taskId: 101, taskName: 'Собрать референсы для лендинга', taskDesc: 'Подобрать визуальные примеры первого экрана и карточек преимуществ.', taskStatus: 'waiting', taskDeadline: '2026-10-16', attachments: [{ id: 'a1', name: 'brief.pdf', size: 840000 }], resultAttachments: [], assignee: 'Мария Волкова', createdAt: '2026-10-09' },
  { taskId: 102, taskName: 'Подготовить UI-kit', taskDesc: 'Собрать состояния кнопок, полей и базовую типографику в единую систему.', taskStatus: 'working', taskDeadline: '2026-10-14', attachments: [], resultAttachments: [], assignee: 'Илья Горин', createdAt: '2026-10-08' },
  { taskId: 103, taskName: 'Проверить адаптив', taskDesc: 'Проверить основные экраны на ширинах 1440, 1024, 768 и 375 px.', taskStatus: 'checking', taskDeadline: '2026-10-13', attachments: [{ id: 'a2', name: 'checklist.xlsx', size: 256000 }], resultAttachments: [{ id: 'r1', name: 'adaptive-report.pdf', size: 610000 }], assignee: 'Анна Лукина', createdAt: '2026-10-07' },
  { taskId: 104, taskName: 'Настроить структуру проекта', taskDesc: 'Создать базовые компоненты и подготовить маршрутизацию.', taskStatus: 'done', attachments: [], resultAttachments: [{ id: 'r2', name: 'project-source.zip', size: 2480000 }], assignee: 'Илья Горин', createdAt: '2026-10-05' },
  { taskId: 105, taskName: 'Написать тексты для профиля', taskDesc: 'Подготовить короткие подписи для роли, команды и контактных данных.', taskStatus: 'waiting', attachments: [], resultAttachments: [], assignee: 'Анна Лукина', createdAt: '2026-10-10' },
  { taskId: 106, taskName: 'Настроить CI-проверки', taskDesc: 'Добавить проверку типов и сборки для клиентской части.', taskStatus: 'done', attachments: [], resultAttachments: [{ id: 'r3', name: 'pipeline.yml', size: 12000 }], assignee: 'Илья Горин', createdAt: '2026-09-28', completedAt: '2026-10-11' },
  { taskId: 107, taskName: 'Провести дизайн-ревью', taskDesc: 'Проверить визуальную консистентность основных экранов.', taskStatus: 'done', attachments: [], resultAttachments: [{ id: 'r4', name: 'review.pdf', size: 430000 }], assignee: 'Мария Волкова', createdAt: '2026-09-25', completedAt: '2026-10-10' },
  { taskId: 108, taskName: 'Обновить документацию', taskDesc: 'Актуализировать описание локального запуска проекта.', taskStatus: 'done', attachments: [], resultAttachments: [{ id: 'r5', name: 'README.md', size: 18000 }], assignee: 'Анна Лукина', createdAt: '2026-09-20', completedAt: '2026-10-09' },
  { taskId: 109, taskName: 'Оптимизировать изображения', taskDesc: 'Сжать изображения без заметной потери качества.', taskStatus: 'done', attachments: [], resultAttachments: [{ id: 'r6', name: 'assets.zip', size: 1820000 }], assignee: 'Мария Волкова', createdAt: '2026-09-18', completedAt: '2026-10-08' },
  { taskId: 110, taskName: 'Исправить навигацию', taskDesc: 'Исправить переходы между защищёнными страницами.', taskStatus: 'done', attachments: [], resultAttachments: [{ id: 'r7', name: 'navigation.patch', size: 9000 }], assignee: 'Илья Горин', createdAt: '2026-09-15', completedAt: '2026-10-07' },
  { taskId: 111, taskName: 'Подготовить чек-лист релиза', taskDesc: 'Собрать обязательные проверки перед публикацией.', taskStatus: 'done', attachments: [], resultAttachments: [{ id: 'r8', name: 'release-checklist.xlsx', size: 96000 }], assignee: 'Анна Лукина', createdAt: '2026-09-10', completedAt: '2026-10-06' },
]

function createInitialHistory(task: Omit<Task, 'history'>): TaskHistoryEvent[] {
  const history: TaskHistoryEvent[] = []
  const add = (fromStatus: TaskStatus, toStatus: TaskStatus, suffix: string, createdAt: string) => history.push({ id: `seed-${task.taskId}-${suffix}`, type: 'status', fromStatus, toStatus, author: task.assignee, createdAt })
  if (task.taskStatus === 'working' || task.taskStatus === 'checking' || task.taskStatus === 'done') add('waiting', 'working', 'work', `${task.createdAt}T10:00:00`)
  if (task.taskStatus === 'checking' || task.taskStatus === 'done') add('working', 'checking', 'check', `${task.createdAt}T15:00:00`)
  if (task.taskStatus === 'done') add('checking', 'done', 'done', `${task.completedAt ?? task.createdAt}T17:00:00`)
  return history
}

let tasks: Task[] = taskSeeds.map((task) => {
  const normalized = task.taskId === 104 ? { ...task, completedAt: '2026-10-12' } : task
  return { ...normalized, history: createInitialHistory(normalized) }
})

const client = axios.create({ baseURL: '/mock-api' })

function response<T>(config: AxiosRequestConfig, data: T, status = 200): AxiosResponse<T> {
  return { data, status, statusText: 'OK', headers: {}, config: config as AxiosResponse<T>['config'] }
}

const cloneTask = (task: Task): Task => ({
  ...task,
  attachments: task.attachments.map((attachment) => ({ ...attachment })),
  resultAttachments: task.resultAttachments.map((attachment) => ({ ...attachment })),
  history: task.history.map((event) => ({ ...event })),
})

const publicUser = ({ password: _password, active: _active, joinedAt: _joinedAt, ...user }: Account): User => user
const publicMember = ({ password: _password, ...member }: Account): TeamMember => member

client.defaults.adapter = async (config) => {
  await new Promise((resolve) => setTimeout(resolve, 280))
  if (config.url === '/auth/login' && config.method === 'post') {
    const credentials = JSON.parse(config.data as string) as LoginCredentials
    const account = accounts.find((item) => item.login === credentials.login.trim().toLowerCase() && item.password === credentials.password)
    if (!account) throw new Error('Неверный логин или пароль')
    if (!account.active) throw new Error('Аккаунт заблокирован. Обратитесь к тимлиду')
    return response(config, publicUser(account))
  }
  if (config.url === '/team' && config.method === 'get') return response(config, { settings: { ...teamSettings }, members: accounts.map(publicMember) })
  if (config.url === '/team' && config.method === 'patch') {
    teamSettings = { ...teamSettings, ...(JSON.parse(config.data as string) as TeamSettings) }
    return response(config, { ...teamSettings })
  }
  if (config.url === '/team/members' && config.method === 'post') {
    const payload = JSON.parse(config.data as string) as NewTeamMember
    if (accounts.some((item) => item.login === payload.login.trim().toLowerCase())) throw new Error('Такой логин уже занят')
    const account: Account = { ...payload, login: payload.login.trim().toLowerCase(), id: Date.now(), active: true, joinedAt: new Date().toISOString().slice(0, 10) }
    accounts = [...accounts, account]
    return response(config, publicMember(account), 201)
  }
  if (config.method === 'patch' && config.url?.startsWith('/team/members/')) {
    const id = Number(config.url.split('/').pop())
    const payload = JSON.parse(config.data as string) as { active: boolean }
    accounts = accounts.map((account) => account.id === id ? { ...account, active: payload.active } : account)
    const account = accounts.find((item) => item.id === id)
    if (!account) throw new Error('Участник не найден')
    return response(config, publicMember(account))
  }
  if (config.url === '/tasks' && config.method === 'get') return response(config, tasks.map(cloneTask))
  if (config.url === '/tasks' && config.method === 'post') {
    const payload = JSON.parse(config.data as string) as NewTask
    const task: Task = { ...payload, resultAttachments: [], history: [], taskId: Date.now(), taskStatus: 'waiting', assignee: 'Не назначен', createdAt: new Date().toISOString().slice(0, 10) }
    tasks = [task, ...tasks]
    return response(config, cloneTask(task), 201)
  }
  if (config.method === 'patch' && config.url?.startsWith('/tasks/')) {
    const id = Number(config.url.split('/').pop())
    const payload = JSON.parse(config.data as string) as { status: TaskStatus; revisionComment?: string; resultAttachments?: Attachment[]; author: string }
    const taskIndex = tasks.findIndex((item) => item.taskId === id)
    if (taskIndex < 0) throw new Error('Задача не найдена')
    const previousTask = tasks[taskIndex]
    const historyEvent: TaskHistoryEvent = {
      id: `event-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
      type: payload.revisionComment ? 'revision' : 'status',
      fromStatus: previousTask.taskStatus,
      toStatus: payload.status,
      author: payload.author,
      createdAt: new Date().toISOString(),
      comment: payload.revisionComment,
    }
    const updatedTask: Task = {
      ...previousTask,
      taskStatus: payload.status,
      completedAt: payload.status === 'done' ? new Date().toISOString() : undefined,
      revisionComment: payload.revisionComment,
      resultAttachments: payload.resultAttachments ?? previousTask.resultAttachments,
      history: [...previousTask.history, historyEvent],
    }
    tasks = tasks.map((task, index) => index === taskIndex ? updatedTask : task)
    return response(config, cloneTask(updatedTask))
  }
  throw new Error('Неизвестный mock-запрос')
}

export const mockApi = {
  login: async (credentials: LoginCredentials) => (await client.post<User>('/auth/login', credentials)).data,
  getTeam: async () => (await client.get<{ settings: TeamSettings; members: TeamMember[] }>('/team')).data,
  updateTeam: async (settings: TeamSettings) => (await client.patch<TeamSettings>('/team', settings)).data,
  createMember: async (member: NewTeamMember) => (await client.post<TeamMember>('/team/members', member)).data,
  toggleMember: async (id: number, active: boolean) => (await client.patch<TeamMember>(`/team/members/${id}`, { active })).data,
  getTasks: async () => (await client.get<Task[]>('/tasks')).data,
  createTask: async (task: NewTask) => (await client.post<Task>('/tasks', task)).data,
  updateTaskStatus: async (taskId: number, status: TaskStatus, author: string, revisionComment?: string, resultAttachments?: Attachment[]) => (await client.patch<Task>(`/tasks/${taskId}`, { status, author, revisionComment, resultAttachments })).data,
}
