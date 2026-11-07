package project_repo

import (
	"context"
	"database/sql"
	"errors"
	"fmt"

	"kanban-board/internal/models"
	repositorycommon "kanban-board/internal/storage/repositories/common"
)

type Repository struct {
	db *sql.DB
}

func Init(db *sql.DB) *Repository {
	return &Repository{db: db}
}

func (r *Repository) ListProjects(ctx context.Context, userID string) ([]models.Project, error) {
	if err := requireActiveUser(ctx, r.db, userID); err != nil {
		return nil, err
	}
	rows, err := r.db.QueryContext(ctx, `
		SELECT
			p.project_id,
			p.name,
			CASE WHEN access.can_view_board THEN COALESCE(p.description, '') ELSE '' END,
			access.can_view_board
		FROM projects p
		CROSS JOIN LATERAL (
			SELECT EXISTS(
				SELECT 1
				FROM users u
				LEFT JOIN project_members pm
					ON pm.project_id = p.project_id
					AND pm.user_id = u.user_id
				WHERE u.user_id = $1
					AND u.is_active = TRUE
					AND (u.system_role = 'CAPTAIN' OR pm.status = 'ACTIVE')
			) AS can_view_board
		) access
		WHERE p.is_archived = FALSE
		ORDER BY p.name
	`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	projects := make([]models.Project, 0)
	for rows.Next() {
		var project models.Project
		if err := rows.Scan(
			&project.ID,
			&project.Name,
			&project.Description,
			&project.CanViewBoard,
		); err != nil {
			return nil, err
		}
		if project.CanViewBoard {
			if err := r.fillProject(ctx, &project); err != nil {
				return nil, err
			}
		}
		projects = append(projects, project)
	}

	return projects, rows.Err()
}

func (r *Repository) GetProject(ctx context.Context, userID, projectID string) (*models.Project, error) {
	if err := repositorycommon.RequireMember(ctx, r.db, userID, projectID); err != nil {
		return nil, err
	}
	var project models.Project
	err := r.db.QueryRowContext(ctx, `
		SELECT project_id, name, COALESCE(description, '')
		FROM projects
		WHERE project_id = $1 AND is_archived = FALSE
	`, projectID).Scan(&project.ID, &project.Name, &project.Description)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, repositorycommon.ErrNotFound
	}
	if err != nil {
		return nil, err
	}
	if err := r.fillProject(ctx, &project); err != nil {
		return nil, err
	}
	project.CanViewBoard = true

	return &project, nil
}

func (r *Repository) fillProject(ctx context.Context, project *models.Project) error {
	rows, err := r.db.QueryContext(ctx, `
		SELECT user_id, role
		FROM project_members
		WHERE project_id = $1 AND status = 'ACTIVE'
		ORDER BY joined_at
	`, project.ID)
	if err != nil {
		return err
	}
	project.Members = make([]models.ProjectMember, 0)
	for rows.Next() {
		var member models.ProjectMember
		if err := rows.Scan(&member.UserID, &member.Role); err != nil {
			rows.Close()
			return err
		}
		project.Members = append(project.Members, member)
	}
	if err := rows.Close(); err != nil {
		return err
	}

	rows, err = r.db.QueryContext(ctx, `
		SELECT
			project_source_id,
			kind,
			title,
			url,
			file_name,
			mime_type,
			file_size,
			added_by_user_id,
			added_at
		FROM project_sources
		WHERE project_id = $1
		ORDER BY added_at
	`, project.ID)
	if err != nil {
		return err
	}
	defer rows.Close()
	project.Sources = make([]models.ProjectSource, 0)
	for rows.Next() {
		var source models.ProjectSource
		var url, fileName, mimeType sql.NullString
		var fileSize sql.NullInt64
		if err := rows.Scan(&source.ID, &source.Kind, &source.Title, &url, &fileName, &mimeType, &fileSize, &source.AddedBy, &source.AddedAt); err != nil {
			return err
		}
		if url.Valid {
			source.URL = &url.String
		}
		if source.Kind == "FILE" {
			source.File = &models.FileInfo{
				ID:          source.ID,
				Name:        fileName.String,
				SizeBytes:   fileSize.Int64,
				MimeType:    mimeType.String,
				DownloadURL: "/api/v1/files/" + source.ID,
			}
		}
		project.Sources = append(project.Sources, source)
	}

	return rows.Err()
}

