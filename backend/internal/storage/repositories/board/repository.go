package board_repo

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

func (r *Repository) ListColumns(ctx context.Context, userID, projectID string) ([]models.BoardColumn, error) {
	if err := repositorycommon.RequireMember(ctx, r.db, userID, projectID); err != nil {
		return nil, err
	}
	return listColumns(ctx, r.db, projectID)
}

type columnQueryer interface {
	QueryContext(context.Context, string, ...any) (*sql.Rows, error)
}

func listColumns(ctx context.Context, queryer columnQueryer, projectID string) ([]models.BoardColumn, error) {
	rows, err := queryer.QueryContext(ctx, `
		SELECT bc.column_id, b.project_id, bc.name, bc.position, bc.kind, bc.color
		FROM board_columns bc
		JOIN boards b ON b.board_id = bc.board_id
		WHERE b.project_id = $1
		ORDER BY bc.position
	`, projectID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	columns := make([]models.BoardColumn, 0)
	for rows.Next() {
		var column models.BoardColumn
		if err := rows.Scan(&column.ID, &column.ProjectID, &column.Title, &column.Position, &column.Kind, &column.Color); err != nil {
			return nil, err
		}
		columns = append(columns, column)
	}
	return columns, rows.Err()
}

func (r *Repository) ReplaceColumns(ctx context.Context, userID, projectID string, columns []models.BoardColumn) ([]models.BoardColumn, error) {
	if err := repositorycommon.RequireCaptain(ctx, r.db, userID); err != nil {
		return nil, err
	}
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, err
	}
	defer tx.Rollback()

	var boardID string
	if err := tx.QueryRowContext(ctx, `SELECT board_id FROM boards WHERE project_id = $1`, projectID).Scan(&boardID); errors.Is(err, sql.ErrNoRows) {
		return nil, repositorycommon.ErrNotFound
	} else if err != nil {
		return nil, err
	}
	if _, err := tx.ExecContext(ctx, `
		UPDATE board_columns SET position = position + 10000 WHERE board_id = $1
	`, boardID); err != nil {
		return nil, err
	}

	kept := make(map[string]struct{}, len(columns))
	for index := range columns {
		kind := "IN_PROGRESS"
		switch index {
		case 0:
			kind = "BACKLOG"
		case len(columns) - 2:
			kind = "REVIEW"
		case len(columns) - 1:
			kind = "DONE"
		}
		column := &columns[index]
		if column.ID == "" {
			if err := tx.QueryRowContext(ctx, `
				INSERT INTO board_columns (column_id, board_id, code, name, kind, color, position)
				VALUES (gen_random_uuid(), $1, 'COLUMN_' || gen_random_uuid()::text, $2, $3, $4, $5)
				RETURNING column_id
			`, boardID, column.Title, kind, column.Color, index).Scan(&column.ID); err != nil {
				return nil, err
			}
		} else {
			result, err := tx.ExecContext(ctx, `
				UPDATE board_columns
				SET name = $1, kind = $2, color = $3, position = $4
				WHERE column_id = $5 AND board_id = $6
			`, column.Title, kind, column.Color, index, column.ID, boardID)
			if err != nil {
				return nil, err
			}
			if err := repositorycommon.RequireAffected(result); err != nil {
				return nil, err
			}
		}
		kept[column.ID] = struct{}{}
	}

	rows, err := tx.QueryContext(ctx, `SELECT column_id, name FROM board_columns WHERE board_id = $1`, boardID)
	if err != nil {
		return nil, err
	}
	type existingColumn struct{ id, name string }
	existing := make([]existingColumn, 0)
	for rows.Next() {
		var column existingColumn
		if err := rows.Scan(&column.id, &column.name); err != nil {
			rows.Close()
			return nil, err
		}
		existing = append(existing, column)
	}
	rows.Close()
	for _, column := range existing {
		if _, exists := kept[column.id]; exists {
			continue
		}
		var taskCount int
		if err := tx.QueryRowContext(ctx, `SELECT COUNT(*) FROM tasks WHERE column_id = $1`, column.id).Scan(&taskCount); err != nil {
			return nil, err
		}
		if taskCount > 0 {
			return nil, fmt.Errorf("%w: В колонке %q есть задачи — сначала перенесите их", repositorycommon.ErrConflict, column.name)
		}
		if _, err := tx.ExecContext(ctx, `DELETE FROM board_columns WHERE column_id = $1`, column.id); err != nil {
			return nil, err
		}
	}
	if _, err := tx.ExecContext(ctx, `
		UPDATE tasks t
		SET completed_at = CASE WHEN bc.kind = 'DONE' THEN COALESCE(t.completed_at, CURRENT_TIMESTAMP) ELSE NULL END
		FROM board_columns bc
		WHERE t.column_id = bc.column_id AND bc.board_id = $1
	`, boardID); err != nil {
		return nil, err
	}
	if err := tx.Commit(); err != nil {
		return nil, err
	}

	return r.ListColumns(ctx, userID, projectID)
}
