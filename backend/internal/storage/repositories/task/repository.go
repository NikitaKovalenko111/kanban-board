package task_repo

import (
	"context"
	"database/sql"
	"fmt"
	"kanban-board/internal/models"
	"time"
)

type TaskRepo struct {
	db  *sql.DB
	ctx context.Context
}

func Init(db *sql.DB, ctx context.Context) *TaskRepo {
	return &TaskRepo{
		db:  db,
		ctx: ctx,
	}
}

func (r *TaskRepo) CreateTask(columnID string, createdBy string, title string, description string, priority string, position int, dueDate *time.Time) (string, error) {
	var id string
	err := r.db.QueryRowContext(r.ctx, `
		INSERT INTO task (
			id, column_id, created_by, title, description,
			priority, position, due_date
		)
		VALUES (gen_random_uuid(), $1, $2, $3, $4, $5, $6, $7)
		RETURNING id
	`, columnID, createdBy, title, description, priority, position, dueDate).Scan(&id)
	if err != nil {
		return "", fmt.Errorf("failed to create task: %v", err)
	}

	return id, nil
}

func (r *TaskRepo) GetTaskByID(id string) (*models.Task, error) {
	var task models.Task
	err := r.db.QueryRowContext(r.ctx, `
		SELECT id, column_id, created_by, title, COALESCE(description, ''),
			priority, position, due_date, created_at, updated_at
		FROM task
		WHERE id = $1
	`, id).Scan(
		&task.ID,
		&task.ColumnID,
		&task.CreatedBy,
		&task.Title,
		&task.Description,
		&task.Priority,
		&task.Position,
		&task.DueDate,
		&task.CreatedAt,
		&task.UpdatedAt,
	)
	if err != nil {
		return nil, fmt.Errorf("failed to get task by id: %v", err)
	}

	return &task, nil
}

func (r *TaskRepo) GetTasksByColumnID(columnID string) ([]models.Task, error) {
	rows, err := r.db.QueryContext(r.ctx, `
		SELECT id, column_id, created_by, title, COALESCE(description, ''),
			priority, position, due_date, created_at, updated_at
		FROM task
		WHERE column_id = $1
		ORDER BY position
	`, columnID)
	if err != nil {
		return nil, fmt.Errorf("failed to get tasks by column id: %v", err)
	}
	defer rows.Close()

	var tasks []models.Task
	for rows.Next() {
		var task models.Task
		if err := rows.Scan(
			&task.ID,
			&task.ColumnID,
			&task.CreatedBy,
			&task.Title,
			&task.Description,
			&task.Priority,
			&task.Position,
			&task.DueDate,
			&task.CreatedAt,
			&task.UpdatedAt,
		); err != nil {
			return nil, fmt.Errorf("failed to scan task: %v", err)
		}
		tasks = append(tasks, task)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("failed to iterate tasks: %v", err)
	}

	return tasks, nil
}

func (r *TaskRepo) UpdateTask(id string, columnID string, title string, description string, priority string, position int, dueDate *time.Time) error {
	_, err := r.db.ExecContext(r.ctx, `
		UPDATE task
		SET column_id = $1,
			title = $2,
			description = $3,
			priority = $4,
			position = $5,
			due_date = $6,
			updated_at = CURRENT_TIMESTAMP
		WHERE id = $7
	`, columnID, title, description, priority, position, dueDate, id)
	if err != nil {
		return fmt.Errorf("failed to update task: %v", err)
	}

	return nil
}

func (r *TaskRepo) DeleteTask(id string) error {
	_, err := r.db.ExecContext(r.ctx, `
		DELETE FROM task
		WHERE id = $1
	`, id)
	if err != nil {
		return fmt.Errorf("failed to delete task: %v", err)
	}

	return nil
}
