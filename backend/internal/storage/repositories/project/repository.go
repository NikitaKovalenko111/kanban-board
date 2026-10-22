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

func (r *ProjectRepo) CreateProject(name string, description string) (string, error) {
	var id string
	err := r.db.QueryRow(`
		INSERT INTO project (name, description)
		VALUES ($1, $2)
		RETURNING id
	`, name, description).Scan(&id)

	if err != nil {
		return "", fmt.Errorf("failed to create project: %v", err)
	}

	return id, nil
}

func (r *ProjectRepo) GetProjectByID(id string) (*models.Project, error) {
	var project models.Project
	err := r.db.QueryRowContext(r.ctx, `
		SELECT id, name, description, created_at
		FROM project
		WHERE id = $1
	`, id).Scan(&project.ID, &project.Name, &project.Description, &project.CreatedAt)

	if err != nil {
		return nil, fmt.Errorf("failed to get project by id: %v", err)
	}

	return &project, nil
}

func (r *ProjectRepo) GetProjectByName(name string) (*models.Project, error) {
	var project models.Project
	err := r.db.QueryRowContext(r.ctx, `
		SELECT id, name, description, created_at
		FROM project
		WHERE name = $1
	`, name).Scan(&project.ID, &project.Name, &project.Description, &project.CreatedAt)

	if err != nil {
		return nil, fmt.Errorf("failed to get project by name: %v", err)
	}

	return &project, nil
}

func (r *ProjectRepo) UpdateProjectByID(id string, name string, description string) error {
	_, err := r.db.ExecContext(r.ctx, `
		UPDATE project
		SET name = $1, description = $2
		WHERE id = $3
	`, name, description, id)

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
