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
	return &Repository{
		db: db,
	}
}

func (r *Repository) ListProjects(ctx context.Context, userID string) ([]models.Project, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT
			p.project_id,
			COALESCE(p.created_by_user_id::text, ''),
			p.name,
			COALESCE(p.description, ''),
			p.join_policy,
			p.is_archived,
			EXISTS(
				SELECT 1
				FROM project_members pm
				WHERE pm.project_id = p.project_id
					AND pm.user_id = $1
					AND pm.status = 'ACTIVE'
			),
			p.created_at,
			p.updated_at
		FROM projects p
		WHERE p.is_archived = FALSE
			AND (
				p.join_policy = 'OPEN'
				OR EXISTS(
					SELECT 1
					FROM project_members pm
					WHERE pm.project_id = p.project_id
						AND pm.user_id = $1
						AND pm.status <> 'LEFT'
				)
			)
		ORDER BY p.created_at
	`, userID)
	if err != nil {
		return nil, fmt.Errorf("list projects: %w", err)
	}
	defer rows.Close()
	projects := make([]models.Project, 0)
	for rows.Next() {
		var project models.Project
		if err := rows.Scan(&project.ID, &project.CreatedBy, &project.Name, &project.Description, &project.JoinPolicy,
			&project.Archived, &project.IsMember, &project.CreatedAt, &project.UpdatedAt); err != nil {
			return nil, err
		}
		projects = append(projects, project)
	}
	return projects, rows.Err()
}

func (r *Repository) GetProject(ctx context.Context, userID, projectID string) (*models.Project, error) {
	var project models.Project
	err := r.db.QueryRowContext(ctx, `
		SELECT
			p.project_id,
			COALESCE(p.created_by_user_id::text, ''),
			p.name,
			COALESCE(p.description, ''),
			p.join_policy,
			p.is_archived,
			EXISTS(
				SELECT 1
				FROM project_members pm
				WHERE pm.project_id = p.project_id
					AND pm.user_id = $1
					AND pm.status = 'ACTIVE'
			),
			p.created_at,
			p.updated_at
		FROM projects p
		WHERE p.project_id = $2
			AND (
				p.join_policy = 'OPEN'
				OR EXISTS(
					SELECT 1
					FROM project_members pm
					WHERE pm.project_id = p.project_id
						AND pm.user_id = $1
						AND pm.status <> 'LEFT'
				)
			)
	`, userID, projectID).Scan(&project.ID, &project.CreatedBy, &project.Name, &project.Description, &project.JoinPolicy,
		&project.Archived, &project.IsMember, &project.CreatedAt, &project.UpdatedAt)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, repositorycommon.ErrNotFound
	}
	if err != nil {
		return nil, fmt.Errorf("get project: %w", err)
	}
	return &project, nil
}

func (r *Repository) CreateProject(ctx context.Context, userID, name, description, joinPolicy string) (string, error) {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return "", err
	}
	defer tx.Rollback()
	var projectID, boardID string
	if err := tx.QueryRowContext(ctx, `
		INSERT INTO projects (
			project_id,
			created_by_user_id,
			name,
			description,
			join_policy
		)
		VALUES (
			gen_random_uuid(),
			$1,
			$2,
			NULLIF($3, ''),
			$4
		)
		RETURNING project_id
	`, userID, name, description, joinPolicy).Scan(&projectID); err != nil {
		return "", fmt.Errorf("create project: %w", err)
	}
	if _, err := tx.ExecContext(ctx, `
		INSERT INTO project_members (
			project_id,
			user_id,
			role
		)
		VALUES ($1, $2, 'LEAD')
	`, projectID, userID); err != nil {
		return "", err
	}
	if err := tx.QueryRowContext(ctx, `
		INSERT INTO boards (
			board_id,
			project_id,
			name
		)
		VALUES (gen_random_uuid(), $1, 'Kanban')
		RETURNING board_id
	`, projectID).Scan(&boardID); err != nil {
		return "", err
	}
	if _, err := tx.ExecContext(ctx, `
		INSERT INTO board_columns (
			column_id,
			board_id,
			code,
			name,
			position
		)
		VALUES
			(gen_random_uuid(), $1, 'WAITING', 'Ожидает', 0),
			(gen_random_uuid(), $1, 'WORKING', 'В работе', 1),
			(gen_random_uuid(), $1, 'CHECKING', 'На проверке', 2),
			(gen_random_uuid(), $1, 'DONE', 'Готово', 3)
	`, boardID); err != nil {
		return "", err
	}
	if err := tx.Commit(); err != nil {
		return "", err
	}
	return projectID, nil
}

func (r *Repository) UpdateProject(ctx context.Context, userID, projectID, name, description, joinPolicy string, archived bool) error {
	if err := repositorycommon.RequireLead(ctx, r.db, userID, projectID); err != nil {
		return err
	}
	result, err := r.db.ExecContext(ctx, `
		UPDATE projects
		SET
		    name=$1,
		    description=NULLIF($2,''),
		    join_policy=$3,
		    is_archived=$4,
		    updated_at=CURRENT_TIMESTAMP
		WHERE project_id=$5
	`, name, description, joinPolicy, archived, projectID)
	if err != nil {
		return fmt.Errorf("update project: %w", err)
	}
	return repositorycommon.RequireAffected(result)
}

func (r *Repository) DeleteProject(ctx context.Context, userID, projectID string) error {
	if err := repositorycommon.RequireLead(ctx, r.db, userID, projectID); err != nil {
		return err
	}
	result, err := r.db.ExecContext(ctx, `
		DELETE FROM projects
		WHERE project_id=$1
	`, projectID)
	if err != nil {
		return fmt.Errorf("delete project: %w", err)
	}
	return repositorycommon.RequireAffected(result)
}

func (r *Repository) ListProjectMembers(ctx context.Context, userID, projectID string) ([]models.ProjectMember, error) {
	if err := repositorycommon.RequireMember(ctx, r.db, userID, projectID); err != nil {
		return nil, err
	}
	rows, err := r.db.QueryContext(ctx, `
		SELECT
			u.user_id,
			u.display_name,
			COALESCE(u.position, ''),
			u.email,
			pm.role,
			pm.status,
			pm.joined_at
		FROM project_members pm
		JOIN users u ON u.user_id = pm.user_id
		WHERE pm.project_id = $1
			AND pm.status <> 'LEFT'
		ORDER BY pm.joined_at
	`, projectID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	members := make([]models.ProjectMember, 0)
	for rows.Next() {
		var m models.ProjectMember
		if err := rows.Scan(&m.UserID, &m.DisplayName, &m.Position, &m.Email, &m.Role, &m.Status, &m.JoinedAt); err != nil {
			return nil, err
		}
		members = append(members, m)
	}
	return members, rows.Err()
}

func (r *Repository) AddProjectMember(ctx context.Context, actorID, projectID, userID, role string) error {
	if err := repositorycommon.RequireLead(ctx, r.db, actorID, projectID); err != nil {
		return err
	}
	_, err := r.db.ExecContext(ctx, `
		INSERT INTO project_members (
			project_id,
			user_id,
			role,
			status
		)
		VALUES ($1, $2, $3, 'ACTIVE')
		ON CONFLICT (project_id, user_id)
		DO UPDATE SET
			role = EXCLUDED.role,
			status = 'ACTIVE',
			updated_at = CURRENT_TIMESTAMP
	`, projectID, userID, role)
	return err
}

func (r *Repository) UpdateProjectMember(ctx context.Context, actorID, projectID, userID, role, status string) error {
	if err := repositorycommon.RequireLead(ctx, r.db, actorID, projectID); err != nil {
		return err
	}
	result, err := r.db.ExecContext(ctx, `
		UPDATE project_members
		SET
		    role=$1,
		    status=$2,
		    updated_at=CURRENT_TIMESTAMP
		WHERE project_id=$3 AND user_id=$4
	`, role, status, projectID, userID)
	if err != nil {
		return err
	}
	return repositorycommon.RequireAffected(result)
}

func (r *Repository) RemoveProjectMember(ctx context.Context, actorID, projectID, userID string) error {
	if actorID != userID {
		if err := repositorycommon.RequireLead(ctx, r.db, actorID, projectID); err != nil {
			return err
		}
	}
	var role string
	if err := r.db.QueryRowContext(ctx, `
		SELECT
		    role
		FROM project_members
		WHERE project_id=$1 AND user_id=$2
	`, projectID, userID).Scan(&role); err != nil {
		return repositorycommon.ErrNotFound
	}
	if role == "LEAD" {
		var count int
		if err := r.db.QueryRowContext(ctx, `
		SELECT
		    COUNT(*)
		FROM project_members
		WHERE project_id=$1 AND role='LEAD' AND status='ACTIVE'
	`, projectID).Scan(&count); err != nil {
			return err
		}
		if count <= 1 {
			return repositorycommon.ErrConflict
		}
	}
	result, err := r.db.ExecContext(ctx, `
		UPDATE project_members
		SET
		    status='LEFT',
		    updated_at=CURRENT_TIMESTAMP
		WHERE project_id=$1 AND user_id=$2
	`, projectID, userID)
	if err != nil {
		return err
	}
	return repositorycommon.RequireAffected(result)
}

func (r *Repository) JoinProject(ctx context.Context, userID, projectID string) error {
	var policy string
	if err := r.db.QueryRowContext(ctx, `
		SELECT
		    join_policy
		FROM projects
		WHERE project_id=$1 AND is_archived=FALSE
	`, projectID).Scan(&policy); errors.Is(err, sql.ErrNoRows) {
		return repositorycommon.ErrNotFound
	} else if err != nil {
		return err
	}
	if policy != "OPEN" {
		return repositorycommon.ErrForbidden
	}
	_, err := r.db.ExecContext(ctx, `
		INSERT INTO project_members (
			project_id,
			user_id,
			role,
			status
		)
		VALUES ($1, $2, 'MEMBER', 'ACTIVE')
		ON CONFLICT (project_id, user_id)
		DO UPDATE SET
			status = 'ACTIVE',
			updated_at = CURRENT_TIMESTAMP
	`, projectID, userID)
	return err
}
