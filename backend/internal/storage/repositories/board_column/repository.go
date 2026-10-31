package board_column_repo

import (
	"context"
	"database/sql"
	"fmt"
	"kanban-board/internal/models"
)

type BoardColumnRepo struct{ db *sql.DB }

func Init(db *sql.DB) *BoardColumnRepo { return &BoardColumnRepo{db: db} }
func (r *BoardColumnRepo) CreateBoardColumn(ctx context.Context, boardID, code, name string, position int) (string, error) {
	var id string
	err := r.db.QueryRowContext(ctx, `INSERT INTO board_columns(column_id,board_id,code,name,position) VALUES(gen_random_uuid(),$1,$2,$3,$4) RETURNING column_id`, boardID, code, name, position).Scan(&id)
	if err != nil {
		return "", fmt.Errorf("failed to create board column: %w", err)
	}
	return id, nil
}
func (r *BoardColumnRepo) GetBoardColumnByID(ctx context.Context, id string) (*models.BoardColumn, error) {
	var c models.BoardColumn
	err := r.db.QueryRowContext(ctx, `SELECT column_id,board_id,code,name,position FROM board_columns WHERE column_id=$1`, id).Scan(&c.ColumnID, &c.BoardID, &c.Code, &c.Name, &c.Position)
	if err != nil {
		return nil, fmt.Errorf("failed to get board column: %w", err)
	}
	return &c, nil
}
func (r *BoardColumnRepo) GetBoardColumnsByBoardID(ctx context.Context, boardID string) ([]models.BoardColumn, error) {
	rows, err := r.db.QueryContext(ctx, `SELECT column_id,board_id,code,name,position FROM board_columns WHERE board_id=$1 ORDER BY position`, boardID)
	if err != nil {
		return nil, fmt.Errorf("failed to get board columns: %w", err)
	}
	defer rows.Close()
	items := make([]models.BoardColumn, 0)
	for rows.Next() {
		var c models.BoardColumn
		if err := rows.Scan(&c.ColumnID, &c.BoardID, &c.Code, &c.Name, &c.Position); err != nil {
			return nil, err
		}
		items = append(items, c)
	}
	return items, rows.Err()
}
func (r *BoardColumnRepo) UpdateBoardColumn(ctx context.Context, id, boardID, code, name string, position int) error {
	_, err := r.db.ExecContext(ctx, `UPDATE board_columns SET board_id=$1,code=$2,name=$3,position=$4 WHERE column_id=$5`, boardID, code, name, position, id)
	if err != nil {
		return fmt.Errorf("failed to update board column: %w", err)
	}
	return nil
}
func (r *BoardColumnRepo) DeleteBoardColumn(ctx context.Context, id string) error {
	_, err := r.db.ExecContext(ctx, `DELETE FROM board_columns WHERE column_id=$1`, id)
	if err != nil {
		return fmt.Errorf("failed to delete board column: %w", err)
	}
	return nil
}
