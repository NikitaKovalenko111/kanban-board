export type TaskStatus = 'waiting' | 'working' | 'checking' | 'done'
export type UserRole = 'lead' | 'member'
export type ScheduleEventType = 'meeting' | 'milestone' | 'class' | 'presentation'

export interface Project {
  id: string
  name: string
  description: string
  color: string
  sources: ProjectSource[]
}

export interface ProjectSource extends Attachment {
  uploadedAt: string
  uploadedBy: string
}

export type NewProject = Omit<Project, 'id' | 'sources'>

export interface AddProjectSourcesPayload {
  projectId: string
  files: Attachment[]
  author: string
}

export interface ScheduleEvent {
  id: number
  projectId: string
  title: string
  description: string
  startsAt: string
  type: ScheduleEventType
  location?: string
  author: string
}

export type NewScheduleEvent = Omit<ScheduleEvent, 'id'>

export interface Attachment {
  id: string
  name: string
  size: number
}

export interface TaskHistoryEvent {
  id: string
  type: 'status' | 'revision'
  fromStatus: TaskStatus
  toStatus: TaskStatus
  author: string
  createdAt: string
  comment?: string
}

export interface Task {
  taskId: number
  projectId: string
  taskName: string
  taskDesc: string
  taskStatus: TaskStatus
  taskDeadline?: string
  attachments: Attachment[]
  resultAttachments: Attachment[]
  assignee: string
  createdAt: string
  completedAt?: string
  revisionComment?: string
  history: TaskHistoryEvent[]
}

export interface User {
  id: number
  name: string
  login: string
  role: UserRole
  position: string
  avatarUrl?: string
}

export interface LoginCredentials {
  login: string
  password: string
}

export interface TeamSettings {
  name: string
  description: string
}

export interface TeamMember extends User {
  active: boolean
  joinedAt: string
}

export interface NewTeamMember {
  name: string
  login: string
  password: string
  position: string
  role: UserRole
}

export type NewTask = Pick<Task, 'projectId' | 'taskName' | 'taskDesc' | 'taskDeadline' | 'attachments'>

export interface TaskStatusUpdate {
  taskId: number
  status: TaskStatus
  revisionComment?: string
  resultAttachments?: Attachment[]
  author: string
}
