package com.kanbanboard.mobile.core.policy

import com.kanbanboard.mobile.core.model.Project
import com.kanbanboard.mobile.core.model.ProjectSource
import com.kanbanboard.mobile.core.model.Session

/**
 * Проекты: создаёт и настраивает (название, описание, состав, роли) капитан.
 * Источники добавляют участники проекта и капитан; удаляет — автор источника или капитан.
 */
object ProjectPolicy {
    fun canManage(session: Session): Boolean = session.isCaptain

    fun canAddSource(session: Session, project: Project): Boolean =
        session.isCaptain || project.isMember(session.user.id)

    fun canRemoveSource(session: Session, source: ProjectSource): Boolean =
        session.isCaptain || source.addedBy == session.user.id

    fun isValidUrl(url: String): Boolean {
        val trimmed = url.trim()
        return (trimmed.startsWith("https://") || trimmed.startsWith("http://")) &&
            trimmed.length in 11..2000 && trimmed.none { it.isWhitespace() }
    }
}
