export type TaskStatus = 'waiting' | 'working' | 'checking' | 'done'
export type UserRole = 'lead' | 'member'

export interface Attachment {
  id: string
  name: string
  size: number
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
  revisionComment?: string
}

export interface User {
  id: number
  name: string
  login: string
  role: UserRole
  position: string
}

export type NewTask = Pick<Task, 'taskName' | 'taskDesc' | 'taskDeadline' | 'attachments'>

export interface TaskStatusUpdate {
  taskId: number
  status: TaskStatus
  revisionComment?: string
  resultAttachments?: Attachment[]
}
