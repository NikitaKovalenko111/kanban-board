import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { backendHttp } from './backendApi'
import { saveBackendSession } from './backendSession'
import { realApi } from './realApi'
import { mapEvent, mapTask } from './mappers'
import type { ColumnDto, EventDto, ProfileDto, ProjectDto, TaskDto } from './contracts'

const userId = '89aa283b-e289-4584-af55-10d331cb979f'
const otherId = 'a0c95905-d70c-47b9-a8e7-89f5c47a83b0'
const projectId = 'c522a7fd-91e1-4f1c-a2f5-5832d3da5de4'
const profile: ProfileDto = { id: userId, fullName: 'Капитан API', username: 'captain', studentGroup: 'TOP-203B', imageUrl: null, role: 'CAPTAIN', schedulePrivacy: 'BUSY', joinedAt: '2026-11-05T10:00:00Z', active: true }
const project: ProjectDto = { id: projectId, name: 'Проект API', description: '', members: [{ userId, role: 'Lead' }], sources: [] }
const columns: ColumnDto[] = [
  { id: 'backlog', projectId, title: 'Ожидает', kind: 'BACKLOG', position: 0, color: '#123456' },
  { id: 'progress', projectId, title: 'Разработка', kind: 'IN_PROGRESS', position: 1, color: '#123456' },
  { id: 'review', projectId, title: 'Проверка', kind: 'REVIEW', position: 2, color: '#123456' },
  { id: 'done', projectId, title: 'Готово', kind: 'DONE', position: 3, color: '#123456' },
]
const task: TaskDto = { id: 'task-id', projectId, columnId: 'backlog', position: 0, title: 'Задача API', description: 'ТЗ', priority: 'NORMAL', assigneeId: null, createdAt: '2026-11-05T10:00:00Z', dueAt: null, completedAt: null }
const original = backendHttp.defaults.adapter
let requests: InternalAxiosRequestConfig[]
let currentTask: TaskDto
const json = (config: InternalAxiosRequestConfig, data: unknown) => ({ data, config, status: 200, statusText: 'OK', headers: {} })
beforeEach(() => {
  realApi.clear(); requests = []; currentTask = structuredClone(task)
  saveBackendSession({ user_id: userId, login: 'captain', access_token: 'access', refresh_token: 'refresh', token_type: 'Bearer' })
  backendHttp.defaults.adapter = async config => {
    requests.push(config)
    const path = config.url
    if (path === '/me') return json(config, profile)
    if (path === '/users') return json(config, [profile])
    if (path === '/projects') return json(config, config.method === 'post' ? project : [project])
    if (path === `/projects/${projectId}`) return json(config, project)
    if (path?.endsWith('/columns')) return json(config, columns)
    if (path?.endsWith('/tasks')) return json(config, config.method === 'post' ? currentTask : [currentTask])
    if (path === '/tasks/task-id/take') { currentTask = { ...currentTask, assigneeId: userId, columnId: 'progress' }; return json(config, currentTask) }
    if (path === '/tasks/task-id/assign-to-me') { currentTask = { ...currentTask, assigneeId: userId }; return json(config, currentTask) }
    if (path === '/tasks/task-id/move') { currentTask = { ...currentTask, columnId: JSON.parse(config.data).columnId }; return json(config, currentTask) }
    if (path === '/tasks/task-id/comments') return json(config, {})
    if (path?.endsWith('/attachments')) return json(config, {})
    if (path === '/tasks/task-id') return json(config, currentTask)
    throw Error(`Unexpected request ${config.method} ${path}`)
  }
})
afterEach(() => { backendHttp.defaults.adapter = original; realApi.clear(); sessionStorage.clear() })

