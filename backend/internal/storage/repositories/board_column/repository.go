package board_column_repo

import (
	"context"
	"database/sql"
	"fmt"
	"kanban-board/internal/models"
)

type BoardColumnRepo struct {
	db  *sql.DB
	ctx context.Context
}

func Init(db *sql.DB, ctx context.Context) *BoardColumnRepo {
	return &BoardColumnRepo{
		db:  db,
		ctx: ctx,
	}
}

func (r *BoardColumnRepo) CreateBoardColumn(boardID string, name string, position int) (string, error) {
	var id string
	err := r.db.QueryRowContext(r.ctx, `
		INSERT INTO board_column (id, board_id, name, position)
		VALUES (gen_random_uuid(), $1, $2, $3)
		RETURNING id
	`, boardID, name, position).Scan(&id)
	if err != nil {
		return "", fmt.Errorf("failed to create board column: %v", err)
	}

	return id, nil
}

func (r *BoardColumnRepo) GetBoardColumnByID(id string) (*models.BoardColumn, error) {
	var column models.BoardColumn
	err := r.db.QueryRowContext(r.ctx, `
		SELECT id, board_id, name, position
		FROM board_column
		WHERE id = $1
	`, id).Scan(&column.ID, &column.BoardID, &column.Name, &column.Position)
	if err != nil {
		return nil, fmt.Errorf("failed to get board column by id: %v", err)
	}

	return &column, nil
}

func (r *BoardColumnRepo) GetBoardColumnsByBoardID(boardID string) ([]models.BoardColumn, error) {
	rows, err := r.db.QueryContext(r.ctx, `
		SELECT id, board_id, name, position
		FROM board_column
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
		if err := rows.Scan(&column.ID, &column.BoardID, &column.Name, &column.Position); err != nil {
			return nil, fmt.Errorf("failed to scan board column: %v", err)
		}
		columns = append(columns, column)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("failed to iterate board columns: %v", err)
	}

	return columns, nil
}

func (r *BoardColumnRepo) UpdateBoardColumn(id string, boardID string, name string, position int) error {
	_, err := r.db.ExecContext(r.ctx, `
		UPDATE board_column
		SET board_id = $1, name = $2, position = $3
		WHERE id = $4
	`, boardID, name, position, id)
	if err != nil {
		return fmt.Errorf("failed to update board column: %v", err)
	}

	return nil
}

func (r *BoardColumnRepo) DeleteBoardColumn(id string) error {
	_, err := r.db.ExecContext(r.ctx, `
		DELETE FROM board_column
		WHERE id = $1
	`, id)
	if err != nil {
		return fmt.Errorf("failed to delete board column: %v", err)
	}

	return nil
}
