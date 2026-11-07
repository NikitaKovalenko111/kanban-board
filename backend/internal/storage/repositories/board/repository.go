package board_repo

import (
	"context"
	"database/sql"

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
