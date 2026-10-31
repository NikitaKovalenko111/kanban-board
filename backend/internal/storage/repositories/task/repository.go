package task_repo

import (
	"context"
	"database/sql"
	"fmt"
	"kanban-board/internal/models"
	"time"
)

type TaskRepo struct {
	db *sql.DB
}

func Init(db *sql.DB) *TaskRepo {
	return &TaskRepo{
		db: db,
	}
}

func (r *TaskRepo) CreateTask(ctx context.Context, columnID string, createdBy string, title string, description string, priority string, position int, dueDate *time.Time) (string, error) {
	var taskID string
	err := r.db.QueryRowContext(ctx, `
		INSERT INTO tasks (
			task_id, column_id, created_by_user_id, title, description,
			priority, position, due_date
		)
		VALUES (gen_random_uuid(), $1, $2, $3, $4, $5, $6, $7)
		RETURNING task_id
	`, columnID, createdBy, title, description, priority, position, dueDate).Scan(&taskID)
	if err != nil {
		return "", fmt.Errorf("failed to create task: %v", err)
	}

	return taskID, nil
}

func (r *TaskRepo) GetTaskByID(ctx context.Context, taskID string) (*models.Task, error) {
	var task models.Task
	err := r.db.QueryRowContext(ctx, `
		SELECT task_id, column_id, created_by_user_id, title, COALESCE(description, ''),
			priority, position, due_date, completed_at, created_at, updated_at
		FROM tasks
		WHERE task_id = $1
	`, taskID).Scan(
		&task.TaskID,
		&task.ColumnID,
		&task.CreatedByUserID,
		&task.Title,
		&task.Description,
		&task.Priority,
		&task.Position,
		&task.DueDate,
		&task.CompletedAt,
		&task.CreatedAt,
		&task.UpdatedAt,
	)
	if err != nil {
		return nil, fmt.Errorf("failed to get task by id: %v", err)
	}

	return &task, nil
}

func (r *TaskRepo) GetTasksByColumnID(ctx context.Context, columnID string) ([]models.Task, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT task_id, column_id, created_by_user_id, title, COALESCE(description, ''),
			priority, position, due_date, completed_at, created_at, updated_at
		FROM tasks
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
			&task.TaskID,
			&task.ColumnID,
			&task.CreatedByUserID,
			&task.Title,
			&task.Description,
			&task.Priority,
			&task.Position,
			&task.DueDate,
			&task.CompletedAt,
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

func (r *TaskRepo) UpdateTask(ctx context.Context, taskID string, columnID string, title string, description string, priority string, position int, dueDate *time.Time) error {
	_, err := r.db.ExecContext(ctx, `
		UPDATE tasks
		SET column_id = $1,
			title = $2,
			description = $3,
			priority = $4,
			position = $5,
			due_date = $6,
			updated_at = CURRENT_TIMESTAMP
		WHERE task_id = $7
	`, columnID, title, description, priority, position, dueDate, taskID)
	if err != nil {
		return fmt.Errorf("failed to update task: %v", err)
	}

	return nil
}

func (r *TaskRepo) DeleteTask(ctx context.Context, taskID string) error {
	_, err := r.db.ExecContext(ctx, `
		DELETE FROM tasks
		WHERE task_id = $1
	`, taskID)
	if err != nil {
		return fmt.Errorf("failed to delete task: %v", err)
	}

	return nil
}
