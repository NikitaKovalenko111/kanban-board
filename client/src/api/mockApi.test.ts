import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import type { mockApi as Api } from './mockApi'
import { scheduleEvent } from '../test/fixtures'

let api: typeof Api
// Новый модуль для каждого теста изолирует изменяемые данные mock API.
beforeEach(async () => {
  vi.resetModules()
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2026-10-22T10:00:00Z'))
  api = (await import('./mockApi')).mockApi
  const actor = await request(api.login({ login: 'alexey', password: 'lead123' }))
  localStorage.setItem('mock-session', JSON.stringify(actor))
})
afterEach(() => vi.useRealTimers())
async function request<T>(promise: Promise<T>): Promise<T> {
  const result = promise.then(value => ({ value }), error => ({ error }))
  await vi.runAllTimersAsync()
  const settled = await result
  if ('error' in settled) throw settled.error
  return settled.value
}

async function signIn(login: string, password = 'member123') {
  const user = await request(api.login({ login, password }))
  localStorage.setItem('mock-session', JSON.stringify(user))
  return user
}
const readText = (blob: Blob) => new Promise<string>((resolve, reject) => {
  const reader = new FileReader()
  reader.onload = () => resolve(reader.result as string)
  reader.onerror = () => reject(reader.error)
  reader.readAsText(blob)
})

