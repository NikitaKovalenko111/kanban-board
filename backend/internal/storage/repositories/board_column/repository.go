package board_column_repo

import (
	"context"
	"database/sql"
	"fmt"
	"kanban-board/internal/models"
)

type BoardColumnRepo struct {
	db *sql.DB
}

func Init(db *sql.DB) *BoardColumnRepo {
	return &BoardColumnRepo{
		db: db,
	}
}

func (r *BoardColumnRepo) CreateBoardColumn(ctx context.Context, boardID string, name string, position int) (string, error) {
	var columnID string
	err := r.db.QueryRowContext(ctx, `
		INSERT INTO board_columns (column_id, board_id, name, position)
		VALUES (gen_random_uuid(), $1, $2, $3)
		RETURNING column_id
	`, boardID, name, position).Scan(&columnID)
	if err != nil {
		return "", fmt.Errorf("failed to create board column: %v", err)
	}

	return columnID, nil
}

func (r *BoardColumnRepo) GetBoardColumnByID(ctx context.Context, columnID string) (*models.BoardColumn, error) {
	var column models.BoardColumn
	err := r.db.QueryRowContext(ctx, `
		SELECT column_id, board_id, name, position
		FROM board_columns
		WHERE column_id = $1
	`, columnID).Scan(&column.ColumnID, &column.BoardID, &column.Name, &column.Position)
	if err != nil {
		return nil, fmt.Errorf("failed to get board column by id: %v", err)
	}

	return &column, nil
}

func (r *BoardColumnRepo) GetBoardColumnsByBoardID(ctx context.Context, boardID string) ([]models.BoardColumn, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT column_id, board_id, name, position
		FROM board_columns
		WHERE board_id = $1
		ORDER BY position
	`, boardID)
	if err != nil {
		return nil, fmt.Errorf("failed to get board columns: %v", err)
	}
	defer rows.Close()

	var columns []models.BoardColumn
	for rows.Next() {
		var column models.BoardColumn
		if err := rows.Scan(&column.ColumnID, &column.BoardID, &column.Name, &column.Position); err != nil {
			return nil, fmt.Errorf("failed to scan board column: %v", err)
		}
		columns = append(columns, column)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("failed to iterate board columns: %v", err)
	}

	return columns, nil
}

func (r *BoardColumnRepo) UpdateBoardColumn(ctx context.Context, columnID string, boardID string, name string, position int) error {
	_, err := r.db.ExecContext(ctx, `
		UPDATE board_columns
		SET board_id = $1, name = $2, position = $3
		WHERE column_id = $4
	`, boardID, name, position, columnID)
	if err != nil {
		return fmt.Errorf("failed to update board column: %v", err)
	}

	return nil
}

func (r *BoardColumnRepo) DeleteBoardColumn(ctx context.Context, columnID string) error {
	_, err := r.db.ExecContext(ctx, `
		DELETE FROM board_columns
		WHERE column_id = $1
	`, columnID)
	if err != nil {
		return fmt.Errorf("failed to delete board column: %v", err)
	}

	return nil
}
