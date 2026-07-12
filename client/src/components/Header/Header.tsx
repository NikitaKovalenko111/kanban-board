import type { JSX } from "react"
import type React from "react"

type PropsType = {}

const Header: React.FC<PropsType> = (): JSX.Element => {
    return (
        <header className="header">
            <div className="container header__container">
                <nav className="header__nav">
                    <ul className="header__nav-list">
                        <li className="header__nav-list-item">Доска</li>
                    </ul>
                </nav>
            </div>
        </header>
    )
}

export default Header