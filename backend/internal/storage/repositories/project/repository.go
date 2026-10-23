package project_repo

import (
	"context"
	"database/sql"
	"fmt"
	"kanban-board/internal/models"
)

type ProjectRepo struct {
	db  *sql.DB
	ctx context.Context
}

func Init(sql *sql.DB, ctx context.Context) *ProjectRepo {
	return &ProjectRepo{
		db:  sql,
		ctx: ctx,
	}
}

func (r *ProjectRepo) CreateProject(teamID string, name string, description string) (string, error) {
	var id string
	err := r.db.QueryRowContext(r.ctx, `
		INSERT INTO project (id, team_id, name, description)
		VALUES (gen_random_uuid(), $1, $2, $3)
		RETURNING id
	`, teamID, name, description).Scan(&id)

	if err != nil {
		return "", fmt.Errorf("failed to create project: %v", err)
	}

	return id, nil
}

func (r *ProjectRepo) GetProjectByID(id string) (*models.Project, error) {
	var project models.Project
	err := r.db.QueryRowContext(r.ctx, `
		SELECT id, team_id, name, COALESCE(description, ''), created_at
		FROM project
		WHERE id = $1
	`, id).Scan(&project.ID, &project.TeamID, &project.Name, &project.Description, &project.CreatedAt)

	if err != nil {
		return nil, fmt.Errorf("failed to get project by id: %v", err)
	}

	return &project, nil
}

func (r *ProjectRepo) GetProjectByName(name string) (*models.Project, error) {
	var project models.Project
	err := r.db.QueryRowContext(r.ctx, `
		SELECT id, team_id, name, COALESCE(description, ''), created_at
		FROM project
		WHERE name = $1
	`, name).Scan(&project.ID, &project.TeamID, &project.Name, &project.Description, &project.CreatedAt)

	if err != nil {
		return nil, fmt.Errorf("failed to get project by name: %v", err)
	}

	return &project, nil
}

func (r *ProjectRepo) GetProjectsByTeamID(teamID string) ([]models.Project, error) {
	rows, err := r.db.QueryContext(r.ctx, `
		SELECT id, team_id, name, COALESCE(description, ''), created_at
		FROM project
		WHERE team_id = $1
		ORDER BY created_at
	`, teamID)
	if err != nil {
		return nil, fmt.Errorf("failed to get projects by team id: %v", err)
	}
	defer rows.Close()

	var projects []models.Project
	for rows.Next() {
		var project models.Project
		if err := rows.Scan(
			&project.ID,
			&project.TeamID,
			&project.Name,
			&project.Description,
			&project.CreatedAt,
		); err != nil {
			return nil, fmt.Errorf("failed to scan project: %v", err)
		}
		projects = append(projects, project)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("failed to iterate projects: %v", err)
	}

	return projects, nil
}

func (r *ProjectRepo) UpdateProjectByID(id string, teamID string, name string, description string) error {
	_, err := r.db.ExecContext(r.ctx, `
		UPDATE project
		SET team_id = $1, name = $2, description = $3
		WHERE id = $4
	`, teamID, name, description, id)

	if err != nil {
		return fmt.Errorf("failed to update project: %v", err)
	}

	return nil
}

func (r *ProjectRepo) DeleteProjectByID(id string) error {
	_, err := r.db.ExecContext(r.ctx, `
		DELETE FROM project
		WHERE id = $1
	`, id)
	if err != nil {
		return fmt.Errorf("failed to delete project: %v", err)
	}

	return nil
}
