package task_assignee_repo

import (
	"context"
	"database/sql"
	"fmt"
	"kanban-board/internal/models"
)

type TaskAssigneeRepo struct {
	db  *sql.DB
	ctx context.Context
}

func Init(db *sql.DB, ctx context.Context) *TaskAssigneeRepo {
	return &TaskAssigneeRepo{
		db:  db,
		ctx: ctx,
	}
}

func (r *TaskAssigneeRepo) AddTaskAssignee(taskID string, userID string) error {
	_, err := r.db.ExecContext(r.ctx, `
		INSERT INTO task_assignee (task_id, user_id)
		VALUES ($1, $2)
	`, taskID, userID)
	if err != nil {
		return fmt.Errorf("failed to add task assignee: %v", err)
	}

	return nil
}

func (r *TaskAssigneeRepo) GetTaskAssigneesByTaskID(taskID string) ([]models.TaskAssignee, error) {
	rows, err := r.db.QueryContext(r.ctx, `
		SELECT task_id, user_id
		FROM task_assignee
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

func (r *TaskAssigneeRepo) UpdateTaskAssignee(taskID string, oldUserID string, newUserID string) error {
	_, err := r.db.ExecContext(r.ctx, `
		UPDATE task_assignee
		SET user_id = $1
		WHERE task_id = $2 AND user_id = $3
	`, newUserID, taskID, oldUserID)
	if err != nil {
		return fmt.Errorf("failed to update task assignee: %v", err)
	}

	return nil
}

func (r *TaskAssigneeRepo) RemoveTaskAssignee(taskID string, userID string) error {
	_, err := r.db.ExecContext(r.ctx, `
		DELETE FROM task_assignee
		WHERE task_id = $1 AND user_id = $2
	`, taskID, userID)
	if err != nil {
		return fmt.Errorf("failed to remove task assignee: %v", err)
	}

	return nil
}
