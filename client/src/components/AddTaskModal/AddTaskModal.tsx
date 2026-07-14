import type { JSX } from "react";
import type React from "react";

type PropsType = {}

const AddTaskModal: React.FC<PropsType> = (): JSX.Element => {
    return (
        <div className="add-task-modal">
            <div className="add-task-modal__wrapper">
                <h2 className="add-task-modal__header">Добавить задачу</h2>
                <form className="add-task-modal__form" method="post">
                    <label>
                        Название задачи
                        <input type="text" name="task-name" placeholder="Название" className="add-task-modal__name-input" />
                    </label>
                    <label>
                        Описание задачи
                        <textarea name="task-name" placeholder="Описание" className="add-task-modal__desc-input" />
                    </label>
                    <label>
                        Дедлайн
                        <input type="date" name="task-name" className="add-task-modal__date-input" />
                    </label>
                    <button type="submit" className="add-task-modal__submit-btn">Добавить</button>
                </form>
            </div>
        </div>
    )
}

export default AddTaskModal