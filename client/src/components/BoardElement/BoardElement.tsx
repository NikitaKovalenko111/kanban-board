import { useState, type JSX } from "react";
import type React from "react";
import type { Task } from "../../types";
import TaskComponent from "../Task/Task";
import AddTaskModal from "../AddTaskModal/AddTaskModal";

type PropsType = {
    name: string
}

const BoardElement: React.FC<PropsType> = ({ name }): JSX.Element => {
    const [showAddModal, setShowAddModal] = useState<boolean>(true)

    const tasks: Task[] = [
        {
            taskId: 1,
            taskName: "Тест 1",
            taskStatus: "waiting",
            taskDeadline: "08.14.2026",
            taskDesc: "Тестовое описание 1"
        },
        {
            taskId: 2,
            taskName: "Тест 2",
            taskStatus: "waiting",
            taskDeadline: "09.14.2026",
            taskDesc: "Тестовое описание 2"
        },
        {
            taskId: 2,
            taskName: "Тест 2",
            taskStatus: "waiting",
            taskDeadline: "09.14.2026",
            taskDesc: "Тестовое описание 2"
        },
        {
            taskId: 2,
            taskName: "Тест 2",
            taskStatus: "waiting",
            taskDeadline: "09.14.2026",
            taskDesc: "Тестовое описание 2"
        },
        {
            taskId: 2,
            taskName: "Тест 2",
            taskStatus: "waiting",
            taskDeadline: "09.14.2026",
            taskDesc: "Тестовое описание 2"
        },
        {
            taskId: 2,
            taskName: "Тест 2",
            taskStatus: "waiting",
            taskDeadline: "09.14.2026",
            taskDesc: "Тестовое описание 2"
        },
        {
            taskId: 2,
            taskName: "Тест 2",
            taskStatus: "waiting",
            taskDeadline: "09.14.2026",
            taskDesc: "Тестовое описание 2"
        }
    ]

    return (
        <div className="board-element">
            {
                showAddModal && <AddTaskModal />
            }
            <div className="board-element__top">
                <h3 className="board-element__header">{ name }</h3>
                <div className="board-element__plus-btn">+</div>
            </div>
            <div className="board-element__tasks">
                {
                    tasks.map(t => {
                        return <TaskComponent key={t.taskId} taskName={t.taskName} taskDesc={t.taskDesc} taskDeadline={t.taskDeadline} />
                    })
                }
            </div>
        </div>
    )
}

export default BoardElement