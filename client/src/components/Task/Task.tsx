import type { JSX } from 'react'
import type React from 'react'
import type { Task as TaskType } from '../../types'

type PropsType = { task: TaskType; onClick: () => void }

const Task: React.FC<PropsType> = ({ task, onClick }): JSX.Element => {
    const overdue = task.taskDeadline && new Date(task.taskDeadline) < new Date() && task.taskStatus !== 'done'
    return (
        <button className="task" onClick={onClick}>
            <div className="task__meta"><span>#{task.taskId}</span>{task.attachments.length > 0 && <span>⌕ {task.attachments.length}</span>}</div>
            <h4 className="task__name">{task.taskName}</h4>
            <p className="task__desc">{task.taskDesc}</p>
            {task.revisionComment && <span className="task__revision">↩ На доработке</span>}
            <div className="task__footer">
              <span className="task__assignee" title={task.assignee}>{task.assignee.split(' ').map((part) => part[0]).join('')}</span>
              {task.taskDeadline && <span className={`task__deadline ${overdue ? 'is-overdue' : ''}`}>◷ {new Date(task.taskDeadline).toLocaleDateString('ru-RU', { day: '2-digit', month: 'short' })}</span>}
            </div>
        </button>
    )
}

export default Task
