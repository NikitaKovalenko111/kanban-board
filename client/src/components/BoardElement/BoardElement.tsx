import type { JSX } from 'react'
import type React from 'react'
import type { Task } from '../../types'
import TaskComponent from '../Task/Task'

type PropsType = {
    name: string
    accent: string
    tasks: Task[]
    onTaskClick: (task: Task) => void
    showProject?: boolean
    onHistory?: () => void
}

const BoardElement: React.FC<PropsType> = ({ name, accent, tasks, onTaskClick, showProject, onHistory }): JSX.Element => {
    return (
        <div className="board-element">
            <div className="board-element__top">
                <h3 className="board-element__header"><i style={{ background: accent }} />{name}<span>{tasks.length}</span></h3>
                {onHistory && <button className="board-element__history" onClick={onHistory}>Вся история →</button>}
            </div>
            <div className="board-element__tasks">
                {tasks.map((task) => <TaskComponent key={task.taskId} task={task} onClick={() => onTaskClick(task)} showProject={showProject} />)}
                {tasks.length === 0 && <div className="board-element__empty">Здесь пока пусто</div>}
            </div>
        </div>
    )
}

export default BoardElement
