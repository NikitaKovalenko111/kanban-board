import type { JSX } from "react"
import type React from "react"
import BoardElement from "../../components/BoardElement/BoardElement"

type PropsType = {}

const BoardPage: React.FC<PropsType> = (): JSX.Element => {
    return (
        <main className="board-page">
            <div className="container board-page__container">
                <div className="board-page__tables">
                    <BoardElement name="Ожидание" />
                    <BoardElement name="В работе" />
                    <BoardElement name="На проверке" />
                    <BoardElement name="Выполнено" />
                </div>
            </div>
        </main>
    )
}

export default BoardPage