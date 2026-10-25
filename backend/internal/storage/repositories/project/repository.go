package project_repo

import (
	"context"
	"database/sql"
	"fmt"
	"kanban-board/internal/models"
)

type ProjectRepo struct {
	db *sql.DB
}

func Init(sql *sql.DB) *ProjectRepo {
	return &ProjectRepo{
		db: sql,
	}
}

func (r *ProjectRepo) CreateProject(ctx context.Context, teamID string, name string, description string) (string, error) {
	var projectID string
	err := r.db.QueryRowContext(ctx, `
		INSERT INTO projects (project_id, team_id, name, description)
		VALUES (gen_random_uuid(), $1, $2, $3)
		RETURNING project_id
	`, teamID, name, description).Scan(&projectID)

	if err != nil {
		return "", fmt.Errorf("failed to create project: %v", err)
	}

	return projectID, nil
}

func (r *ProjectRepo) GetProjectByID(ctx context.Context, projectID string) (*models.Project, error) {
	var project models.Project
	err := r.db.QueryRowContext(ctx, `
		SELECT project_id, team_id, name, COALESCE(description, ''), created_at
		FROM projects
		WHERE project_id = $1
	`, projectID).Scan(&project.ProjectID, &project.TeamID, &project.Name, &project.Description, &project.CreatedAt)

	if err != nil {
		return nil, fmt.Errorf("failed to get project by id: %v", err)
	}

	return &project, nil
}

func (r *ProjectRepo) GetProjectByName(ctx context.Context, name string) (*models.Project, error) {
	var project models.Project
	err := r.db.QueryRowContext(ctx, `
		SELECT project_id, team_id, name, COALESCE(description, ''), created_at
		FROM projects
		WHERE name = $1
	`, name).Scan(&project.ProjectID, &project.TeamID, &project.Name, &project.Description, &project.CreatedAt)

	if err != nil {
		return nil, fmt.Errorf("failed to get project by name: %v", err)
	}

	return &project, nil
}

func (r *ProjectRepo) GetProjectsByTeamID(ctx context.Context, teamID string) ([]models.Project, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT project_id, team_id, name, COALESCE(description, ''), created_at
		FROM projects
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
			&project.ProjectID,
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

func (r *ProjectRepo) UpdateProjectByID(ctx context.Context, projectID string, teamID string, name string, description string) error {
	_, err := r.db.ExecContext(ctx, `
		UPDATE projects
		SET team_id = $1, name = $2, description = $3
		WHERE project_id = $4
	`, teamID, name, description, projectID)

	if err != nil {
		return fmt.Errorf("failed to update project: %v", err)
	}

	return nil
}

func (r *ProjectRepo) DeleteProjectByID(ctx context.Context, projectID string) error {
	_, err := r.db.ExecContext(ctx, `
		DELETE FROM projects
		WHERE project_id = $1
	`, projectID)
	if err != nil {
		return fmt.Errorf("failed to delete project: %v", err)
	}

	return nil
}
