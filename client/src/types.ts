export interface Task {
    taskId: number
    taskName: string
    taskDesc: string
    taskStatus: TaskStatus
    taskDeadline: string
}

type TaskStatus = 'waiting' | 'working' | 'checking' | 'done'