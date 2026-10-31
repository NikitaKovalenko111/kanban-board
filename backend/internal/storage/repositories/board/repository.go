package board_repo

import (
	"context"
	"database/sql"
	"errors"
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

func (r *Repository) GetBoard(ctx context.Context, userID, projectID string) (*models.Board, error) {
	if err := repositorycommon.RequireMember(ctx, r.db, userID, projectID); err != nil {
		return nil, err
	}
	var board models.Board
	if err := r.db.QueryRowContext(ctx, `
		SELECT
		    board_id,
		    project_id,
		    name
		FROM boards
		WHERE project_id=$1
	`, projectID).Scan(&board.ID, &board.ProjectID, &board.Name); errors.Is(err, sql.ErrNoRows) {
		return nil, repositorycommon.ErrNotFound
	} else if err != nil {
		return nil, err
	}
	rows, err := r.db.QueryContext(ctx, `
		SELECT
		    column_id,
		    code,
		    name,
		    position
		FROM board_columns
		WHERE board_id=$1
		ORDER BY position
	`, board.ID)
	if err != nil {
		return nil, err
	}
	board.Columns = make([]models.BoardColumn, 0)
	for rows.Next() {
		var c models.BoardColumn
		if err := rows.Scan(&c.ID, &c.Code, &c.Name, &c.Position); err != nil {
			rows.Close()
			return nil, err
		}
		board.Columns = append(board.Columns, c)
	}
	rows.Close()
	board.Tasks, err = r.listTasks(ctx, projectID)
	if err != nil {
		return nil, err
	}
	return &board, nil
}

func (r *Repository) listTasks(ctx context.Context, projectID string) ([]models.Task, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT
			t.task_id,
			b.project_id,
			t.column_id,
			bc.code,
			t.created_by_user_id,
			t.title,
			COALESCE(t.description, ''),
			t.priority,
			t.position,
			t.due_date,
			t.completed_at,
			t.created_at,
			t.updated_at
		FROM tasks t
		JOIN board_columns bc ON bc.column_id = t.column_id
		JOIN boards b ON b.board_id = bc.board_id
		WHERE b.project_id = $1
		ORDER BY bc.position, t.position
	`, projectID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	tasks := make([]models.Task, 0)
	for rows.Next() {
		var t models.Task
		if err := rows.Scan(&t.ID, &t.ProjectID, &t.ColumnID, &t.Status, &t.CreatedBy, &t.Title, &t.Description, &t.Priority, &t.Position, &t.DueDate, &t.CompletedAt, &t.CreatedAt, &t.UpdatedAt); err != nil {
			return nil, err
		}
		ids, err := r.taskAssignees(ctx, t.ID)
		if err != nil {
			return nil, err
		}
		t.AssigneeIDs = ids
		tasks = append(tasks, t)
	}
	return tasks, rows.Err()
}

func (r *Repository) CreateColumn(ctx context.Context, userID, projectID, code, name string, position int) (string, error) {
	if err := repositorycommon.RequireLead(ctx, r.db, userID, projectID); err != nil {
		return "", err
	}
	var id string
	err := r.db.QueryRowContext(ctx, `
		INSERT INTO board_columns (
		    column_id,
		    board_id,
		    code,
		    name,
		    position
		)
		SELECT
		    gen_random_uuid(),
		    board_id,
		    $2,
		    $3,
		    $4
		FROM boards
		WHERE project_id=$1
		RETURNING column_id
	`, projectID, code, name, position).Scan(&id)
	return id, err
}

func (r *Repository) UpdateColumn(ctx context.Context, userID, columnID, code, name string, position int) error {
	var projectID string
	if err := r.db.QueryRowContext(ctx, `
		SELECT
		    b.project_id
		FROM board_columns bc
		JOIN boards b ON b.board_id=bc.board_id
		WHERE bc.column_id=$1
	`, columnID).Scan(&projectID); errors.Is(err, sql.ErrNoRows) {
		return repositorycommon.ErrNotFound
	} else if err != nil {
		return err
	}
	if err := repositorycommon.RequireLead(ctx, r.db, userID, projectID); err != nil {
		return err
	}
	result, err := r.db.ExecContext(ctx, `
		UPDATE board_columns
		SET
		    code=$1,
		    name=$2,
		    position=$3
		WHERE column_id=$4
	`, code, name, position, columnID)
	if err != nil {
		return err
	}
	return repositorycommon.RequireAffected(result)
}

func (r *Repository) DeleteColumn(ctx context.Context, userID, columnID string) error {
	var projectID string
	if err := r.db.QueryRowContext(ctx, `
		SELECT
		    b.project_id
		FROM board_columns bc
		JOIN boards b ON b.board_id=bc.board_id
		WHERE bc.column_id=$1
	`, columnID).Scan(&projectID); errors.Is(err, sql.ErrNoRows) {
		return repositorycommon.ErrNotFound
	} else if err != nil {
		return err
	}
	if err := repositorycommon.RequireLead(ctx, r.db, userID, projectID); err != nil {
		return err
	}
	result, err := r.db.ExecContext(ctx, `
		DELETE FROM board_columns
		WHERE column_id=$1
	`, columnID)
	if err != nil {
		return err
	}
	return repositorycommon.RequireAffected(result)
}

func (r *Repository) taskAssignees(ctx context.Context, taskID string) ([]string, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT
		    user_id
		FROM task_assignees
		WHERE task_id=$1
		ORDER BY user_id
	`, taskID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	ids := make([]string, 0)
	for rows.Next() {
		var id string
		if err := rows.Scan(&id); err != nil {
			return nil, err
		}
		ids = append(ids, id)
	}
	return ids, rows.Err()
}