func (r *Repository) CreateProject(ctx context.Context, userID, name, description string, members []models.ProjectMember) (string, error) {
	if err := repositorycommon.RequireCaptain(ctx, r.db, userID); err != nil {
		return "", err
	}
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return "", err
	}
	defer tx.Rollback()

	var projectID, boardID string
	if err := tx.QueryRowContext(ctx, `
		INSERT INTO projects (project_id, created_by_user_id, name, description)
		VALUES (gen_random_uuid(), $1, $2, NULLIF($3, ''))
		RETURNING project_id
	`, userID, name, description).Scan(&projectID); err != nil {
		return "", err
	}
	if err := tx.QueryRowContext(ctx, `
		INSERT INTO boards (board_id, project_id, name)
		VALUES (gen_random_uuid(), $1, 'Kanban')
		RETURNING board_id
	`, projectID).Scan(&boardID); err != nil {
		return "", err
	}
	if _, err := tx.ExecContext(ctx, `
		INSERT INTO board_columns (column_id, board_id, code, name, kind, color, position)
		VALUES
			(gen_random_uuid(), $1, 'BACKLOG', 'Ожидает', 'BACKLOG', '#8A94A6', 0),
			(gen_random_uuid(), $1, 'IN_PROGRESS', 'В работе', 'IN_PROGRESS', '#6576DF', 1),
			(gen_random_uuid(), $1, 'REVIEW', 'На проверке', 'REVIEW', '#DA8A37', 2),
			(gen_random_uuid(), $1, 'DONE', 'Выполнено', 'DONE', '#58A779', 3)
	`, boardID); err != nil {
		return "", err
	}
	for _, member := range members {
		if _, err := tx.ExecContext(ctx, `
			INSERT INTO project_members (project_id, user_id, role, status)
			SELECT $1, $2, $3, 'ACTIVE'
			WHERE EXISTS(SELECT 1 FROM users WHERE user_id = $2 AND is_active = TRUE)
			ON CONFLICT (project_id, user_id)
			DO UPDATE SET role = EXCLUDED.role, status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP
		`, projectID, member.UserID, member.Role); err != nil {
			return "", err
		}
	}

	if err := tx.Commit(); err != nil {
		return "", err
	}

	return projectID, nil
}

func (r *Repository) UpdateProject(ctx context.Context, actorID, projectID string, name, description *string, members *[]models.ProjectMember) error {
	if err := repositorycommon.RequireCaptain(ctx, r.db, actorID); err != nil {
		return err
	}
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	result, err := tx.ExecContext(ctx, `
		UPDATE projects
		SET
			name = COALESCE($1, name),
			description = CASE WHEN $2 THEN NULLIF($3, '') ELSE description END,
			updated_at = CURRENT_TIMESTAMP
		WHERE project_id = $4
	`, name, description != nil, description, projectID)
	if err != nil {
		return err
	}
	if err := repositorycommon.RequireAffected(result); err != nil {
		return err
	}
	if members != nil {
		var hasAssignedRemovedMember bool
		if err := tx.QueryRowContext(ctx, `
			SELECT EXISTS(
				SELECT 1
				FROM project_members pm
				JOIN task_assignees ta ON ta.user_id = pm.user_id
				JOIN tasks t ON t.task_id = ta.task_id
				JOIN board_columns bc ON bc.column_id = t.column_id
				JOIN boards b ON b.board_id = bc.board_id
				WHERE pm.project_id = $1
					AND pm.status = 'ACTIVE'
					AND b.project_id = $1
					AND bc.kind <> 'DONE'
					AND NOT EXISTS(
						SELECT 1
						FROM jsonb_to_recordset($2::jsonb) AS requested(user_id text, role text)
						WHERE requested.user_id::uuid = pm.user_id
					)
			)
		`, projectID, membersJSON(*members)).Scan(&hasAssignedRemovedMember); err != nil {
			return err
		}
		if hasAssignedRemovedMember {
			return fmt.Errorf("%w: У исключаемого участника есть незакрытые задачи — сначала переназначьте их", repositorycommon.ErrConflict)
		}
		for _, member := range *members {
			if _, err := tx.ExecContext(ctx, `
				INSERT INTO project_members (project_id, user_id, role, status)
				VALUES ($1, $2, $3, 'ACTIVE')
				ON CONFLICT (project_id, user_id)
				DO UPDATE SET role = EXCLUDED.role, status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP
			`, projectID, member.UserID, member.Role); err != nil {
				return err
			}
		}
		if _, err := tx.ExecContext(ctx, `
			UPDATE project_members pm
			SET status = 'LEFT', updated_at = CURRENT_TIMESTAMP
			WHERE pm.project_id = $1
				AND NOT EXISTS(
					SELECT 1 FROM jsonb_to_recordset($2::jsonb) AS requested(user_id text, role text)
					WHERE requested.user_id::uuid = pm.user_id
				)
		`, projectID, membersJSON(*members)); err != nil {
			return err
		}
	}

	return tx.Commit()
}

func membersJSON(members []models.ProjectMember) string {
	value := "["
	for index, member := range members {
		if index > 0 {
			value += ","
		}
		value += fmt.Sprintf(`{"user_id":%q,"role":%q}`, member.UserID, member.Role)
	}
	return value + "]"
}