describe('mockApi', () => {
  it('событие наследует интервалы типа, индивидуальные напоминания не меняют тип', async () => {
    localStorage.setItem('event-types', JSON.stringify([{ id: scheduleEvent.type, label: 'Встреча', color: '#123456', reminderMinutes: [30] }]))
    const event = await request(api.createScheduleEvent({ ...scheduleEvent, projectId: 'ai-hackathon', reminderMode: 'type', reminderMinutes: [30] }))
    expect((await request(api.getSchedule('all'))).find((item) => item.id === event.id)?.reminderMinutes).toEqual([30])
    expect(await request(api.updateEventReminders(event.id, { reminderMode: 'custom', reminderMinutes: [45] }))).toMatchObject({ reminderMode: 'custom', reminderMinutes: [45] })
    expect(JSON.parse(localStorage.getItem('event-types')!)[0].reminderMinutes).toEqual([30])
    localStorage.setItem('event-types', JSON.stringify([{ id: scheduleEvent.type, label: 'Встреча', color: '#123456', reminderMinutes: [120] }]))
    expect((await request(api.getSchedule('all'))).find((item) => item.id === event.id)?.reminderMinutes).toEqual([45])
    expect(await request(api.updateEventReminders(event.id, { reminderMode: 'type', reminderMinutes: [] }))).toMatchObject({ reminderMinutes: [120] })
  })

  it('участник меняет напоминания своего события, чужие личные события защищены', async () => {
    await signIn('maria')
    const event = await request(api.createScheduleEvent({ ...scheduleEvent, category: 'personal', reminderMode: 'custom', reminderMinutes: [90] }))
    expect(await request(api.updateEventReminders(event.id, { reminderMode: 'custom', reminderMinutes: [30] }))).toMatchObject({ reminderMinutes: [30] })
    await signIn('alexey', 'lead123')
    await expect(request(api.updateEventReminders(event.id, { reminderMode: 'custom', reminderMinutes: [15] }))).rejects.toThrow('Нельзя менять напоминания чужого события')
    const hidden = (await request(api.getSchedule('all'))).find((item) => item.id === event.id)!
    expect(hidden.reminderMinutes).toEqual([])
  })

  it.each(['team', 'group', 'class', 'deadline', 'personal'] as const)('участник может создать событие категории %s', async (category) => {
    const actor = await signIn('maria')
    const created = await request(api.createScheduleEvent({ ...scheduleEvent, category, ownerId: 1, author: 'Поддельный автор' }))
    expect(created).toMatchObject({ category, ownerId: actor.id, author: actor.name })
  })

  it('не сохраняет недопустимые интервалы напоминаний', async () => {
    await expect(request(api.createScheduleEvent({ ...scheduleEvent, reminderMode: 'custom', reminderMinutes: [-30] }))).rejects.toThrow('Укажите целое число минут от 0')
    const event = await request(api.createScheduleEvent({ ...scheduleEvent, reminderMode: 'custom', reminderMinutes: [90] }))
    await expect(request(api.updateEventReminders(event.id, { reminderMode: 'custom', reminderMinutes: [1.5] }))).rejects.toThrow()
    expect((await request(api.getSchedule('all'))).find((item) => item.id === event.id)?.reminderMinutes).toEqual([90])
  })
  it('капитан назначает задачу участнику при создании и только исполнитель берёт её в работу', async () => {
    const created = await request(api.createTask({ projectId: 'ai-hackathon', taskName: 'Назначенная задача', taskDesc: 'ТЗ', attachments: [], assigneeId: 2 }))
    expect(created).toMatchObject({ assigneeId: 2, assignee: 'Мария Волкова', taskStatus: 'waiting' })
    await signIn('ilya')
    await expect(request(api.updateTaskStatus(created.taskId, 'working', 'Илья'))).rejects.toThrow()
    await signIn('maria')
    expect(await request(api.updateTaskStatus(created.taskId, 'working', 'Мария'))).toMatchObject({ assigneeId: 2, taskStatus: 'working' })
  })

  it('переназначение сохраняет ТЗ, исходники и статус, записывается в историю', async () => {
    const file = await request(api.uploadAttachment(new File(['ТЗ'], 'brief.txt')))
    const created = await request(api.createTask({ projectId: 'ai-hackathon', taskName: 'Задача', taskDesc: 'ТЗ', attachments: [file], assigneeId: 1 }))
    await request(api.updateTaskStatus(created.taskId, 'working', 'Капитан'))
    const updated = await request(api.assignTask(created.taskId, 2))
    expect(updated).toMatchObject({ assigneeId: 2, assignee: 'Мария Волкова', taskDesc: 'ТЗ', attachments: [file], taskStatus: 'working' })
    expect(updated.history.at(-1)).toMatchObject({ type: 'assignment', author: 'Алексей Морозов', comment: 'Алексей Морозов → Мария Волкова' })
    const files = [await request(api.uploadAttachment(new File(['Результат'], 'result.txt')))]
    await expect(request(api.updateTaskStatus(created.taskId, 'checking', 'Капитан', undefined, files))).rejects.toThrow()
    await signIn('maria')
    expect(await request(api.updateTaskStatus(created.taskId, 'checking', 'Мария', undefined, files))).toMatchObject({ taskStatus: 'checking', assigneeId: 2 })
  })

  it('обычный участник не может назначить задачу даже через mock API', async () => {
    const created = await request(api.createTask({ projectId: 'ai-hackathon', taskName: 'Задача', taskDesc: 'ТЗ', attachments: [] }))
    await signIn('maria')
    await expect(request(api.assignTask(created.taskId, 2))).rejects.toThrow('Действие доступно только капитану')
    expect((await request(api.getTasks())).find((task) => task.taskId === created.taskId)?.assignee).toBe('Не назначен')
  })

  it.each([5, 999])('не назначает заблокированного или отсутствующего участника %s', async (assigneeId) => {
    const payload = { projectId: 'ai-hackathon', taskName: 'Задача', taskDesc: 'ТЗ', attachments: [] }
    await expect(request(api.createTask({ ...payload, assigneeId }))).rejects.toThrow('Выберите активного участника')
    const created = await request(api.createTask(payload))
    await expect(request(api.assignTask(created.taskId, assigneeId))).rejects.toThrow('Выберите активного участника')
  })

  it('позволяет снять назначение только в ожидании, не меняет исполнителя сданной задачи', async () => {
    const created = await request(api.createTask({ projectId: 'ai-hackathon', taskName: 'Задача', taskDesc: 'ТЗ', attachments: [], assigneeId: 2 }))
    expect(await request(api.assignTask(created.taskId, null))).toMatchObject({ assignee: 'Не назначен', assigneeId: undefined })
    await request(api.updateTaskStatus(created.taskId, 'working', 'Капитан'))
    await expect(request(api.assignTask(created.taskId, null))).rejects.toThrow('У задачи в работе должен быть исполнитель')
    const files = [await request(api.uploadAttachment(new File(['ok'], 'result.txt')))]
    await request(api.updateTaskStatus(created.taskId, 'checking', 'Капитан', undefined, files))
    await expect(request(api.assignTask(created.taskId, 2))).rejects.toThrow('Исполнителя можно менять только в ожидании или в работе')
  })
  it.each([
    ['alexey', 'lead123'],
    ['maria', 'member123'],
  ])('пользователь %s меняет собственный пароль с проверкой текущего', async (login, oldPassword) => {
    await signIn(login, oldPassword)
    await expect(request(api.changePassword({ currentPassword: oldPassword, newPassword: 'updated123' }))).resolves.toEqual({ success: true })
    await expect(request(api.login({ login, password: oldPassword }))).rejects.toThrow('Неверный логин или пароль')
    expect(await request(api.login({ login, password: 'updated123' }))).toMatchObject({ login })
  })

  it('не меняет пароль при неверном текущем пароле', async () => {
    await expect(request(api.changePassword({ currentPassword: 'wrong123', newPassword: 'updated123' }))).rejects.toThrow('Текущий пароль указан неверно')
    expect(await request(api.login({ login: 'alexey', password: 'lead123' }))).toMatchObject({ id: 1 })
  })

  it.each(['12345', 'lead123'])('отклоняет короткий или прежний новый пароль: %s', async (newPassword) => {
    await expect(request(api.changePassword({ currentPassword: 'lead123', newPassword }))).rejects.toThrow()
    expect(await request(api.login({ login: 'alexey', password: 'lead123' }))).toMatchObject({ id: 1 })
  })

  it('смена пароля участником не затрагивает чужой аккаунт', async () => {
    await signIn('maria')
    const payload = { currentPassword: 'member123', newPassword: 'updated123', userId: 1 }
    await request(api.changePassword(payload))
    expect(await request(api.login({ login: 'alexey', password: 'lead123' }))).toMatchObject({ id: 1 })
    expect(await request(api.login({ login: 'maria', password: 'updated123' }))).toMatchObject({ id: 2 })
  })
  it('нормализует логин и не раскрывает пароль', async () => {
    const user = await request(api.login({ login: ' ALEXEY ', password: 'lead123' }))
    expect(user).toMatchObject({ login: 'alexey', role: 'lead' })
    expect(user).not.toHaveProperty('password')
    expect(user).not.toHaveProperty('active')
  })

  it.each([
    ['alexey', 'wrong', 'Неверный логин или пароль'],
    ['oleg', 'member123', 'Аккаунт заблокирован. Обратитесь к тимлиду'],
  ])('отклоняет вход %s с недопустимыми данными', async (login, password, message) => {
    await expect(request(api.login({ login, password }))).rejects.toThrow(message)
  })

  it('создаёт задачу, сохраняет результат, историю и возврат на доработку', async () => {
    const created = await request(api.createTask({ projectId: 'ai-hackathon', taskName: 'Новая задача', taskDesc: 'Описание', attachments: [] }))
    expect(created).toMatchObject({ taskStatus: 'waiting', assignee: 'Не назначен', history: [], resultAttachments: [] })
    const files = [await request(api.uploadAttachment(new File(['Результат'], 'result.txt', { type: 'text/plain' })))]
    await request(api.updateTaskStatus(created.taskId, 'working', 'Поддельный автор'))
    await request(api.updateTaskStatus(created.taskId, 'checking', 'Поддельный автор', undefined, files))
    const done = await request(api.updateTaskStatus(created.taskId, 'done', 'Поддельный автор'))
    expect(done.completedAt).toBe(new Date().toISOString())
    expect(done.resultAttachments).toEqual(files)
    expect(done.history).toHaveLength(3)
    expect(done.history.every((event) => event.author === 'Алексей Морозов')).toBe(true)
    const revised = await request(api.updateTaskStatus(created.taskId, 'working', 'Тимлид', 'Исправить', []))
    expect(revised).toMatchObject({ taskStatus: 'working', revisionComment: 'Исправить', resultAttachments: [] })
    expect(revised.completedAt).toBeUndefined()
    expect(revised.history).toHaveLength(4)
    expect(revised.history[3]).toMatchObject({ type: 'revision', comment: 'Исправить', fromStatus: 'done', toStatus: 'working' })
    expect(await request(api.getTasks())).toContainEqual(revised)
  })

  it('возвращает независимые копии вложений и истории задач', async () => {
    const tasks = await request(api.getTasks())
    const original = tasks.find(task => task.attachments.length && task.history.length)!
    const expected = structuredClone(original)
    original.attachments[0].name = 'changed'
    original.history[0].author = 'changed'
    expect((await request(api.getTasks())).find(task => task.taskId === original.taskId)).toEqual(expected)
  })

  it('сообщает об обновлении отсутствующей задачи', async () => {
    await expect(request(api.updateTaskStatus(-1, 'working', 'Тимлид'))).rejects.toThrow('Задача не найдена')
  })

  it('создаёт проект и выбирает расписание только этого проекта', async () => {
    const project = await request(api.createProject({ name: ' Новый проект ', description: ' Описание ', color: '#123456' }))
    expect(project).toMatchObject({ name: 'Новый проект', description: 'Описание', sources: [] })
    expect(await request(api.getProjects())).toContainEqual(project)
    const { id: _id, ...eventPayload } = scheduleEvent
    const event = await request(api.createScheduleEvent({ ...eventPayload, projectId: project.id }))
    expect(await request(api.getSchedule(project.id))).toEqual([event])
    expect(await request(api.getSchedule('all'))).toContainEqual(event)
  })

  it('создаёт участника и запрещает повторный нормализованный логин', async () => {
    const member = { name: 'Участник', login: ' NEW ', password: 'secret', role: 'member' as const, position: 'QA', projectRoles: { 'ai-hackathon': 'QA' } }
    expect(await request(api.createMember(member))).toMatchObject({ login: 'new', active: true })
    await expect(request(api.createMember({ ...member, login: 'new' }))).rejects.toThrow('Такой логин уже занят')
    expect((await request(api.getTeam())).members.every(member => !('password' in member))).toBe(true)
  })

  it('участник не может взять чужую задачу даже с подменённым автором', async () => {
    await signIn('maria')
    const before = (await request(api.getTasks())).find((task) => task.taskId === 105)!
    await expect(request(api.updateTaskStatus(105, 'working', 'Анна Лукина', undefined, undefined, 'Мария Волкова'))).rejects.toThrow('другому участнику')
    expect((await request(api.getTasks())).find((task) => task.taskId === 105)).toEqual(before)
  })

  it('участник и капитан не могут сдать чужую задачу или сменить исполнителя при сдаче', async () => {
    const files = [{ id: 'forged', name: 'result.txt', size: 1 }]
    await signIn('maria')
    await expect(request(api.updateTaskStatus(102, 'checking', 'Илья Горин', undefined, files))).rejects.toThrow('только исполнитель')
    await signIn('alexey', 'lead123')
    await expect(request(api.updateTaskStatus(102, 'checking', 'Илья Горин', undefined, files, 'Алексей Морозов'))).rejects.toThrow('только исполнитель')
  })

  it('капитан явно переназначает задачу на себя; исходники и ТЗ сохраняются', async () => {
    const before = (await request(api.getTasks())).find((task) => task.taskId === 101)!
    await expect(request(api.updateTaskStatus(101, 'working', 'Алексей Морозов'))).rejects.toThrow('другому участнику')
    const working = await request(api.updateTaskStatus(101, 'working', 'Не автор', undefined, undefined, 'Алексей Морозов'))
    expect(working.assignee).toBe('Алексей Морозов')
    const file = await request(api.uploadAttachment(new File(['done'], 'done.txt')))
    const checking = await request(api.updateTaskStatus(101, 'checking', 'Не автор', undefined, [file]))
    expect(checking.attachments).toEqual(before.attachments)
    expect(checking.taskDesc).toBe(before.taskDesc)
    expect(checking.history.every((event) => event.author === 'Алексей Морозов')).toBe(true)
  })

  it('пустой результат и невозможные переходы статуса отклоняются', async () => {
    await signIn('ilya')
    await expect(request(api.updateTaskStatus(102, 'checking', 'Илья Горин', undefined, []))).rejects.toThrow('Прикрепите результат')
    await expect(request(api.updateTaskStatus(102, 'done', 'Илья Горин'))).rejects.toThrow('Недопустимое')
  })

  it('совпадающее имя нового аккаунта не даёт права сдачи чужой задачи', async () => {
    await request(api.createMember({ name: 'Илья Горин', login: 'other-ilya', password: 'secret123', role: 'member', projectRoles: {}, position: '' }))
    await signIn('other-ilya', 'secret123')
    await expect(request(api.updateTaskStatus(102, 'checking', 'Илья Горин', undefined, [{ id: 'fake', name: 'fake', size: 1 }]))).rejects.toThrow('только исполнитель')
  })

  it('хранит и скачивает настоящее содержимое файла и добавляет его в новый проект', async () => {
    const attachment = await request(api.uploadAttachment(new File(['Файл проекта\u0000123'], 'source.txt', { type: 'text/plain' })))
    const project = await request(api.createProject({ name: 'С файлами', description: '', color: '#123456', sources: [attachment] }))
    expect(project.sources[0]).toMatchObject(attachment)
    expect(await request(readText(await request(api.downloadAttachment(attachment.id))))).toBe('Файл проекта\u0000123')
    await expect(request(api.downloadAttachment('missing-file'))).rejects.toThrow('Файл недоступен')
  })

  it('блокировка, создание аккаунта и сброс пароля доступны только капитану', async () => {
    await signIn('maria')
    const forged = JSON.parse(localStorage.getItem('mock-session')!)
    localStorage.setItem('mock-session', JSON.stringify({ ...forged, role: 'lead' }))
    await expect(request(api.toggleMember(3, false))).rejects.toThrow('только капитану')
    await expect(request(api.resetPassword(3))).rejects.toThrow('только капитану')
    await expect(request(api.createMember({ name: 'X', login: 'x', password: 'secret123', role: 'lead', projectRoles: {}, position: '' }))).rejects.toThrow('только капитану')
    await signIn('alexey', 'lead123')
    const reset = await request(api.resetPassword(3))
    await expect(request(api.login({ login: 'ilya', password: 'member123' }))).rejects.toThrow('Неверный')
    expect(await request(api.login({ login: 'ilya', password: reset.password }))).toMatchObject({ id: 3 })
    await request(api.toggleMember(3, false))
    await expect(request(api.login({ login: 'ilya', password: reset.password }))).rejects.toThrow('заблокирован')
  })

  it.each([
    ['hidden', null, null],
    ['busy', 'Занят', ''],
    ['title', 'Личный приём', ''],
    ['details', 'Личный приём', 'Личные детали'],
  ] as const)('применяет приватность %s в ответе API, включая просмотр капитаном', async (privacy, title, description) => {
    const owner = await signIn('maria')
    await request(api.updatePrivacy(privacy))
    const created = await request(api.createScheduleEvent({ ...scheduleEvent, category: 'personal', ownerId: owner.id, projectId: 'ai-hackathon', title: 'Личный приём', description: 'Личные детали', location: 'Скрытый адрес' }))
    expect((await request(api.getSchedule('all'))).find((event) => event.id === created.id)?.description).toBe('Личные детали')
    await signIn('alexey', 'lead123')
    const result = (await request(api.getSchedule('all'))).find((event) => event.id === created.id)
    if (title === null) expect(result).toBeUndefined()
    else {
      expect(result).toMatchObject({ title, description })
      if (privacy !== 'details') expect(result?.location).toBeUndefined()
    }
  })

  it('возвращает пустое расписание проекта без событий', async () => {
    const project = await request(api.createProject({ name: 'Без событий', description: '', color: '#123456' }))
    expect(await request(api.getSchedule(project.id))).toEqual([])
  })

  it('капитан удаляет источник только указанного проекта', async () => {
    const before = await request(api.getProjects())
    const target = before.find((project) => project.id === 'ai-hackathon')!
    const other = before.find((project) => project.id === 'web-course')!
    const updated = await request(api.deleteProjectSource(target.id, target.sources[0].id))
    expect(updated.sources).toHaveLength(0)
    expect((await request(api.getProjects())).find((project) => project.id === other.id)?.sources).toEqual(other.sources)
  })

  it('участник может удалить собственный источник, но не чужой', async () => {
    await signIn('maria')
    await expect(request(api.deleteProjectSource('ai-hackathon', 'source-1'))).rejects.toThrow('капитан или его автор')
    const file = await request(api.uploadAttachment(new File(['source'], 'mine.txt')))
    const project = await request(api.addProjectSources({ projectId: 'ai-hackathon', author: 'Поддельный автор', files: [file] }))
    expect(project.sources.find((source) => source.id === file.id)).toMatchObject({ uploadedById: 2, uploadedBy: 'Мария Волкова' })
    const updated = await request(api.deleteProjectSource(project.id, file.id))
    expect(updated.sources.some((source) => source.id === file.id)).toBe(false)
    expect(updated.sources.some((source) => source.id === 'source-1')).toBe(true)
  })

  it('удаление отсутствующего источника возвращает ошибку и сохраняет проект', async () => {
    const before = await request(api.getProjects())
    await expect(request(api.deleteProjectSource('ai-hackathon', 'missing'))).rejects.toThrow('Источник не найден')
    expect(await request(api.getProjects())).toEqual(before)
  })
})
