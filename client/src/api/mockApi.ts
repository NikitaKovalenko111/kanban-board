import axios, { type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { loadFile, saveFile } from './fileStorage'
import { visibleSchedule } from '../utils/schedule'
import { loadEventTypes } from '../utils/eventTypes'
import { eventReminderMinutes, validateReminderMinutes } from '../utils/reminders'
import { validateTaskTransition } from '../utils/taskPermissions'
import type { EventPrivacy, EventReminders, TaskStatusUpdate } from '../types'
import type { AddProjectSourcesPayload, AddTaskCommentPayload, Attachment, LoginCredentials, NewProject, NewScheduleEvent, NewTask, NewTeamMember, Project, ProjectSource, ScheduleEvent, Task, TaskComment, TaskHistoryEvent, TaskStatus, TeamMember, TeamSettings, User } from '../types'

interface Account extends TeamMember { password: string }
interface ProjectUpdate { id: string; name: string; description: string; color: string; members: { userId: number; role: string }[] }

let teamSettings: TeamSettings = {
  name: 'Веб-продукт',
  description: 'Команда разработки внутренних и клиентских веб-продуктов.',
}

let accounts: Account[] = [
  { id: 1, name: 'Алексей Морозов', login: 'alexey', password: 'lead123', role: 'lead', position: 'Тимлид', projectRoles: { 'ai-hackathon': 'Капитан проекта', 'web-course': 'Куратор', 'internal-portal': 'Тимлид' }, active: true, joinedAt: '2026-04-12' },
  { id: 2, name: 'Мария Волкова', login: 'maria', password: 'member123', role: 'member', position: 'Дизайнер', projectRoles: { 'ai-hackathon': 'UI/UX-дизайнер', 'web-course': 'Дизайнер интерфейсов', 'internal-portal': 'Продуктовый дизайнер' }, active: true, joinedAt: '2026-05-08' },
  { id: 3, name: 'Илья Горин', login: 'ilya', password: 'member123', role: 'member', position: 'Frontend-разработчик', projectRoles: { 'ai-hackathon': 'Frontend-разработчик', 'web-course': 'Староста группы', 'internal-portal': 'Frontend-разработчик' }, active: true, joinedAt: '2026-06-03' },
  { id: 4, name: 'Анна Лукина', login: 'anna', password: 'member123', role: 'member', position: 'QA-инженер', projectRoles: { 'ai-hackathon': 'QA-инженер', 'web-course': 'Участник', 'internal-portal': 'QA-инженер' }, active: true, joinedAt: '2026-07-21' },
  { id: 5, name: 'Олег Миронов', login: 'oleg', password: 'member123', role: 'member', position: 'Backend-разработчик', projectRoles: { 'ai-hackathon': 'Backend-разработчик', 'internal-portal': 'Backend-разработчик' }, active: false, joinedAt: '2026-08-15' },
]

let projects: Project[] = [
  { id: 'ai-hackathon', name: 'AI Hackathon', description: 'Командный проект для осеннего хакатона', color: '#8b72e8', sources: [{ id: 'source-1', name: 'Регламент хакатона.pdf', size: 684000, uploadedAt: '2026-10-18T12:30:00', uploadedBy: 'Алексей Морозов' }] },
  { id: 'web-course', name: 'Веб-разработка', description: 'Учебная дисциплина и лабораторные работы', color: '#4e8bd8', sources: [{ id: 'source-2', name: 'Материалы курса.zip', size: 2480000, uploadedAt: '2026-10-15T09:20:00', uploadedBy: 'Алексей Морозов' }] },
  { id: 'internal-portal', name: 'Внутренний портал', description: 'Развитие внутреннего продукта команды', color: '#42a682', sources: [] },
]

let scheduleEvents: ScheduleEvent[] = [
  { id: 201, projectId: 'ai-hackathon', title: 'Установочная встреча', description: 'Распределить роли и утвердить идею решения.', startsAt: '2026-10-31T18:00:00', endsAt: '2026-10-31T19:00:00', type: 'meeting', typeLabel: 'Встреча', typeColor: '#6577df', group: 'both', reminder: '15-min', location: 'Переговорная 2', author: 'Алексей Морозов' },
  { id: 202, projectId: 'ai-hackathon', title: 'Промежуточный питч', description: 'Показать прототип и получить обратную связь.', startsAt: '2026-11-02T15:30:00', type: 'presentation', typeLabel: 'Презентация', typeColor: '#8b72e8', group: 'both', reminder: '1-hour', location: 'Главный зал', author: 'Алексей Морозов' },
  { id: 203, projectId: 'web-course', title: 'Практика по React Router', description: 'Маршрутизация и защищённые страницы.', startsAt: '2026-10-31T12:10:00', endsAt: '2026-10-31T13:40:00', type: 'class', typeLabel: 'Занятие', typeColor: '#42a682', group: '203', reminder: '15-min', location: 'Аудитория 304', author: 'Алексей Морозов' },
  { id: 204, projectId: 'web-course', title: 'Сдача лабораторной №4', description: 'Канбан-доска и управление состоянием.', startsAt: '2026-11-05T23:59:00', type: 'milestone', typeLabel: 'Контрольная точка', typeColor: '#e58a58', group: '204', reminder: '1-day', author: 'Алексей Морозов' },
  { id: 205, projectId: 'internal-portal', title: 'Планирование спринта', description: 'Приоритизация задач следующего этапа.', startsAt: '2026-11-01T10:00:00', endsAt: '2026-11-01T11:00:00', type: 'meeting', typeLabel: 'Встреча', typeColor: '#6577df', group: 'both', reminder: 'at-start', location: 'Онлайн', author: 'Алексей Морозов' },
]

const taskSeeds: Omit<Task, 'history' | 'comments' | 'projectId'>[] = [
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

function createInitialHistory(task: Omit<Task, 'history' | 'comments'>): TaskHistoryEvent[] {
  const history: TaskHistoryEvent[] = []
  const add = (fromStatus: TaskStatus, toStatus: TaskStatus, suffix: string, createdAt: string) => history.push({ id: `seed-${task.taskId}-${suffix}`, type: 'status', fromStatus, toStatus, author: task.assignee, createdAt })
  if (task.taskStatus === 'working' || task.taskStatus === 'checking' || task.taskStatus === 'done') add('waiting', 'working', 'work', `${task.createdAt}T10:00:00`)
  if (task.taskStatus === 'checking' || task.taskStatus === 'done') add('working', 'checking', 'check', `${task.createdAt}T15:00:00`)
  if (task.taskStatus === 'done') add('checking', 'done', 'done', `${task.completedAt ?? task.createdAt}T17:00:00`)
  return history
}

let tasks: Task[] = taskSeeds.map((task) => {
  const projectId = [101, 103, 107, 109].includes(task.taskId) ? 'ai-hackathon' : [105, 108, 111].includes(task.taskId) ? 'web-course' : 'internal-portal'
  const withProject: Omit<Task, 'history' | 'comments'> = { ...task, projectId }
  const normalized = task.taskId === 104 ? { ...withProject, completedAt: '2026-10-12' } : withProject
  const comments: TaskComment[] = task.taskId === 103 ? [{ id: 'comment-103-1', author: 'Алексей Морозов', text: 'Проверьте также горизонтальную ориентацию планшета.', createdAt: '2026-10-12T14:20:00' }] : []
  return { ...normalized, assigneeId: accounts.find((account) => account.name === normalized.assignee)?.id, history: createInitialHistory(normalized), comments }
})

accounts = accounts.map((account) => ({ ...account, eventPrivacy: (localStorage.getItem(`event-privacy-${account.id}`) as EventPrivacy | null) ?? 'busy' }))

const client = axios.create({ baseURL: import.meta.env.VITE_API_URL || '/mock-api', withCredentials: true })
client.interceptors.response.use((result) => result, (reason: unknown) => {
  if (axios.isAxiosError(reason)) {
    const body = reason.response?.data as { message?: string } | undefined
    return Promise.reject(new Error(body?.message ?? (reason.response ? 'Не удалось выполнить запрос' : 'Не удалось связаться с сервером. Проверьте подключение')))
  }
  return Promise.reject(reason)
})

const currentActor = (): Account => {
  let session: User | null = null
  try { session = JSON.parse(localStorage.getItem('mock-session') ?? 'null') as User | null } catch { /* No valid session. */ }
  const actor = accounts.find((account) => account.id === session?.id)
  if (!actor?.active) throw new Error('Войдите в аккаунт с действующим доступом')
  return actor
}
const requireLead = () => {
  const actor = currentActor()
  if (actor.role !== 'lead') throw new Error('Действие доступно только капитану')
  return actor
}
// Demonstration documents have real, readable text contents; original external files were not supplied.
const seedFiles = new Map<string, Blob>()
const seedAttachment = (file: Attachment, text: string): Attachment => {
  const blob = new Blob([text], { type: 'text/plain;charset=utf-8' })
  seedFiles.set(file.id, blob)
  return { ...file, name: file.name.replace(/\.[^.]+$/, '') + '.txt', size: blob.size }
}
projects = projects.map((project) => ({ ...project, sources: project.sources.map((source) => ({ ...source, uploadedById: accounts.find((account) => account.name === source.uploadedBy)?.id, ...seedAttachment(source, project.name + '\n' + project.description) })) }))
tasks = tasks.map((task) => ({ ...task, attachments: task.attachments.map((file) => seedAttachment(file, task.taskName + '\n' + task.taskDesc)), resultAttachments: task.resultAttachments.map((file) => seedAttachment(file, 'Результат: ' + task.taskName)) }))
const validateFiles = async (files: Attachment[]) => {
  for (const file of files) {
    const blob = seedFiles.get(file.id) ?? await loadFile(file.id)
    if (blob.size !== file.size) throw new Error('Данные файла повреждены. Загрузите файл повторно')
  }
}

function response<T>(config: AxiosRequestConfig, data: T, status = 200): AxiosResponse<T> {
  return { data, status, statusText: 'OK', headers: {}, config: config as AxiosResponse<T>['config'] }
}

const cloneTask = (task: Task): Task => ({
  ...task,
  attachments: task.attachments.map((attachment) => ({ ...attachment })),
  resultAttachments: task.resultAttachments.map((attachment) => ({ ...attachment })),
  history: task.history.map((event) => ({ ...event })),
  comments: task.comments.map((comment) => ({ ...comment })),
})

const publicUser = ({ password: _password, active: _active, joinedAt: _joinedAt, ...user }: Account): User => user
const publicMember = ({ password: _password, ...member }: Account): TeamMember => member

if (!import.meta.env.VITE_API_URL) client.defaults.adapter = async (config) => {
  await new Promise((resolve) => setTimeout(resolve, 280))
  if (config.url !== '/auth/login') currentActor()
  if (config.url === '/users/me/password' && config.method === 'post') {
    const actor = currentActor()
    const { currentPassword, newPassword } = JSON.parse(config.data as string) as { currentPassword: string; newPassword: string }
    if (typeof currentPassword !== 'string' || currentPassword !== actor.password) throw new Error('Текущий пароль указан неверно')
    if (typeof newPassword !== 'string' || newPassword.length < 6) throw new Error('Новый пароль должен содержать не менее 6 символов')
    if (newPassword === currentPassword) throw new Error('Новый пароль должен отличаться от текущего')
    accounts = accounts.map((account) => account.id === actor.id ? { ...account, password: newPassword } : account)
    return response(config, { success: true })
  }
  if (config.method === 'delete' && config.url?.match(/^\/projects\/[^/]+\/sources\/[^/]+$/)) {
    const [, , projectId, , sourceId] = config.url.split('/')
    const actor = currentActor()
    const project = projects.find((item) => item.id === projectId)
    if (!project) throw new Error('Проект не найден')
    const source = project.sources.find((file) => file.id === sourceId)
    if (!source) throw new Error('Источник не найден')
    const isOwner = source.uploadedById !== undefined ? source.uploadedById === actor.id : source.uploadedBy === actor.name
    if (actor.role !== 'lead' && !isOwner) throw new Error('Удалить источник может только капитан или его автор')
    const updated = { ...project, sources: project.sources.filter((file) => file.id !== sourceId) }
    projects = projects.map((item) => item.id === projectId ? updated : item)
    return response(config, updated)
  }
  if (config.url === '/attachments' && config.method === 'post') {
    const file = (config.data as FormData).get('file')
    if (!(file instanceof Blob)) throw new Error('Выберите файл')
    if (file.size > 25 * 1024 * 1024) throw new Error('Размер файла не должен превышать 25 МБ')
    const id = crypto.randomUUID()
    await saveFile(id, file)
    return response(config, { id, name: (file as File).name || 'file', size: file.size }, 201)
  }
  if (config.url === '/users/me' && config.method === 'patch') {
    const actor = currentActor()
    const { eventPrivacy } = JSON.parse(config.data as string) as { eventPrivacy: EventPrivacy }
    if (!['hidden', 'busy', 'title', 'details'].includes(eventPrivacy)) throw new Error('Неверная настройка приватности')
    accounts = accounts.map((account) => account.id === actor.id ? { ...account, eventPrivacy } : account)
    localStorage.setItem(`event-privacy-${actor.id}`, eventPrivacy)
    return response(config, publicUser(accounts.find((account) => account.id === actor.id)!))
  }
  if (config.url?.match(/^\/team\/members\/\d+\/password$/) && config.method === 'post') {
    const actor = requireLead()
    const id = Number(config.url.split('/')[3])
    if (id === actor.id) throw new Error('Нельзя сбросить собственный пароль в этом окне')
    if (!accounts.some((account) => account.id === id)) throw new Error('Участник не найден')
    const password = 'team-' + crypto.randomUUID().slice(0, 12)
    accounts = accounts.map((account) => account.id === id ? { ...account, password } : account)
    return response(config, { password })
  }
  if (config.url === '/projects' && config.method === 'get') return response(config, projects.map((project) => ({ ...project })))
  if (config.method === 'patch' && config.url?.match(/^\/projects\/[^/]+$/)) {
    const session = JSON.parse(localStorage.getItem('mock-session') ?? 'null') as User | null
    const actor = accounts.find((account) => account.id === session?.id)
    if (!actor?.active || actor.role !== 'lead') throw new Error('Управлять проектом может только тимлид')
    const id = config.url.split('/')[2]
    const payload = JSON.parse(config.data as string) as ProjectUpdate
    const existing = projects.find((project) => project.id === id)
    if (!existing) throw new Error('Проект не найден')
    if (!payload.name.trim()) throw new Error('Укажите название проекта')
    const project = { ...existing, name: payload.name.trim(), description: payload.description.trim(), color: payload.color }
    projects = projects.map((item) => item.id === id ? project : item)
    accounts = accounts.map((account) => {
      const projectRoles = { ...account.projectRoles }
      const membership = payload.members.find((member) => member.userId === account.id)
      if (membership) projectRoles[id] = membership.role.trim() || 'Участник проекта'
      else delete projectRoles[id]
      return { ...account, projectRoles }
    })
    return response(config, { project, members: accounts.map(publicMember) })
  }
  if (config.url === '/projects' && config.method === 'post') {
    const actor = requireLead()
    const payload = JSON.parse(config.data as string) as NewProject
    await validateFiles(payload.sources ?? [])
    const baseId = payload.name.trim().toLowerCase().replace(/[^a-zа-яё0-9]+/gi, '-').replace(/^-|-$/g, '') || 'project'
    const id = projects.some((project) => project.id === baseId) ? `${baseId}-${Date.now()}` : baseId
    const project: Project = { ...payload, name: payload.name.trim(), description: payload.description.trim(), id, sources: (payload.sources ?? []).map((file) => ({ ...file, uploadedAt: new Date().toISOString(), uploadedBy: actor.name, uploadedById: actor.id })) }
    accounts = accounts.map((account) => account.id === actor.id ? { ...account, projectRoles: { ...account.projectRoles, [id]: 'Капитан проекта' } } : account)
    projects = [...projects, project]
    return response(config, { ...project }, 201)
  }
  if (config.url?.match(/^\/projects\/[^/]+\/sources$/) && config.method === 'post') {
    const projectId = config.url.split('/')[2]
    const payload = JSON.parse(config.data as string) as AddProjectSourcesPayload
    await validateFiles(payload.files)
    const projectIndex = projects.findIndex((project) => project.id === projectId)
    if (projectIndex < 0) throw new Error('Проект не найден')
    const uploadedAt = new Date().toISOString()
    const sources: ProjectSource[] = payload.files.map((file) => ({ ...file, uploadedAt, uploadedBy: currentActor().name, uploadedById: currentActor().id }))
    const updatedProject = { ...projects[projectIndex], sources: [...projects[projectIndex].sources, ...sources] }
    projects = projects.map((project, index) => index === projectIndex ? updatedProject : project)
    return response(config, { ...updatedProject, sources: updatedProject.sources.map((source) => ({ ...source })) })
  }
  if (config.url?.startsWith('/schedule/') && config.method === 'get') {
    const projectId = config.url.split('/').pop()
    return response(config, visibleSchedule(scheduleEvents.filter((event) => projectId === 'all' || event.projectId === projectId).map((event) => ({ ...event, ...(event.reminderMode ? { reminderMinutes: eventReminderMinutes(event, loadEventTypes()) } : {}) })), currentActor(), accounts))
  }
  if (config.url === '/schedule' && config.method === 'post') {
    const actor = currentActor()
    const payload = JSON.parse(config.data as string) as NewScheduleEvent
    if (payload.reminderMode && !['type', 'custom'].includes(payload.reminderMode)) throw new Error('Неизвестный режим напоминаний')
    if (payload.reminderMinutes) payload.reminderMinutes = validateReminderMinutes(payload.reminderMinutes)
    const event: ScheduleEvent = { ...payload, author: actor.name, ownerId: actor.id, id: Date.now() }
    scheduleEvents = [...scheduleEvents, event]
    return response(config, { ...event }, 201)
  }
  if (config.method === 'patch' && config.url?.match(/^\/schedule\/\d+\/reminders$/)) {
    const actor = currentActor()
    const id = Number(config.url.split('/')[2])
    const previous = scheduleEvents.find((event) => event.id === id)
    if (!previous) throw new Error('Событие не найдено')
    if (previous.ownerId !== actor.id && !(actor.role === 'lead' && previous.category !== 'personal')) throw new Error('Нельзя менять напоминания чужого события')
    const payload = JSON.parse(config.data as string) as EventReminders
    if (!['type', 'custom'].includes(payload.reminderMode)) throw new Error('Неизвестный режим напоминаний')
    const updated: ScheduleEvent = { ...previous, reminder: 'none', reminderMode: payload.reminderMode, reminderMinutes: validateReminderMinutes(payload.reminderMinutes) }
    scheduleEvents = scheduleEvents.map((event) => event.id === id ? updated : event)
    return response(config, { ...updated, reminderMinutes: eventReminderMinutes(updated, loadEventTypes()) })
  }
  if (config.method === 'get' && config.url?.match(/^\/attachments\/[^/]+\/download$/)) {
    const attachmentId = config.url.split('/')[2]
    return response(config, seedFiles.get(attachmentId) ?? await loadFile(attachmentId))
  }
  if (config.url === '/auth/login' && config.method === 'post') {
    const credentials = JSON.parse(config.data as string) as LoginCredentials
    const account = accounts.find((item) => item.login === credentials.login.trim().toLowerCase() && item.password === credentials.password)
    if (!account) throw new Error('Неверный логин или пароль')
    if (!account.active) throw new Error('Аккаунт заблокирован. Обратитесь к тимлиду')
    return response(config, publicUser(account))
  }
  if (config.url === '/team' && config.method === 'get') return response(config, { settings: { ...teamSettings }, members: accounts.map(publicMember) })
  if (config.url === '/team' && config.method === 'patch') {
    requireLead()
    teamSettings = { ...teamSettings, ...(JSON.parse(config.data as string) as TeamSettings) }
    return response(config, { ...teamSettings })
  }
  if (config.url === '/team/members' && config.method === 'post') {
    requireLead()
    const payload = JSON.parse(config.data as string) as NewTeamMember
    if (!payload.name.trim() || !/^[a-z0-9._-]+$/.test(payload.login.trim().toLowerCase()) || payload.password.length < 6 || !['lead', 'member'].includes(payload.role)) throw new Error('Проверьте имя, логин и пароль (от 6 символов)')
    if (accounts.some((item) => item.login === payload.login.trim().toLowerCase())) throw new Error('Такой логин уже занят')
    const account: Account = { ...payload, login: payload.login.trim().toLowerCase(), id: Date.now(), active: true, joinedAt: new Date().toISOString().slice(0, 10) }
    accounts = [...accounts, account]
    return response(config, publicMember(account), 201)
  }
  if (config.method === 'patch' && config.url?.startsWith('/team/members/')) {
    const actor = requireLead()
    const id = Number(config.url.split('/').pop())
    if (id === actor.id) throw new Error('Нельзя заблокировать свой аккаунт')
    const payload = JSON.parse(config.data as string) as { active: boolean }
    accounts = accounts.map((account) => account.id === id ? { ...account, active: payload.active } : account)
    const account = accounts.find((item) => item.id === id)
    if (!account) throw new Error('Участник не найден')
    return response(config, publicMember(account))
  }
  if (config.url === '/tasks' && config.method === 'get') return response(config, tasks.map(cloneTask))
  if (config.url === '/tasks' && config.method === 'post') {
    requireLead()
    const payload = JSON.parse(config.data as string) as NewTask
    const assignee = payload.assigneeId === undefined ? undefined : accounts.find((account) => account.id === payload.assigneeId && account.active)
    if (payload.assigneeId !== undefined && !assignee) throw new Error('Выберите активного участника')
    await validateFiles(payload.attachments)
    const task: Task = { ...payload, resultAttachments: [], history: [], comments: [], taskId: Date.now(), taskStatus: 'waiting', assignee: assignee?.name ?? 'Не назначен', assigneeId: assignee?.id, createdAt: new Date().toISOString().slice(0, 10) }
    tasks = [task, ...tasks]
    return response(config, cloneTask(task), 201)
  }
  if (config.method === 'patch' && config.url?.match(/^\/tasks\/\d+\/assignee$/)) {
    const actor = requireLead()
    const id = Number(config.url.split('/')[2])
    const previous = tasks.find((task) => task.taskId === id)
    if (!previous) throw new Error('Задача не найдена')
    if (previous.taskStatus !== 'waiting' && previous.taskStatus !== 'working') throw new Error('Исполнителя можно менять только в ожидании или в работе')
    const { assigneeId } = JSON.parse(config.data as string) as { assigneeId: number | null }
    const assignee = assigneeId === null ? undefined : accounts.find((account) => account.id === assigneeId && account.active)
    if (assigneeId !== null && !assignee) throw new Error('Выберите активного участника')
    if (!assignee && previous.taskStatus === 'working') throw new Error('У задачи в работе должен быть исполнитель')
    if (previous.assigneeId === assignee?.id && previous.assignee === (assignee?.name ?? 'Не назначен')) return response(config, cloneTask(previous))
    const updated: Task = {
      ...previous, assignee: assignee?.name ?? 'Не назначен', assigneeId: assignee?.id,
      history: [...previous.history, { id: crypto.randomUUID(), type: 'assignment', fromStatus: previous.taskStatus, toStatus: previous.taskStatus, author: actor.name, createdAt: new Date().toISOString(), comment: `${previous.assignee} → ${assignee?.name ?? 'Не назначен'}` }],
    }
    tasks = tasks.map((task) => task.taskId === id ? updated : task)
    return response(config, cloneTask(updated))
  }
  if (config.method === 'patch' && config.url?.startsWith('/tasks/')) {
    const id = Number(config.url.split('/').pop())
    const payload = JSON.parse(config.data as string) as TaskStatusUpdate
    const taskIndex = tasks.findIndex((item) => item.taskId === id)
    if (taskIndex < 0) throw new Error('Задача не найдена')
    const previousTask = tasks[taskIndex]
    const actor = currentActor()
    validateTaskTransition(previousTask, payload, actor)
    if (payload.status === 'checking') await validateFiles(payload.resultAttachments ?? [])
    const historyEvent: TaskHistoryEvent = {
      id: `event-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
      type: payload.revisionComment ? 'revision' : 'status',
      fromStatus: previousTask.taskStatus,
      toStatus: payload.status,
      author: actor.name,
      createdAt: new Date().toISOString(),
      comment: payload.revisionComment,
    }
    const updatedTask: Task = {
      ...previousTask,
      taskStatus: payload.status,
      completedAt: payload.status === 'done' ? new Date().toISOString() : undefined,
      revisionComment: payload.revisionComment,
      resultAttachments: payload.resultAttachments ?? previousTask.resultAttachments,
      assignee: previousTask.taskStatus === 'waiting' && payload.status === 'working' ? actor.name : previousTask.assignee,
      assigneeId: previousTask.taskStatus === 'waiting' && payload.status === 'working' ? actor.id : previousTask.assigneeId,
      history: [...previousTask.history, historyEvent],
    }
    tasks = tasks.map((task, index) => index === taskIndex ? updatedTask : task)
    return response(config, cloneTask(updatedTask))
  }
  if (config.method === 'post' && config.url?.match(/^\/tasks\/\d+\/comments$/)) {
    const taskId = Number(config.url.split('/')[2])
    const payload = JSON.parse(config.data as string) as AddTaskCommentPayload
    if (!payload.text.trim()) throw new Error('Введите комментарий')
    const comment: TaskComment = { id: crypto.randomUUID(), author: currentActor().name, text: payload.text.trim(), createdAt: new Date().toISOString() }
    const taskIndex = tasks.findIndex((task) => task.taskId === taskId)
    if (taskIndex < 0) throw new Error('Задача не найдена')
    const updatedTask = { ...tasks[taskIndex], comments: [...tasks[taskIndex].comments, comment] }
    tasks = tasks.map((task, index) => index === taskIndex ? updatedTask : task)
    return response(config, cloneTask(updatedTask), 201)
  }
  throw new Error('Неизвестный mock-запрос')
}

export const mockApi = {
  updateEventReminders: async (id: number, reminders: EventReminders) => (await client.patch<ScheduleEvent>(`/schedule/${id}/reminders`, reminders)).data,
  uploadAttachment: async (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return (await client.post<Attachment>('/attachments', form)).data
  },
  resetPassword: async (id: number) => (await client.post<{ password: string }>(`/team/members/${id}/password`)).data,
  changePassword: async (payload: { currentPassword: string; newPassword: string }) => (await client.post<{ success: boolean }>('/users/me/password', payload)).data,
  updatePrivacy: async (eventPrivacy: EventPrivacy) => (await client.patch<User>('/users/me', { eventPrivacy })).data,
  getProjects: async () => (await client.get<Project[]>('/projects')).data,
  createProject: async (project: NewProject) => (await client.post<Project>('/projects', project)).data,
  updateProject: async (project: ProjectUpdate) => (await client.patch<{ project: Project; members: TeamMember[] }>(`/projects/${project.id}`, project)).data,
  addProjectSources: async (payload: AddProjectSourcesPayload) => (await client.post<Project>(`/projects/${payload.projectId}/sources`, payload)).data,
  deleteProjectSource: async (projectId: string, sourceId: string) => (await client.delete<Project>(`/projects/${projectId}/sources/${sourceId}`)).data,
  getSchedule: async (projectId: string) => (await client.get<ScheduleEvent[]>(`/schedule/${projectId}`)).data,
  createScheduleEvent: async (event: NewScheduleEvent) => (await client.post<ScheduleEvent>('/schedule', event)).data,
  downloadAttachment: async (attachmentId: string) => (await client.get<Blob>(`/attachments/${attachmentId}/download`, { responseType: 'blob' })).data,
  login: async (credentials: LoginCredentials) => (await client.post<User>('/auth/login', credentials)).data,
  getTeam: async () => (await client.get<{ settings: TeamSettings; members: TeamMember[] }>('/team')).data,
  updateTeam: async (settings: TeamSettings) => (await client.patch<TeamSettings>('/team', settings)).data,
  createMember: async (member: NewTeamMember) => (await client.post<TeamMember>('/team/members', member)).data,
  toggleMember: async (id: number, active: boolean) => (await client.patch<TeamMember>(`/team/members/${id}`, { active })).data,
  getTasks: async () => (await client.get<Task[]>('/tasks')).data,
  createTask: async (task: NewTask) => (await client.post<Task>('/tasks', task)).data,
  assignTask: async (taskId: number, assigneeId: number | null) => (await client.patch<Task>(`/tasks/${taskId}/assignee`, { assigneeId })).data,
  updateTaskStatus: async (taskId: number, status: TaskStatus, author: string, revisionComment?: string, resultAttachments?: Attachment[], assignee?: string) => (await client.patch<Task>(`/tasks/${taskId}`, { status, author, revisionComment, resultAttachments, assignee })).data,
  addTaskComment: async (payload: AddTaskCommentPayload) => (await client.post<Task>(`/tasks/${payload.taskId}/comments`, payload)).data,
}
