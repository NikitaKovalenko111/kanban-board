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
const project: ProjectDto = { id: projectId, name: 'Проект API', canViewBoard: true, description: '', members: [{ userId, role: 'Lead' }], sources: [] }
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
    if (path === `/users/${otherId}` && config.method === 'patch') return json(config, { ...profile, id: otherId })
    if (path === '/users') return json(config, [profile])
    if (path === '/projects') return json(config, config.method === 'post' ? project : [project])
    if (path === `/projects/${projectId}`) return json(config, project)
    if (path?.endsWith('/columns')) return json(config, columns)
    if (path?.endsWith('/tasks')) return json(config, config.method === 'post' ? currentTask : [currentTask])
    if (path === '/tasks/task-id/take') { currentTask = { ...currentTask, assigneeId: userId, columnId: 'progress' }; return json(config, currentTask) }
    if (path === '/tasks/task-id/assign-to-me') { currentTask = { ...currentTask, assigneeId: userId }; return json(config, currentTask) }
    if (path === '/tasks/task-id/move') { currentTask = { ...currentTask, columnId: JSON.parse(config.data).columnId }; return json(config, currentTask) }
    if (path === '/tasks/task-id/comments') return json(config, {})
    if (path?.endsWith('/attachments/links')) return json(config, {})
    if (path?.endsWith('/attachments')) return json(config, {})
    if (path === '/tasks/task-id') return json(config, currentTask)
    throw Error(`Unexpected request ${config.method} ${path}`)
  }
})
afterEach(() => { backendHttp.defaults.adapter = original; realApi.clear(); sessionStorage.clear() })

