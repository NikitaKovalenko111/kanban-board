import type { Attachment, EventPrivacy, Project, ScheduleEvent, Task, TaskStatus, TeamMember } from '../types'
import type { ColumnDto, EventDto, EventTypeDto, FileDto, ProfileDto, ProjectDto, TaskDto } from './contracts'

export const privacyToServer: Record<EventPrivacy, ProfileDto['schedulePrivacy']> = { hidden: 'NOTHING', busy: 'BUSY', title: 'TITLE', details: 'TITLE_AND_DESCRIPTION' }
const statusByKind: Record<ColumnDto['kind'], TaskStatus> = { BACKLOG: 'waiting', IN_PROGRESS: 'working', REVIEW: 'checking', DONE: 'done' }
export function mapProfile(dto: ProfileDto, projects: ProjectDto[] = []): TeamMember {
  if (!dto.id || !['CAPTAIN', 'MEMBER'].includes(dto.role)) throw new Error('Сервер вернул некорректный профиль')
  return { id: dto.id, name: dto.fullName || dto.username, login: dto.username, role: dto.role === 'CAPTAIN' ? 'lead' : 'member', position: '', studentGroup: dto.studentGroup, projectRoles: Object.fromEntries(projects.flatMap(project => (project.members ?? []).filter(member => member.userId === dto.id).map(member => [project.id, member.role]))), avatarUrl: dto.imageUrl ?? undefined, eventPrivacy: (Object.keys(privacyToServer) as EventPrivacy[]).find(key => privacyToServer[key] === dto.schedulePrivacy) ?? 'busy', active: dto.active, joinedAt: dto.joinedAt }
}
export const mapFile = (file: FileDto): Attachment => ({ id: file.id, name: file.name, size: file.sizeBytes })
export const personName = (id: string | null | undefined, profiles: ProfileDto[]) => id ? profiles.find(profile => profile.id === id)?.fullName || profiles.find(profile => profile.id === id)?.username || 'Участник' : 'Не назначен'
export function mapProject(dto: ProjectDto, profiles: ProfileDto[] = []): Project {
  const palette = ['#6577df', '#8b72e8', '#4e8bd8', '#42a682', '#e58a58', '#d75f82']
  const hash = [...dto.id].reduce((sum, character) => sum + character.charCodeAt(0), 0)
  return { id: dto.id, name: dto.name, canViewBoard: dto.canViewBoard, description: dto.description ?? '', color: dto.color || palette[hash % palette.length], sources: (dto.sources ?? []).map(source => ({ ...(source.file ? mapFile(source.file) : { id: source.id, name: source.title, size: 0 }), id: source.id, downloadId: source.file?.id, url: source.kind === 'LINK' ? source.url ?? undefined : undefined, uploadedAt: source.addedAt, uploadedBy: personName(source.addedBy, profiles), uploadedById: source.addedBy ?? undefined })) }
}
export function columnStatus(id: string, columns: ColumnDto[]): TaskStatus {
  const column = columns.find(item => item.id === id)
  if (!column) throw new Error('Колонка задачи не найдена. Обновите доску')
  return statusByKind[column.kind]
}
export function mapTask(dto: TaskDto, columns: ColumnDto[], profiles: ProfileDto[]): Task {
  const history = dto.history ?? []
  const historyColumn = (value?: string) => columns.find(column => column.projectId === dto.projectId && (column.id === value || column.title === value))
  const historyStatus = (value?: string) => { const column = historyColumn(value); return column ? statusByKind[column.kind] : columnStatus(dto.columnId, columns) }
  const isRevision = (item: NonNullable<TaskDto['history']>[number]) => item.action === 'RETURNED' || (!!item.comment && !!item.fromColumn && ['checking', 'done'].includes(historyStatus(item.fromColumn)) && !!item.toColumn && historyStatus(item.toColumn) === 'working')
  const revision = [...history].reverse().find(isRevision)
  const files = (kind: 'SOURCE' | 'RESULT') => (dto.attachments ?? []).filter(file => file.kind === kind).map(file => (file.contentKind === 'LINK' ? { id: file.id, name: file.title || file.url || 'Ссылка', size: 0, url: file.url ?? undefined } : file.file ? { ...mapFile(file.file), id: file.id, downloadId: file.file.id } : { id: file.id, name: file.title || 'Файл недоступен', size: 0 }))
  return { taskId: dto.id, projectId: dto.projectId, taskName: dto.title, taskDesc: dto.description, taskStatus: columnStatus(dto.columnId, columns), priority: dto.priority === 'LOW' ? 'low' : dto.priority === 'HIGH' ? 'high' : 'medium', taskDeadline: dto.dueAt ?? undefined, createdAt: dto.createdAt, completedAt: dto.completedAt ?? undefined, assigneeId: dto.assigneeId ?? undefined, assignee: personName(dto.assigneeId, profiles), attachments: files('SOURCE'), resultAttachments: files('RESULT'), revisionComment: columnStatus(dto.columnId, columns) === 'working' ? revision?.comment : undefined, comments: (dto.comments ?? []).map(comment => ({ id: comment.id, author: personName(comment.authorId, profiles), text: comment.text, createdAt: comment.createdAt })), history: history.map(item => ({ id: item.id, type: item.action === 'CREATED' ? 'created' : item.action.includes('ASSIGN') ? 'assignment' : isRevision(item) ? 'revision' : 'status', author: personName(item.actorId, profiles), createdAt: item.at, fromStatus: historyStatus(item.fromColumn), toStatus: historyStatus(item.toColumn), fromLabel: historyColumn(item.fromColumn)?.title ?? item.fromColumn, toLabel: historyColumn(item.toColumn)?.title ?? item.toColumn, comment: item.comment })) }
}
export function mapEvent(dto: EventDto, types: EventTypeDto[], profiles: ProfileDto[], reminders: Record<string, number[]>): ScheduleEvent {
  const type = types.find(item => item.id === dto.typeId)
  const override = reminders[`event:${dto.id}`]
  return { id: dto.id, projectId: dto.projectId ?? undefined, title: dto.title, description: dto.description ?? '', location: dto.location, startsAt: dto.startsAt, endsAt: dto.endsAt ?? undefined, type: dto.typeId ?? 'event', typeLabel: type?.title ?? 'Событие', typeColor: type?.color ?? '#6577df', category: dto.visibility === 'PERSONAL' ? 'personal' : dto.visibility === 'GROUP' ? 'group' : 'team', group: dto.group ?? undefined, ownerId: dto.ownerId, author: personName(dto.ownerId, profiles), reminder: 'none', reminderMode: 'custom', reminderMinutes: override ?? [], masked: dto.masked, partial: dto.partial }
}
