package task_assignee_repo

import (
	"context"
	"database/sql"
	"fmt"
	"kanban-board/internal/models"
)

type TaskAssigneeRepo struct {
	db *sql.DB
}

func Init(db *sql.DB) *TaskAssigneeRepo {
	return &TaskAssigneeRepo{
		db: db,
	}
}

func (r *TaskAssigneeRepo) AddTaskAssignee(ctx context.Context, taskID string, userID string) error {
	_, err := r.db.ExecContext(ctx, `
		INSERT INTO task_assignees (task_id, user_id)
		VALUES ($1, $2)
	`, taskID, userID)
	if err != nil {
		return fmt.Errorf("failed to add task assignee: %v", err)
	}

	return nil
}

func (r *TaskAssigneeRepo) GetTaskAssigneesByTaskID(ctx context.Context, taskID string) ([]models.TaskAssignee, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT task_id, user_id
		FROM task_assignees
		WHERE task_id = $1
	`, taskID)
	if err != nil {
		return nil, fmt.Errorf("failed to get task assignees: %v", err)
	}
	defer rows.Close()

	var assignees []models.TaskAssignee
	for rows.Next() {
		var assignee models.TaskAssignee
		if err := rows.Scan(&assignee.TaskID, &assignee.UserID); err != nil {
			return nil, fmt.Errorf("failed to scan task assignee: %v", err)
		}
		assignees = append(assignees, assignee)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("failed to iterate task assignees: %v", err)
	}

	return assignees, nil
}

func (r *TaskAssigneeRepo) GetTaskAssigneesByUserID(ctx context.Context, userID string) ([]models.TaskAssignee, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT task_id, user_id
		FROM task_assignees
		WHERE user_id = $1
	`, userID)
	if err != nil {
		return nil, fmt.Errorf("failed to get task assignees by user id: %v", err)
	}
	defer rows.Close()

	var assignees []models.TaskAssignee
	for rows.Next() {
		var assignee models.TaskAssignee
		if err := rows.Scan(&assignee.TaskID, &assignee.UserID); err != nil {
			return nil, fmt.Errorf("failed to scan task assignee: %v", err)
		}
		assignees = append(assignees, assignee)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("failed to iterate task assignees by user id: %v", err)
	}

	return assignees, nil
}

func (r *TaskAssigneeRepo) UpdateTaskAssignee(ctx context.Context, taskID string, oldUserID string, newUserID string) error {
	_, err := r.db.ExecContext(ctx, `
		UPDATE task_assignees
		SET user_id = $1
		WHERE task_id = $2 AND user_id = $3
	`, newUserID, taskID, oldUserID)
	if err != nil {
		return fmt.Errorf("failed to update task assignee: %v", err)
	}

	return nil
}

func (r *TaskAssigneeRepo) RemoveTaskAssignee(ctx context.Context, taskID string, userID string) error {
	_, err := r.db.ExecContext(ctx, `
		DELETE FROM task_assignees
		WHERE task_id = $1 AND user_id = $2
	`, taskID, userID)
	if err != nil {
		return fmt.Errorf("failed to remove task assignee: %v", err)
	}

	return nil
}