describe('адаптер обновлённого API', () => {
  it('отправляет UUID исполнителя строкой в PATCH задачи', async () => {
    await realApi.assignTask(task.id, otherId)
    const request = requests.find(request => request.method === 'patch' && request.url === '/tasks/task-id')!
    expect(JSON.parse(request.data)).toEqual({ assigneeId: otherId })
  })
  it('передаёт параметры расписания по контракту и не скрывает серверную ошибку 500', async () => {
    backendHttp.defaults.adapter = async config => {
      requests.push(config)
      if (config.url === '/events') throw new AxiosError('workspace request failed', 'ERR_BAD_RESPONSE', config, undefined, { ...json(config, {}), status: 500 })
      return json(config, config.url === '/event-types' ? [] : {})
    }
    await expect(realApi.getSchedule('all', { projectId: 'all', personId: userId, groups: ['ТОП-204Б'], from: '2026-11-02', to: '2026-11-09' })).rejects.toThrow('Сервер временно недоступен')
    expect(requests.find(request => request.url === '/events')?.params).toEqual({ personId: userId, groups: 'ТОП-204Б', from: '2026-11-02', to: '2026-11-09' })
  })
  it('получает настоящий профиль и проектные роли без преобразования UUID в число', async () => {
    const user = await realApi.getCurrentUser()
    expect(user).toMatchObject({ id: userId, role: 'lead', name: profile.fullName, projectRoles: { [projectId]: 'Lead' } })
    expect(requests.every(request => request.headers.get('Authorization') === 'Bearer access')).toBe(true)
  })
  it('включает создателя в участников проекта и не отправляет mock-поле color', async () => {
    await realApi.createProject({ name: 'Проект', description: 'Описание', color: '#123456' })
    expect(JSON.parse(requests.find(request => request.method === 'post')!.data)).toEqual({ name: 'Проект', description: 'Описание', members: [{ userId, role: 'Тимлид' }] })
  })
  it('загружает исходник после создания задачи, не отправляя pending ID как вложение', async () => {
    const staged = await realApi.uploadAttachment(new File(['source'], 'brief.txt', { type: 'text/plain' }))
    expect(requests).toHaveLength(0)
    await realApi.createTask({ projectId, taskName: 'Новая', taskDesc: 'ТЗ', attachments: [staged], assigneeId: otherId, taskDeadline: '2026-11-10' })
    const create = requests.find(request => request.method === 'post' && request.url?.endsWith('/tasks'))!
    const body = JSON.parse(create.data)
    expect(body).toMatchObject({ title: 'Новая', description: 'ТЗ', assigneeId: otherId, priority: 'NORMAL' })
    expect(body.dueAt).toMatch(/Z$/)
    expect(body.attachments).toBeUndefined()
    const upload = requests.find(request => request.url?.endsWith('/attachments'))!
    expect(upload.data).toBeInstanceOf(FormData)
    expect(upload.data.get('kind')).toBe('SOURCE')
    expect(requests.indexOf(upload)).toBeGreaterThan(requests.indexOf(create))
  })
  it('использует take для свободной задачи, не выполняя второй move без необходимости', async () => {
    await realApi.updateTaskStatus('task-id', 'working', 'Поддельный автор')
    expect(requests.some(request => request.url?.endsWith('/take'))).toBe(true)
    expect(requests.some(request => request.url?.endsWith('/move'))).toBe(false)
  })
  it('для назначенной себе задачи использует move, не пытаясь взять чужую', async () => {
    currentTask.assigneeId = userId
    await realApi.updateTaskStatus('task-id', 'working', 'Автор')
    expect(requests.some(request => request.url?.endsWith('/take'))).toBe(false)
    expect(JSON.parse(requests.find(request => request.url?.endsWith('/move'))!.data)).toEqual({ columnId: 'progress' })
  })
  it('отправляет замечание в переход REVIEW → IN_PROGRESS, сохраняя исходники', async () => {
    currentTask.columnId = 'review'
    const updated = await realApi.updateTaskStatus('task-id', 'working', 'Автор', 'Поправить отступы', [])
    expect(JSON.parse(requests.find(request => request.url?.endsWith('/move'))!.data)).toEqual({ columnId: 'progress', comment: 'Поправить отступы' })
    expect(updated.taskDesc).toBe('ТЗ')
  })
  it('адаптирует историю с названиями колонок и хранит все замечания', () => {
    const mapped = mapTask({ ...task, columnId: 'progress', history: [
      { id: 'created', action: 'CREATED', actorId: userId, at: task.createdAt },
      { id: 'revision', action: 'RETURNED', actorId: userId, at: task.createdAt, fromColumn: 'Проверка', toColumn: 'Разработка', comment: 'Исправить' },
    ] }, columns, [profile])
    expect(mapped.revisionComment).toBe('Исправить')
    expect(mapped.history[0].type).toBe('created')
    expect(mapped.history[1]).toMatchObject({ type: 'revision', fromStatus: 'checking', toStatus: 'working', fromLabel: 'Проверка', toLabel: 'Разработка' })
  })
  it('не ломает всю доску при истории с удалённым или переименованным названием', () => {
    expect(mapTask({ ...task, history: [{ id: 'old', action: 'MOVED', actorId: userId, at: task.createdAt, fromColumn: 'Старое название', toColumn: 'Ожидает' }] }, columns, [profile]).history[0].fromLabel).toBe('Старое название')
  })
  it('не запрашивает задачи проектов, в которых участник не состоит', async () => {
    const previous = backendHttp.defaults.adapter as (config: InternalAxiosRequestConfig) => Promise<ReturnType<typeof json>>
    backendHttp.defaults.adapter = async config => config.url === '/users' ? json(config, [{ ...profile, role: 'MEMBER' }]) : config.url === '/projects' ? json(config, [{ ...project, members: [] }]) : config.url === `/projects/${projectId}` ? json(config, { ...project, members: [] }) : previous(config)
    expect(await realApi.getTasks()).toEqual([])
    expect(requests.some(request => request.url?.endsWith('/tasks'))).toBe(false)
  })
  it('ошибка загрузки после создания проекта не выдаётся за успешную загрузку файлов', async () => {
    const file = await realApi.uploadAttachment(new File(['source'], 'brief.txt'))
    const previous = backendHttp.defaults.adapter as (config: InternalAxiosRequestConfig) => Promise<ReturnType<typeof json>>
    backendHttp.defaults.adapter = async config => {
      if (config.url?.endsWith('/sources/files')) throw new AxiosError('Upload failed', 'ERR_BAD_RESPONSE', config, undefined, { ...json(config, {}), status: 500 })
      return previous(config)
    }
    await expect(realApi.createProject({ name: 'Проект', description: '', color: '#123456', sources: [file] })).rejects.toThrow('создан, но файлы не загружены')
  })
  it('ограничивает файл серверным лимитом 10 МБ', async () => {
    const file = new File(['data'], 'big.txt'); Object.defineProperty(file, 'size', { value: 11 * 1024 * 1024 })
    await expect(realApi.uploadAttachment(file)).rejects.toThrow('10 МБ')
  })
  it('не добавляет projectId в события и передаёт RFC3339 время', async () => {
    const event: EventDto = { id: 'event', ownerId: userId, title: 'Встреча', description: '', startsAt: '2026-11-05T12:00:00Z', endsAt: null, visibility: 'TEAM', group: null, typeId: 'type', masked: false, partial: false }
    backendHttp.defaults.adapter = async config => { requests.push(config); return json(config, config.url === '/events' ? event : config.url === '/event-types' ? [] : {}) }
    await realApi.createScheduleEvent({ projectId, title: 'Встреча', description: '', startsAt: '2026-11-05T12:00:00', type: 'type', typeLabel: '', typeColor: '', reminder: 'none', author: 'Поддельный автор', category: 'team', reminderMode: 'type' })
    const body = JSON.parse(requests.find(request => request.url === '/events')!.data)
    expect(body.visibility).toBe('TEAM'); expect(body.projectId).toBeUndefined(); expect(body.ownerId).toBeUndefined(); expect(body.startsAt).toMatch(/Z$/)
  })
  it('сохраняет серверную маскировку чужого личного события', () => {
    const event: EventDto = { id: 'private', ownerId: otherId, title: 'Занят', description: '', startsAt: '2026-11-05T12:00:00Z', endsAt: null, visibility: 'PERSONAL', group: null, masked: true, partial: false }
    expect(mapEvent(event, [], [profile], {})).toMatchObject({ title: 'Занят', description: '', masked: true, category: 'personal' })
  })
})
