import { mapLimited } from './parallel'
import type { AddProjectSourcesPayload, AddTaskCommentPayload, Attachment, EntityId, EventPrivacy, EventReminders, NewProject, NewScheduleEvent, NewTask, NewTeamMember, ScheduleEvent, ScheduleQuery, Task, TaskStatus, UpdateProjectPayload } from '../types'
import type { ColumnDto, EventDto, EventTypeDto, GroupDto, LessonsDto, NotificationDto, ProfileDto, ProjectDto, TaskDto } from './contracts'
import { backendHttp as http } from './backendApi'
import { readBackendSession } from './backendSession'
import { columnStatus, mapEvent, mapProfile, mapProject, mapTask, privacyToServer } from './mappers'
import type { EventTypeOption } from '../utils/eventTypes'

let projects: ProjectDto[] = []
let profiles: ProfileDto[] = []
let columns: ColumnDto[] = []
let contextFlight: Promise<void> | null = null
const staged = new Map<string, File>()
const uploadedTaskFiles = new Map<string, string>()
const pathId = (id: EntityId) => encodeURIComponent(String(id))
const iso = (value: string) => new Date(value).toISOString()
const adjacentDate = (value: string, days: number) => { const date = new Date(`${value}T00:00:00Z`); date.setUTCDate(date.getUTCDate() + days); return date.toISOString().slice(0, 10) }
const get = async <T>(path: string, params?: object): Promise<T> => (await http.get<T>(path, { params })).data
async function context() {
  if (contextFlight) return contextFlight
  contextFlight = (async () => {
    const [list, users] = await Promise.all([get<ProjectDto[] | null>('/projects'), get<ProfileDto[] | null>('/users')])
    projects = list ?? []; profiles = users ?? []
  })().finally(() => { contextFlight = null })
  return contextFlight
}
async function projectColumns(projectId: string) {
  const result = await get<ColumnDto[] | null>(`/projects/${pathId(projectId)}/columns`)
  columns = [...columns.filter(column => column.projectId !== projectId), ...(result ?? [])]
  const fixed = result ?? []
  const kinds: ColumnDto['kind'][] = ['BACKLOG', 'IN_PROGRESS', 'REVIEW', 'DONE']
  if (fixed.length !== 4 || kinds.some(kind => fixed.filter(column => column.kind === kind).length !== 1)) throw new Error('Сервер вернул некорректные колонки доски')
  return fixed
}
async function taskDetail(id: EntityId): Promise<Task> {
  const dto = await get<TaskDto>(`/tasks/${pathId(id)}`)
  await projectColumns(dto.projectId)
  return mapTask(dto, columns, profiles)
}
async function upload(path: string, file: File, kind?: string) {
  const form = new FormData(); form.append('file', file)
  if (kind) form.append('kind', kind)
  return http.post(path, form)
}
function stagedFile(file: Attachment): File {
  const content = staged.get(file.id)
  if (!content) throw new Error(`Выберите файл «${file.name}» повторно`)
  return content
}
async function attachTaskFiles(id: EntityId, files: Attachment[], kind: 'SOURCE' | 'RESULT') {
  for (const file of files.filter(item => item.id.startsWith('pending:'))) {
    const target = `${id}:${kind}`
    if (uploadedTaskFiles.get(file.id) === target) continue
    await upload(`/tasks/${pathId(id)}/attachments`, stagedFile(file), kind)
    uploadedTaskFiles.set(file.id, target)
    staged.delete(file.id)
  }
}
async function sourceUpload(projectId: string, files: Attachment[]) {
  for (const file of files) {
    if (file.url) await http.post(`/projects/${pathId(projectId)}/sources/links`, { title: file.name, url: file.url })
    else await upload(`/projects/${pathId(projectId)}/sources/files`, stagedFile(file))
    staged.delete(file.id)
  }
  const project = await get<ProjectDto>(`/projects/${pathId(projectId)}`)
  return mapProject(project, profiles)
}
const typeOption = (type: EventTypeDto): EventTypeOption => ({ id: type.id, label: type.title, color: type.color, builtIn: !type.ownerId, reminderMinutes: type.defaultReminders ?? [] })
function reminders(minutes: number[]) {
  if (minutes.some(value => !Number.isInteger(value) || value < 1 || value > 40320)) throw new Error('Укажите напоминания от 1 до 40320 минут или отключите их')
  if (new Set(minutes).size !== minutes.length) throw new Error('Интервалы напоминаний не должны повторяться')
  return minutes
}
export let scheduleSourceMessage = ''
let scheduleCache: ScheduleEvent[] = []
export const realApi = {
  async getCurrentUser() { const [profile] = await Promise.all([get<ProfileDto>('/me'), context()]); if (profile.id !== readBackendSession()?.user_id) throw new Error('Сервер вернул профиль другого пользователя'); return mapProfile(profile, projects) },
  async getProjects() { await context(); return [...projects].sort((a, b) => Number(b.canViewBoard) - Number(a.canViewBoard)).map(project => mapProject(project, profiles)) },
  async createProject(project: NewProject) {
    const creatorId = readBackendSession()?.user_id
    if (!creatorId) throw new Error('Войдите в аккаунт, чтобы создать проект')
    const dto = (await http.post<ProjectDto>('/projects', { name: project.name, description: project.description, members: [{ userId: creatorId, role: 'Тимлид' }] })).data
    try { return await sourceUpload(dto.id, project.sources ?? []) }
    catch (error) { throw new Error(`Проект «${dto.name}» создан, но файлы не загружены. Откройте его источники и повторите загрузку. ${error instanceof Error ? error.message : ''}`) }
  },
  async updateProject(project: UpdateProjectPayload) {
    const dto = (await http.patch<ProjectDto>(`/projects/${pathId(project.id)}`, { name: project.name, description: project.description, members: project.members.map(member => ({ ...member, userId: String(member.userId) })) })).data
    await context()
    return { project: mapProject(dto, profiles), members: profiles.map(profile => mapProfile(profile, projects)) }
  },
  async addProjectSources(payload: AddProjectSourcesPayload) { return sourceUpload(payload.projectId, payload.files) },
  async deleteProjectSource(projectId: string, sourceId: string) { await http.delete(`/projects/${pathId(projectId)}/sources/${pathId(sourceId)}`); return mapProject(await get<ProjectDto>(`/projects/${pathId(projectId)}`), profiles) },
  async getTeam() { const [settings] = await Promise.all([get<{ name: string; description: string }>('/team'), context()]); return { settings, members: profiles.map(profile => mapProfile(profile, projects)) } },
  async updateTeam(): Promise<never> { throw new Error('Сервер пока не поддерживает изменение названия и описания команды') },
  async createMember(member: NewTeamMember) {
    const dto = (await http.post<ProfileDto>('/users', { fullName: member.name, username: member.login, temporaryPassword: member.password, studentGroup: member.studentGroup ?? '', role: member.role === 'lead' ? 'CAPTAIN' : 'MEMBER' })).data
    profiles = [...profiles.filter(profile => profile.id !== dto.id), dto]
    return mapProfile(dto, projects)
  },
  async toggleMember(id: EntityId, active: boolean) { return mapProfile((await http.patch<ProfileDto>(`/users/${pathId(id)}/status`, { status: active ? 'ACTIVE' : 'BLOCKED' })).data, projects) },
  async resetPassword(id: EntityId) { return { password: (await http.post<{ temporaryPassword: string }>(`/users/${pathId(id)}/reset-password`)).data.temporaryPassword } },
  async changePassword(payload: { currentPassword: string; newPassword: string }) { await http.post('/auth/change-password', payload); return { success: true } },
  async updatePrivacy(privacy: EventPrivacy) { return mapProfile((await http.patch<ProfileDto>('/me', { schedulePrivacy: privacyToServer[privacy] })).data, projects) },
  async updateAvatar(file?: File) {
    const dto = file ? (await http.put<ProfileDto>('/me/photo', (() => { const form = new FormData(); form.append('file', file); return form })())).data : (await http.delete<ProfileDto>('/me/photo')).data
    const profile = mapProfile(dto, projects)
    if (profile.avatarUrl) profile.avatarUrl += `?v=${Date.now()}`
    return profile
  },
  async getTasks() {
    await context()
    const accessible = projects.filter(project => project.canViewBoard)
    const lists = await mapLimited(accessible, async project => {
      await projectColumns(project.id)
      return await get<TaskDto[] | null>(`/projects/${pathId(project.id)}/tasks`) ?? []
    })
    const details = await mapLimited(lists.flat(), task => get<TaskDto>(`/tasks/${pathId(task.id)}`))
    return details.map(task => mapTask(task, columns, profiles))
  },
  async getMyTasks() {
    await context()
    const tasks = await get<TaskDto[] | null>('/tasks') ?? []
    await mapLimited([...new Set(tasks.map(task => task.projectId))], projectColumns)
    return tasks.map(task => mapTask(task, columns, profiles))
  },
  async getTask(id: EntityId) { await context(); return taskDetail(id) },
  async createTask(task: NewTask) {
    const dto = (await http.post<TaskDto>(`/projects/${pathId(task.projectId)}/tasks`, { title: task.taskName, description: task.taskDesc, priority: 'NORMAL', assigneeId: task.assigneeId === undefined ? null : String(task.assigneeId), dueAt: task.taskDeadline ? iso(`${task.taskDeadline}T23:59:00`) : null })).data
    try { await attachTaskFiles(dto.id, task.attachments, 'SOURCE') }
    catch (error) { throw new Error(`Задача создана (${dto.id}), но файлы не загружены. ${error instanceof Error ? error.message : ''}`) }
    return taskDetail(dto.id)
  },
  async assignTask(id: EntityId, assigneeId: EntityId | null) { await http.patch(`/tasks/${pathId(id)}`, { assigneeId: assigneeId === null ? null : String(assigneeId) }); return taskDetail(id) },
  async updateTaskStatus(id: EntityId, status: TaskStatus, _author: string, comment?: string, files?: Attachment[], _assignee?: string) {
    const current = await get<TaskDto>(`/tasks/${pathId(id)}`)
    const project = await projectColumns(current.projectId)
    const previous = columnStatus(current.columnId, project)
    if (previous === 'waiting' && status === 'working') {
      if (current.assigneeId && current.assigneeId !== readBackendSession()?.user_id) await http.post(`/tasks/${pathId(id)}/assign-to-me`)
      else if (!current.assigneeId) await http.post(`/tasks/${pathId(id)}/take`)
    }
    if (files?.length) await attachTaskFiles(id, files, 'RESULT')
    const refreshed = await get<TaskDto>(`/tasks/${pathId(id)}`)
    if (columnStatus(refreshed.columnId, project) !== status) {
      const kind: Record<TaskStatus, ColumnDto['kind']> = { waiting: 'BACKLOG', working: 'IN_PROGRESS', checking: 'REVIEW', done: 'DONE' }
      const target = project.find(column => column.kind === kind[status])
      if (!target) throw new Error('В проекте нет нужной колонки')
      await http.post(`/tasks/${pathId(id)}/move`, { columnId: target.id, ...(comment ? { comment } : {}) })
    }
    return taskDetail(id)
  },
  async addTaskComment(payload: AddTaskCommentPayload) { await http.post(`/tasks/${pathId(payload.taskId)}/comments`, { text: payload.text }); return taskDetail(payload.taskId) },
  async uploadAttachment(file: File): Promise<Attachment> {
    if (file.size > 10 * 1024 * 1024) throw new Error('Максимальный размер файла — 10 МБ')
    const id = `pending:${crypto.randomUUID()}`; staged.set(id, file)
    return { id, name: file.name, size: file.size }
  },
  async downloadAttachment(id: string): Promise<Blob> { if (id.startsWith('pending:')) return stagedFile({ id, name: '', size: 0 }); return (await http.get<Blob>(`/files/${pathId(id)}`, { responseType: 'blob' })).data },
  async login(): Promise<never> { throw new Error('Используйте серверную авторизацию') },
  async getEventTypes() { return (await get<EventTypeDto[] | null>('/event-types') ?? []).map(typeOption) },
  async saveEventType(type: EventTypeOption, create: boolean) {
    const body = { title: type.label, color: type.color, defaultReminders: reminders(type.reminderMinutes ?? []) }
    return typeOption((create ? await http.post<EventTypeDto>('/event-types', body) : await http.patch<EventTypeDto>(`/event-types/${pathId(type.id)}`, body)).data)
  },
  async deleteEventType(id: string) { await http.delete(`/event-types/${pathId(id)}`) },
  async getGroups() { return await get<GroupDto[] | null>('/student-groups') ?? [] },
  async getSchedule(_projectId: string, query?: ScheduleQuery): Promise<ScheduleEvent[]> {
    const now = new Date(); const from = query?.from ?? new Date(now.getFullYear(), now.getMonth(), 1).toISOString().slice(0, 10); const to = query?.to ?? new Date(now.getFullYear(), now.getMonth() + 2, 0).toISOString().slice(0, 10)
    if (!profiles.length) await context()
    const groupView = query?.groups !== undefined
    const requestedGroups = query?.groups ?? []
    const groupPeople = requestedGroups.map(group => profiles.find(profile => profile.active && profile.studentGroup === group)?.id).filter((id): id is string => !!id)
    const people = groupView ? [...new Set(groupPeople)] : [query?.personId]
    // The API date range is interpreted in UTC; include boundary days for local-time rendering.
    const [eventLists, types, overrides] = await Promise.all([mapLimited(people.length ? people : [query?.personId], personId => get<EventDto[] | null>('/events', { from: adjacentDate(from, -1), to: adjacentDate(to, 1), personId })), get<EventTypeDto[] | null>('/event-types'), get<Record<string, number[]> | null>('/me/reminders')])
    const events = [...new Map(eventLists.flatMap(list => list ?? []).filter(event => !groupView || event.visibility === 'TEAM' || (event.visibility === 'GROUP' && !!event.group && requestedGroups.includes(event.group))).map(event => [event.id, event])).values()]
    const owner = query?.personId ? profiles.find(profile => profile.id === query.personId) : profiles.find(profile => profile.id === readBackendSession()?.user_id)
    const groups = query?.groups?.length ? query.groups : owner?.studentGroup ? [owner.studentGroup] : []
    const lessons = await Promise.all(groups.map(group => get<LessonsDto>('/schedule/lessons', { group, from, to }).catch((): LessonsDto => ({ lessons: [], updatedAt: null, source: 'UNAVAILABLE' }))))
    scheduleSourceMessage = lessons.some(result => result.source === 'UNAVAILABLE') ? 'Источник учебного расписания недоступен. События команды показаны отдельно.' : lessons.some(result => result.source === 'CACHED') ? 'Учебное расписание загружено из кеша.' : ''
    if (groupView && groupPeople.length < requestedGroups.length) scheduleSourceMessage += `${scheduleSourceMessage ? ' ' : ''}События групп без активных участников недоступны; пары показаны по выбранным группам.`
    const mapped = [...(events ?? []).map(event => mapEvent(event, types ?? [], profiles, overrides ?? {})), ...lessons.flatMap(result => (result.lessons ?? []).map(lesson => ({ id: `lesson:${lesson.id}`, projectId: 'all', title: lesson.title, description: lesson.teacher, location: lesson.room, startsAt: `${lesson.date}T${lesson.start}`, endsAt: `${lesson.date}T${lesson.end}`, type: 'class', typeLabel: lesson.kind, typeColor: '#42a682', category: 'class' as const, group: lesson.group, author: lesson.teacher, reminder: 'none' as const, reminderMode: 'custom' as const, reminderMinutes: overrides?.[`lesson:${lesson.id}`] ?? [] })))]
    scheduleCache = mapped
    return mapped
  },
  async getScheduleEventDate(id: string) { return (await get<EventDto>(`/events/${pathId(id)}`)).startsAt },
  async getScheduleEventTarget(id: string) { return get<EventDto>(`/events/${pathId(id)}`) },
  async getLessonDate(id: string, notificationAt: string) {
    const cached = scheduleCache.find(event => event.id === `lesson:${id}`)
    if (cached) return cached.startsAt
    // Notifications contain the delivery time, which may precede the lesson by up to 28 days.
    if (!profiles.length) await context()
    const group = profiles.find(profile => profile.id === readBackendSession()?.user_id)?.studentGroup
    if (!group) throw new Error('Укажите учебную группу, чтобы открыть занятие')
    const start = new Date(notificationAt); start.setDate(start.getDate() - 1)
    const end = new Date(notificationAt); end.setDate(end.getDate() + 30)
    const result = await get<LessonsDto>('/schedule/lessons', { group, from: start.toISOString().slice(0, 10), to: end.toISOString().slice(0, 10) })
    const lesson = result.lessons?.find(lesson => lesson.id === id)
    if (!lesson) throw new Error('Занятие не найдено в текущем расписании')
    return `${lesson.date}T${lesson.start}`
  },
  async createScheduleEvent(event: NewScheduleEvent) {
    const dto = (await http.post<EventDto>('/events', { title: event.title, description: event.description, location: event.location ?? '', startsAt: iso(event.startsAt), endsAt: event.endsAt ? iso(event.endsAt) : null, visibility: event.category === 'personal' ? 'PERSONAL' : event.category === 'group' ? 'GROUP' : 'TEAM', group: event.category === 'group' ? event.group : '', typeId: event.type || null, ...(event.reminderMode === 'custom' ? { myReminders: reminders(event.reminderMinutes ?? []) } : {}) })).data
    if (event.reminderMode === 'custom' && event.reminderMinutes?.length) await http.put(`/me/reminders/${pathId(`event:${dto.id}`)}`, { minutes: event.reminderMinutes })
    const [types, overrides] = await Promise.all([get<EventTypeDto[]>('/event-types'), get<Record<string, number[]>>('/me/reminders')])
    return mapEvent(dto, types ?? [], profiles, overrides ?? {})
  },
  async updateEventReminders(id: EntityId, value: EventReminders) {
    const key = String(id).startsWith('lesson:') ? String(id) : `event:${id}`
    await http.put(`/me/reminders/${pathId(key)}`, { minutes: value.reminderMode === 'type' ? [] : reminders(value.reminderMinutes) })
    if (key.startsWith('lesson:')) { const lesson = scheduleCache.find(item => String(item.id) === key); if (!lesson) throw new Error('Обновите расписание'); return { ...lesson, reminderMode: value.reminderMode, reminderMinutes: value.reminderMinutes } }
    const [event, types, overrides] = await Promise.all([get<EventDto>(`/events/${pathId(id)}`), get<EventTypeDto[]>('/event-types'), get<Record<string, number[]>>('/me/reminders')])
    return mapEvent(event, types ?? [], profiles, overrides ?? {})
  },
  async notifications() { return await get<NotificationDto[] | null>('/notifications', { limit: 100 }) ?? [] },
  async readNotifications() { await http.post('/notifications/read-all') },
  clear() { projects = []; profiles = []; columns = []; scheduleCache = []; scheduleSourceMessage = ''; staged.clear(); uploadedTaskFiles.clear() },
}
