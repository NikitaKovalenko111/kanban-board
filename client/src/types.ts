export type EntityId = string | number
export type TaskStatus = 'waiting' | 'working' | 'checking' | 'done'
export type UserRole = 'lead' | 'member'
export type ScheduleEventType = string
export type StudyGroup = string
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
  uploadedById?: EntityId
}

export type NewProject = Omit<Project, 'id' | 'sources'> & { sources?: Attachment[] }

export interface UpdateProjectPayload extends NewProject {
  id: string
  members: { userId: EntityId; role: string }[]
}

export interface AddProjectSourcesPayload {
  projectId: string
  files: Attachment[]
  author: string
}

export interface ScheduleEvent {
  id: EntityId
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
  ownerId?: EntityId
  category?: EventCategory
  masked?: boolean
}

export type NewScheduleEvent = Omit<ScheduleEvent, 'id'>

export interface Attachment {
  id: string
  name: string
  size: number
  url?: string
  downloadId?: string
}

export interface TaskComment {
  id: string
  author: string
  text: string
  createdAt: string
}

export interface TaskHistoryEvent {
  id: string
  type: 'status' | 'revision' | 'assignment' | 'created'
  fromStatus: TaskStatus
  toStatus: TaskStatus
  fromLabel?: string
  toLabel?: string
  author: string
  createdAt: string
  comment?: string
}

export interface Task {
  taskId: EntityId
  projectId: string
  taskName: string
  taskDesc: string
  taskStatus: TaskStatus
  taskDeadline?: string
  attachments: Attachment[]
  resultAttachments: Attachment[]
  assignee: string
  assigneeId?: EntityId
  createdAt: string
  completedAt?: string
  revisionComment?: string
  history: TaskHistoryEvent[]
  comments: TaskComment[]
}

export interface User {
  id: EntityId
  name: string
  login: string
  role: UserRole
  position: string
  projectRoles: Record<string, string>
  avatarUrl?: string
  studentGroup?: string
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
  studentGroup?: string
  name: string
  login: string
  password: string
  position: string
  role: UserRole
  projectRoles: Record<string, string>
}

export type NewTask = Pick<Task, 'projectId' | 'taskName' | 'taskDesc' | 'taskDeadline' | 'attachments'> & { assigneeId?: EntityId }

export interface TaskAssignment {
  taskId: EntityId
  assigneeId: EntityId | null
}

export interface TaskStatusUpdate {
  taskId: EntityId
  status: TaskStatus
  revisionComment?: string
  resultAttachments?: Attachment[]
  author: string
  assignee?: string
}

export interface AddTaskCommentPayload {
  taskId: EntityId
  author: string
  text: string
}
export interface ScheduleQuery { projectId: string; personId?: EntityId; groups?: string[]; from: string; to: string }
