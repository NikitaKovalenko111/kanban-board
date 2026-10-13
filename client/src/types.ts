export type TaskStatus = 'waiting' | 'working' | 'checking' | 'done'
export type UserRole = 'lead' | 'member'

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

export type NewTask = Pick<Task, 'taskName' | 'taskDesc' | 'taskDeadline' | 'attachments'>

export interface TaskStatusUpdate {
  taskId: number
  status: TaskStatus
  revisionComment?: string
  resultAttachments?: Attachment[]
  author: string
}