describe('адаптер обновлённого API', () => {
  it('отправляет сохраняемый цвет при редактировании проекта вместе с UUID участников', async () => {
    await realApi.updateProject({ id: projectId, name: 'Проект', description: '', color: '#123abc', members: [{ userId, role: 'Тимлид' }] })
    expect(JSON.parse(requests.find(request => request.method === 'patch')!.data)).toMatchObject({ color: '#123abc', members: [{ userId, role: 'Тимлид' }] })
  })
  it('не загружает уже сохранённую ссылку результата повторно после доработки', async () => {
    currentTask = { ...currentTask, columnId: 'progress', assigneeId: userId, attachments: [{ id: 'saved-link', kind: 'RESULT', contentKind: 'LINK', title: 'Результат', url: 'https://example.com/result', uploadedBy: userId, uploadedAt: task.createdAt }] }
    await realApi.updateTaskStatus(task.id, 'checking', 'Автор', undefined, [{ id: 'saved-link', name: 'Результат', url: 'https://example.com/result', size: 0 }])
    expect(requests.some(request => request.url?.endsWith('/attachments/links'))).toBe(false)
  })
  it('отключает напоминания пустым PUT и восстанавливает исходные через DELETE', async () => {
    backendHttp.defaults.adapter = async config => {
      requests.push(config)
      if (config.url === '/tasks/task-id') return json(config, currentTask)
      if (config.url === '/events/event') return json(config, { id: 'event', ownerId: userId, title: 'Событие', startsAt: task.createdAt, endsAt: null, visibility: 'TEAM', group: null, masked: false, partial: false })
      if (config.url === '/event-types') return json(config, [])
      return json(config, config.method === 'get' ? { 'event:event': [] } : {})
    }
    await realApi.updateEventReminders('event', { reminderMode: 'custom', reminderMinutes: [] })
    expect(JSON.parse(requests.find(request => request.method === 'put')!.data)).toEqual({ minutes: [] })
    await realApi.updateEventReminders('event', { reminderMode: 'type', reminderMinutes: [] })
    expect(requests.some(request => request.method === 'delete' && request.url === '/me/reminders/event%3Aevent')).toBe(true)
  })
  it('не выдумывает напоминания типа, когда сервер вернул пустую эффективную настройку', () => {
    const event: EventDto = { id: 'event', ownerId: userId, title: 'Событие', startsAt: task.createdAt, endsAt: null, visibility: 'TEAM', group: null, typeId: 'type', masked: false, partial: false }
    expect(mapEvent(event, [{ id: 'type', title: 'Тип', color: '#123456', ownerId: userId, defaultReminders: [90] }], [], { 'event:event': [] }).reminderMinutes).toEqual([])
    expect(mapEvent(event, [], [], {}).reminderMinutes).toEqual([])
  })
  it('преобразует смесь FILE/LINK в исходники и результат без обращения к отсутствующему file', () => {
    const mapped = mapTask({ ...task, attachments: [
      { id: 'source-link', kind: 'SOURCE', contentKind: 'LINK', title: 'ТЗ', url: 'https://example.com/spec', uploadedBy: userId, uploadedAt: task.createdAt },
      { id: 'result-link', kind: 'RESULT', contentKind: 'LINK', title: 'Результат', url: 'https://example.com/result', uploadedBy: userId, uploadedAt: task.createdAt },
    ] }, columns, [profile])
    expect(mapped.attachments[0]).toMatchObject({ name: 'ТЗ', url: 'https://example.com/spec' })
    expect(mapped.resultAttachments[0]).toMatchObject({ name: 'Результат', url: 'https://example.com/result' })
  })
  it.each([['low', 'LOW'], ['medium', 'NORMAL'], ['high', 'HIGH']] as const)('сохраняет UI-приоритет %s в серверном формате %s', async (priority, wire) => {
    const previous = backendHttp.defaults.adapter as (config: InternalAxiosRequestConfig) => Promise<ReturnType<typeof json>>
    backendHttp.defaults.adapter = async config => {
      if (config.url === '/tasks/task-id' && config.method === 'patch') currentTask = { ...currentTask, priority: JSON.parse(config.data).priority }
      return previous(config)
    }
    await realApi.createTask({ projectId, taskName: 'Приоритет', taskDesc: 'ТЗ', attachments: [], priority })
    expect(JSON.parse(requests.find(request => request.method === 'post')!.data).priority).toBe(wire)
    expect((await realApi.updateTaskPriority(task.id, priority)).priority).toBe(priority)
    expect(JSON.parse(requests.find(request => request.method === 'patch')!.data)).toEqual({ priority: wire })
  })
  it('не изменяет приоритет уже взятой задачи через API', async () => {
    currentTask.columnId = 'progress'
    await expect(realApi.updateTaskPriority(task.id, 'high')).rejects.toThrow('только до начала работы')
    expect(requests.some(request => request.method === 'patch')).toBe(false)
  })
  it('сохраняет логин и группу через поддерживаемые PATCH профиля и участника', async () => {
    const previous = backendHttp.defaults.adapter as (config: InternalAxiosRequestConfig) => Promise<ReturnType<typeof json>>
    backendHttp.defaults.adapter = async config => {
      if (config.url === '/me' && config.method === 'patch') { requests.push(config); return json(config, { ...profile, studentGroup: 'TOP-204B' }) }
      return previous(config)
    }
    expect(await realApi.updateAccount(userId, { login: profile.username, studentGroup: 'TOP-204B' })).toMatchObject({ id: userId, studentGroup: 'TOP-204B' })
    expect(JSON.parse(requests.find(request => request.method === 'patch')!.data)).toEqual({ username: profile.username, studentGroup: 'TOP-204B' })
    await realApi.updateAccount(otherId, { login: 'other', studentGroup: 'TOP-204B' })
    expect(JSON.parse(requests.find(request => request.url === `/users/${otherId}`)!.data)).toEqual({ username: 'other', studentGroup: 'TOP-204B' })
  })
  it('загружает ссылки SOURCE и RESULT без файлового multipart', async () => {
    const link = { id: 'link:1', name: 'ТЗ', size: 0, url: 'https://example.com' }
    await realApi.createTask({ projectId, taskName: 'Задача', taskDesc: 'ТЗ', attachments: [link] })
    expect(JSON.parse(requests.find(request => request.url?.endsWith('/attachments/links'))!.data)).toEqual({ kind: 'SOURCE', title: 'ТЗ', url: link.url })
    currentTask = { ...currentTask, columnId: 'progress', assigneeId: userId }
    await realApi.updateTaskStatus(task.id, 'checking', 'Автор', undefined, [{ ...link, id: 'link:2' }])
    expect(JSON.parse(requests.filter(request => request.url?.endsWith('/attachments/links'))[1]!.data).kind).toBe('RESULT')
    expect(requests.some(request => request.data instanceof FormData)).toBe(false)
  })
  it('при ошибке перехода повторяет сдачу без повторной загрузки уже принятого файла', async () => {
    currentTask = { ...currentTask, columnId: 'progress', assigneeId: userId }
    const file = await realApi.uploadAttachment(new File(['result'], 'result.txt'))
    const previous = backendHttp.defaults.adapter as (config: InternalAxiosRequestConfig) => Promise<ReturnType<typeof json>>
    let fail = true
    backendHttp.defaults.adapter = async config => {
      if (fail && config.url?.endsWith('/move')) { fail = false; throw new AxiosError('Offline', 'ERR_NETWORK', config) }
      return previous(config)
    }
    await expect(realApi.updateTaskStatus(task.id, 'checking', 'Автор', undefined, [file])).rejects.toThrow('Не удалось связаться')
    await expect(realApi.updateTaskStatus(task.id, 'checking', 'Автор', undefined, [file])).resolves.toMatchObject({ taskStatus: 'checking' })
    expect(requests.filter(request => request.url?.endsWith('/attachments'))).toHaveLength(1)
  })
  it('использует canViewBoard сервера вместо локального членства', async () => {
    const previous = backendHttp.defaults.adapter as (config: InternalAxiosRequestConfig) => Promise<ReturnType<typeof json>>
    backendHttp.defaults.adapter = async config => config.url === '/projects' ? json(config, [{ ...project, members: [] }]) : config.url === '/users' ? json(config, [{ ...profile, role: 'MEMBER' }]) : previous(config)
    expect(await realApi.getTasks()).toHaveLength(1)
    expect(requests.some(request => request.url === `/projects/${projectId}/tasks`)).toBe(true)
  })
  it('получает личные счётчики через GET /tasks без загрузки всех досок и деталей', async () => {
    expect(await realApi.getMyTasks()).toHaveLength(1)
    expect(requests.filter(request => request.url === '/tasks')).toHaveLength(1)
    expect(requests.some(request => request.url === `/projects/${projectId}/tasks` || request.url === '/tasks/task-id')).toBe(false)
  })
  it('отклоняет динамические или неполные колонки вместо выбора случайной', async () => {
    const previous = backendHttp.defaults.adapter as (config: InternalAxiosRequestConfig) => Promise<ReturnType<typeof json>>
    backendHttp.defaults.adapter = async config => config.url?.endsWith('/columns') ? json(config, [...columns, { ...columns[1], id: 'extra' }]) : previous(config)
    await expect(realApi.getTasks()).rejects.toThrow('некорректные колонки')
  })
  it('сохраняет все интервалы типа и не отправляет повторы', async () => {
    backendHttp.defaults.adapter = async config => { requests.push(config); return json(config, { id: 'type', title: 'Тип', color: '#123456', ownerId: userId, defaultReminders: [30, 90] }) }
    expect((await realApi.saveEventType({ id: 'type', label: 'Тип', color: '#123456', reminderMinutes: [30, 90] }, false)).reminderMinutes).toEqual([30, 90])
    expect(JSON.parse(requests[0].data).defaultReminders).toEqual([30, 90])
    await expect(realApi.saveEventType({ id: 'type', label: 'Тип', color: '#123456', reminderMinutes: [30, 30] }, false)).rejects.toThrow('не должны повторяться')
  })
  it('отправляет ссылку проекта JSON-запросом, без попытки загрузить файл', async () => {
    const previous = backendHttp.defaults.adapter as (config: InternalAxiosRequestConfig) => Promise<ReturnType<typeof json>>
    backendHttp.defaults.adapter = async config => {
      if (config.url?.endsWith('/sources/links')) { requests.push(config); return json(config, {}) }
      return previous(config)
    }
    await realApi.addProjectSources({ projectId, author: 'Имя', files: [{ id: 'link:1', name: 'Репозиторий', size: 0, url: 'https://example.com/' }] })
    expect(JSON.parse(requests.find(request => request.url?.endsWith('/sources/links'))!.data)).toEqual({ title: 'Репозиторий', url: 'https://example.com/' })
    expect(requests.some(request => request.url?.endsWith('/sources/files'))).toBe(false)
  })
  it('сетевая ошибка расписания остаётся ошибкой, без подмены моками', async () => {
    await realApi.getCurrentUser()
    backendHttp.defaults.adapter = async config => {
      if (config.url === '/events') throw new AxiosError('Offline', 'ERR_NETWORK', config)
      return json(config, config.url === '/event-types' ? [] : {})
    }
    await expect(realApi.getSchedule('all')).rejects.toThrow('Не удалось связаться')
  })
  it('групповой просмотр использует group без активного представителя и исключает личные события', async () => {
    const previous = backendHttp.defaults.adapter as (config: InternalAxiosRequestConfig) => Promise<ReturnType<typeof json>>
    backendHttp.defaults.adapter = async config => config.url === '/users' ? json(config, [profile, { ...profile, id: otherId, studentGroup: 'TOP-204B' }]) : previous(config)
    await realApi.getCurrentUser()
    backendHttp.defaults.adapter = async config => {
      requests.push(config)
      if (config.url === '/events') return json(config, [
        { id: 'team', ownerId: userId, title: 'Общее', startsAt: task.createdAt, endsAt: null, visibility: 'TEAM', group: null, masked: false, partial: false },
        { id: 'private', ownerId: otherId, title: 'Личное', startsAt: task.createdAt, endsAt: null, visibility: 'PERSONAL', group: null, masked: false, partial: false },
        { id: 'group', ownerId: otherId, title: 'Группа 204', startsAt: task.createdAt, endsAt: null, visibility: 'GROUP', group: 'TOP-204B', masked: false, partial: false },
      ])
      return json(config, config.url === '/event-types' ? [] : config.url === '/schedule/lessons' ? { lessons: [], source: 'ACTUAL' } : {})
    }
    const result = await realApi.getSchedule('all', { projectId: 'all', groups: ['TOP-204B'], from: '2026-11-02', to: '2026-11-09' })
    expect(result.map(event => event.id)).toEqual(['team', 'group'])
    expect(requests.find(request => request.url === '/events')?.params).toMatchObject({ group: 'TOP-204B' })
  })
  it('находит дату занятия по lessonId, а не по времени доставки уведомления', async () => {
    await realApi.getCurrentUser()
    backendHttp.defaults.adapter = async config => {
      requests.push(config)
      return json(config, { id: 'lesson-id', externalId: 'external', date: '2026-11-06', start: '08:00', end: '09:20' })
    }
    expect(await realApi.getLessonDate('lesson-id', '2026-11-05T22:45:00Z')).toBe('2026-11-06T08:00')
    expect(requests.at(-1)?.url).toBe('/schedule/lessons/lesson-id')
  })
  it('сохраняет ошибку удалённого занятия вместо догадки по названию', async () => {
    backendHttp.defaults.adapter = async config => { throw new AxiosError('Missing', 'ERR_BAD_RESPONSE', config, undefined, { ...json(config, {}), status: 404 }) }
    await expect(realApi.getLessonDate('missing')).rejects.toThrow()
  })
  it('отправляет UUID исполнителя строкой в PATCH задачи', async () => {
    await realApi.assignTask(task.id, otherId)
    const request = requests.find(request => request.method === 'patch' && request.url === '/tasks/task-id')!
    expect(JSON.parse(request.data)).toEqual({ assigneeId: otherId })
  })
  it('загружает расписание без устаревшего groups и сохраняет все напоминания', async () => {
    await realApi.getCurrentUser()
    backendHttp.defaults.adapter = async config => {
      requests.push(config)
      if (config.url === '/events') return json(config, [])
      if (config.url === '/schedule/lessons') return json(config, { lessons: [], source: 'ACTUAL' })
      return json(config, config.url === '/event-types' ? [] : {})
    }
    await expect(realApi.getSchedule('all', { projectId: 'all', personId: userId, from: '2026-11-02', to: '2026-11-09' })).resolves.toEqual([])
    expect(requests.find(request => request.url === '/events')?.params).toEqual({ personId: userId, from: '2026-11-01', to: '2026-11-10' })
  })
  it('получает настоящий профиль и проектные роли без преобразования UUID в число', async () => {
    const user = await realApi.getCurrentUser()
    expect(user).toMatchObject({ id: userId, role: 'lead', name: profile.fullName, projectRoles: { [projectId]: 'Lead' } })
    expect(requests.every(request => request.headers.get('Authorization') === 'Bearer access')).toBe(true)
  })
  it('включает создателя в участников проекта и отправляет сохраняемый color', async () => {
    await realApi.createProject({ name: 'Проект', description: 'Описание', color: '#123456' })
    expect(JSON.parse(requests.find(request => request.method === 'post')!.data)).toEqual({ name: 'Проект', description: 'Описание', color: '#123456', members: [{ userId, role: 'Тимлид' }] })
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
  it('не запрашивает детали проектов с canViewBoard=false даже для капитана', async () => {
    const previous = backendHttp.defaults.adapter as (config: InternalAxiosRequestConfig) => Promise<ReturnType<typeof json>>
    backendHttp.defaults.adapter = async config => config.url === '/users' ? json(config, [{ ...profile, role: 'MEMBER' }]) : config.url === '/projects' ? json(config, [{ id: projectId, name: 'Недоступный проект', canViewBoard: false }]) : config.url === `/projects/${projectId}` ? json(config, { ...project, members: [] }) : previous(config)
    expect(await realApi.getTasks()).toEqual([])
    expect(requests.some(request => request.url?.endsWith('/tasks') || request.url?.endsWith('/columns') || request.url === `/projects/${projectId}`)).toBe(false)
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
  it('добавляет projectId только командному событию и передаёт RFC3339 время', async () => {
    const event: EventDto = { id: 'event', ownerId: userId, title: 'Встреча', description: '', startsAt: '2026-11-05T12:00:00Z', endsAt: null, visibility: 'TEAM', group: null, typeId: 'type', masked: false, partial: false }
    backendHttp.defaults.adapter = async config => { requests.push(config); return json(config, config.url === '/events' ? event : config.url === '/event-types' ? [] : {}) }
    await realApi.createScheduleEvent({ projectId, title: 'Встреча', description: '', startsAt: '2026-11-05T12:00:00', type: 'type', typeLabel: '', typeColor: '', reminder: 'none', author: 'Поддельный автор', category: 'team', reminderMode: 'type' })
    const body = JSON.parse(requests.find(request => request.url === '/events')!.data)
    expect(body.visibility).toBe('TEAM'); expect(body.projectId).toBe(projectId); expect(body.ownerId).toBeUndefined(); expect(body.startsAt).toMatch(/Z$/)
  })
  it('сохраняет серверную маскировку чужого личного события', () => {
    const event: EventDto = { id: 'private', ownerId: otherId, title: 'Занят', description: '', startsAt: '2026-11-05T12:00:00Z', endsAt: null, visibility: 'PERSONAL', group: null, masked: true, partial: false }
    expect(mapEvent(event, [], [profile], {})).toMatchObject({ title: 'Занят', description: '', masked: true, category: 'personal' })
  })
})
