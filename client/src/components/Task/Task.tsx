import type { JSX } from "react";
import type React from "react";

type PropsType = {
    taskName: string
    taskDesc: string
    taskDeadline: string
}

const Task: React.FC<PropsType> = ({ taskName, taskDeadline, taskDesc }): JSX.Element => {
    return (
        <div className="task">
            <h4 className="task__name">{ taskName }</h4>
            <p className="task__desc">{ taskDesc }</p>
            <p className="task__deadline">{ taskDeadline }</p>
        </div>
    )
}

export default Task