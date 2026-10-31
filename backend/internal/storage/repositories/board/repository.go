package board

import (
	"context"
	"database/sql"
	"fmt"
	"kanban-board/internal/models"
)

type BoardRepo struct {
	sql *sql.DB
}

func Init(sql *sql.DB) *BoardRepo {
	return &BoardRepo{
		sql: sql,
	}
}

func (r *BoardRepo) CreateBoard(ctx context.Context, projectID string, name string) (string, error) {
	var boardID string
	err := r.sql.QueryRowContext(ctx, `
		INSERT INTO boards (board_id, project_id, name)
		VALUES (gen_random_uuid(), $1, $2)
		RETURNING board_id
	`, projectID, name).Scan(&boardID)

	if err != nil {
		return "", fmt.Errorf("failed to create board: %v", err)
	}

	return boardID, nil
}

func (r *BoardRepo) GetBoardByID(ctx context.Context, boardID string) (*models.Board, error) {
	var board models.Board
	err := r.sql.QueryRowContext(ctx, `
		SELECT board_id, project_id, name
		FROM boards
		WHERE board_id = $1
	`, boardID).Scan(&board.BoardID, &board.ProjectID, &board.Name)

	if err != nil {
		return nil, fmt.Errorf("failed to get board by id: %v", err)
	}

	return &board, nil
}

func (r *BoardRepo) GetBoardByName(ctx context.Context, name string) (*models.Board, error) {
	var board models.Board
	err := r.sql.QueryRowContext(ctx, `
		SELECT board_id, project_id, name
		FROM boards
		WHERE name = $1
	`, name).Scan(&board.BoardID, &board.ProjectID, &board.Name)

	if err != nil {
		return nil, fmt.Errorf("failed to get board by name: %v", err)
	}
	return &board, nil
}

func (r *BoardRepo) GetBoardsByProjectID(ctx context.Context, projectID string) ([]models.Board, error) {
	rows, err := r.sql.QueryContext(ctx, `
		SELECT board_id, project_id, name
		FROM boards
		WHERE project_id = $1
		ORDER BY name
	`, projectID)
	if err != nil {
		return nil, fmt.Errorf("failed to get boards by project id: %v", err)
	}
	defer rows.Close()

	var boards []models.Board
	for rows.Next() {
		var board models.Board
		if err := rows.Scan(&board.BoardID, &board.ProjectID, &board.Name); err != nil {
			return nil, fmt.Errorf("failed to scan board: %v", err)
		}
		boards = append(boards, board)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("failed to iterate boards: %v", err)
	}

	return boards, nil
}

func (r *BoardRepo) UpdateBoard(ctx context.Context, boardID string, projectID string, name string) error {
	_, err := r.sql.ExecContext(ctx, `
		UPDATE boards
		SET project_id = $1, name = $2
		WHERE board_id = $3
	`, projectID, name, boardID)
	if err != nil {
		return fmt.Errorf("failed to update board: %v", err)
	}

	return nil
}

func (r *BoardRepo) DeleteBoard(ctx context.Context, boardID string) error {
	_, err := r.sql.ExecContext(ctx, `
		DELETE FROM boards
		WHERE board_id = $1
	`, boardID)
	if err != nil {
		return fmt.Errorf("failed to delete board: %v", err)
	}

	return nil
}