func (r *Repository) DeleteProject(ctx context.Context, actorID, projectID string) ([]string, error) {
	if err := repositorycommon.RequireCaptain(ctx, r.db, actorID); err != nil {
		return nil, err
	}
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, err
	}
	defer tx.Rollback()

	rows, err := tx.QueryContext(ctx, `
		SELECT storage_key
		FROM project_sources
		WHERE project_id = $1 AND kind = 'FILE'
		UNION ALL
		SELECT a.storage_key
		FROM attachments a
		JOIN tasks t ON t.task_id = a.task_id
		JOIN board_columns bc ON bc.column_id = t.column_id
		JOIN boards b ON b.board_id = bc.board_id
		WHERE b.project_id = $1
	`, projectID)
	if err != nil {
		return nil, err
	}
	storageKeys := make([]string, 0)
	for rows.Next() {
		var storageKey string
		if err := rows.Scan(&storageKey); err != nil {
			rows.Close()
			return nil, err
		}
		storageKeys = append(storageKeys, storageKey)
	}
	if err := rows.Close(); err != nil {
		return nil, err
	}

	result, err := tx.ExecContext(ctx, `DELETE FROM projects WHERE project_id = $1`, projectID)
	if err != nil {
		return nil, err
	}
	if err := repositorycommon.RequireAffected(result); err != nil {
		return nil, err
	}
	if err := tx.Commit(); err != nil {
		return nil, err
	}

	return storageKeys, nil
}

func requireActiveUser(ctx context.Context, queryer repositorycommon.Queryer, userID string) error {
	var active bool
	err := queryer.QueryRowContext(ctx, `SELECT is_active FROM users WHERE user_id = $1`, userID).Scan(&active)
	if errors.Is(err, sql.ErrNoRows) {
		return repositorycommon.ErrNotFound
	}
	if err != nil {
		return err
	}
	if !active {
		return repositorycommon.ErrForbidden
	}
	return nil
}

func (r *Repository) AddLinkSource(ctx context.Context, userID, projectID, title, url string) (string, error) {
	if err := repositorycommon.RequireMember(ctx, r.db, userID, projectID); err != nil {
		return "", err
	}
	var sourceID string
	err := r.db.QueryRowContext(ctx, `
		INSERT INTO project_sources (project_source_id, project_id, kind, title, url, added_by_user_id)
		VALUES (gen_random_uuid(), $1, 'LINK', $2, $3, $4)
		RETURNING project_source_id
	`, projectID, title, url, userID).Scan(&sourceID)
	return sourceID, err
}

func (r *Repository) AddFileSource(ctx context.Context, userID, projectID, title, storageKey, mimeType string, size int64) (string, error) {
	if err := repositorycommon.RequireMember(ctx, r.db, userID, projectID); err != nil {
		return "", err
	}
	var sourceID string
	err := r.db.QueryRowContext(ctx, `
		INSERT INTO project_sources (
			project_source_id, project_id, kind, title, storage_key,
			file_name, mime_type, file_size, added_by_user_id
		)
		VALUES (gen_random_uuid(), $1, 'FILE', $2, $3, $2, $4, $5, $6)
		RETURNING project_source_id
	`, projectID, title, storageKey, mimeType, size, userID).Scan(&sourceID)
	return sourceID, err
}

func (r *Repository) DeleteSource(ctx context.Context, userID, projectID, sourceID string) (string, error) {
	var storageKey string
	err := r.db.QueryRowContext(ctx, `
		DELETE FROM project_sources ps
		USING users u
		WHERE ps.project_source_id = $1
			AND ps.project_id = $2
			AND u.user_id = $3
			AND (ps.added_by_user_id = $3 OR u.system_role = 'CAPTAIN')
		RETURNING COALESCE(ps.storage_key, '')
	`, sourceID, projectID, userID).Scan(&storageKey)
	if errors.Is(err, sql.ErrNoRows) {
		return "", repositorycommon.ErrForbidden
	}
	return storageKey, err
}

func (r *Repository) GetSourceFile(ctx context.Context, userID, sourceID string) (string, string, string, error) {
	var projectID, storageKey, name, mimeType string
	err := r.db.QueryRowContext(ctx, `
		SELECT project_id, COALESCE(storage_key, ''), COALESCE(file_name, ''), COALESCE(mime_type, '')
		FROM project_sources
		WHERE project_source_id = $1 AND kind = 'FILE'
	`, sourceID).Scan(&projectID, &storageKey, &name, &mimeType)
	if errors.Is(err, sql.ErrNoRows) {
		return "", "", "", repositorycommon.ErrNotFound
	}
	if err != nil {
		return "", "", "", err
	}
	if err := repositorycommon.RequireMember(ctx, r.db, userID, projectID); err != nil {
		return "", "", "", err
	}
	return storageKey, name, mimeType, nil
}
