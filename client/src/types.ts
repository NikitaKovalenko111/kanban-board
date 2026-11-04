export type TaskStatus = 'waiting' | 'working' | 'checking' | 'done'
export type UserRole = 'lead' | 'member'
export type ScheduleEventType = string
export type StudyGroup = '203' | '204' | 'both'
export type ReminderScenario = 'none' | 'at-start' | '15-min' | '1-hour' | '1-day'
export interface EventReminders { reminderMode: 'type' | 'custom'; reminderMinutes: number[] }
export type EventPrivacy = 'hidden' | 'busy' | 'title' | 'details'
export type EventCategory = 'class' | 'team' | 'group' | 'personal' | 'deadline'

export interface Project {
  id: string
  name: string
  description: string
  color: string
  sources: ProjectSource[]
  teamName?: string
}

export interface ProjectSource extends Attachment {
  uploadedAt: string
  uploadedBy: string
  uploadedById?: number
}

export type NewProject = Omit<Project, 'id' | 'sources'> & { sources?: Attachment[] }

export interface UpdateProjectPayload extends NewProject {
  id: string
  members: { userId: number; role: string }[]
}

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
  endsAt?: string
  type: ScheduleEventType
  typeLabel: string
  typeColor: string
  group?: StudyGroup
  reminder: ReminderScenario
  reminderMode?: EventReminders['reminderMode']
  reminderMinutes?: number[]
  location?: string
  author: string
  ownerId?: number
  category?: EventCategory
}

export type NewScheduleEvent = Omit<ScheduleEvent, 'id'>

export interface Attachment {
  id: string
  name: string
  size: number
}

export interface TaskComment {
  id: string
  author: string
  text: string
  createdAt: string
}

export interface TaskHistoryEvent {
  id: string
  type: 'status' | 'revision' | 'assignment'
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
  assigneeId?: number
  createdAt: string
  completedAt?: string
  revisionComment?: string
  history: TaskHistoryEvent[]
  comments: TaskComment[]
}

export interface User {
  id: number
  name: string
  login: string
  role: UserRole
  position: string
  projectRoles: Record<string, string>
  avatarUrl?: string
  eventPrivacy?: EventPrivacy
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
  projectRoles: Record<string, string>
}

export type NewTask = Pick<Task, 'projectId' | 'taskName' | 'taskDesc' | 'taskDeadline' | 'attachments'> & { assigneeId?: number }

export interface TaskAssignment {
  taskId: number
  assigneeId: number | null
}

export interface TaskStatusUpdate {
  taskId: number
  status: TaskStatus
  revisionComment?: string
  resultAttachments?: Attachment[]
  author: string
  assignee?: string
}

export interface AddTaskCommentPayload {
  taskId: number
  author: string
  text: string
}
