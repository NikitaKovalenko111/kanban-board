import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import type { mockApi as Api } from './mockApi'

let api: typeof Api
// Новый модуль для каждого теста изолирует изменяемые данные mock API.
beforeEach(async () => {
  vi.resetModules()
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2026-10-22T10:00:00Z'))
  api = (await import('./mockApi')).mockApi
})
afterEach(() => vi.useRealTimers())
async function request<T>(promise: Promise<T>): Promise<T> {
  const result = promise.then(value => ({ value }), error => ({ error }))
  await vi.runAllTimersAsync()
  const settled = await result
  if ('error' in settled) throw settled.error
  return settled.value
}

describe('mockApi', () => {
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
    const files = [{ id: 'result', name: 'result.zip', size: 100 }]
    const done = await request(api.updateTaskStatus(created.taskId, 'done', 'Тимлид', undefined, files))
    expect(done.completedAt).toBe(new Date().toISOString())
    expect(done.resultAttachments).toEqual(files)
    expect(done.history).toEqual([expect.objectContaining({ fromStatus: 'waiting', toStatus: 'done', type: 'status', author: 'Тимлид' })])
    const revised = await request(api.updateTaskStatus(created.taskId, 'working', 'Тимлид', 'Исправить', []))
    expect(revised).toMatchObject({ taskStatus: 'working', revisionComment: 'Исправить', resultAttachments: [] })
    expect(revised.completedAt).toBeUndefined()
    expect(revised.history).toHaveLength(2)
    expect(revised.history[1]).toMatchObject({ type: 'revision', comment: 'Исправить', fromStatus: 'done', toStatus: 'working' })
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
    const event = await request(api.createScheduleEvent({ projectId: project.id, title: 'Встреча', description: '', startsAt: '2026-10-23T10:00:00', type: 'meeting', author: 'Тимлид' }))
    expect(await request(api.getSchedule(project.id))).toEqual([event])
    expect(await request(api.getSchedule('all'))).toContainEqual(event)
  })

  it('создаёт участника и запрещает повторный нормализованный логин', async () => {
    const member = { name: 'Участник', login: ' NEW ', password: 'secret', role: 'member' as const, position: 'QA' }
    expect(await request(api.createMember(member))).toMatchObject({ login: 'new', active: true })
    await expect(request(api.createMember({ ...member, login: 'new' }))).rejects.toThrow('Такой логин уже занят')
    expect((await request(api.getTeam())).members.every(member => !('password' in member))).toBe(true)
  })
})
