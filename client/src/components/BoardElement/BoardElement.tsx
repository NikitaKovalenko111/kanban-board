import type { JSX } from "react";
import type React from "react";

type PropsType = {
    name: string
}

const BoardElement: React.FC<PropsType> = ({ name }): JSX.Element => {
    return (
        <div className="board-element">
            <div className="board-element__top">
                <h3 className="board-element__header">{ name }</h3>
                <div className="board-element__plus-btn">+</div>
            </div>
            <div className="board-element__tasks">
                
            </div>
        </div>
    )
}

export default BoardElement