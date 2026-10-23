package board

import (
	"context"
	"database/sql"
	"fmt"
	"kanban-board/internal/models"
)

type BoardRepo struct {
	sql *sql.DB
	ctx context.Context
}

func Init(sql *sql.DB, ctx context.Context) *BoardRepo {
	return &BoardRepo{
		sql: sql,
		ctx: ctx,
	}
}

func (r *BoardRepo) CreateBoard(projectID string, name string) (string, error) {
	var id string
	err := r.sql.QueryRowContext(r.ctx, `
		INSERT INTO board (id, project_id, name)
		VALUES (gen_random_uuid(), $1, $2)
		RETURNING id
	`, projectID, name).Scan(&id)

	if err != nil {
		return "", fmt.Errorf("failed to create board: %v", err)
	}

	return id, nil
}

func (r *BoardRepo) GetBoardByID(id string) (*models.Board, error) {
	var board models.Board
	err := r.sql.QueryRowContext(r.ctx, `
		SELECT id, project_id, name
		FROM board 
		WHERE id = $1
	`, id).Scan(&board.ID, &board.ProjectID, &board.Name)

	if err != nil {
		return nil, fmt.Errorf("failed to get board by id: %v", err)
	}

	return &board, nil
}

func (r *BoardRepo) GetBoardByName(name string) (*models.Board, error) {
	var board models.Board
	err := r.sql.QueryRowContext(r.ctx, `
		SELECT id, project_id, name
		FROM board
		WHERE name = $1
	`, name).Scan(&board.ID, &board.ProjectID, &board.Name)

	if err != nil {
		return nil, fmt.Errorf("failed to get board by name: %v", err)
	}
	return &board, nil
}

func (r *BoardRepo) GetBoardsByProjectID(projectID string) ([]models.Board, error) {
	rows, err := r.sql.QueryContext(r.ctx, `
		SELECT id, project_id, name
		FROM board
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
		if err := rows.Scan(&board.ID, &board.ProjectID, &board.Name); err != nil {
			return nil, fmt.Errorf("failed to scan board: %v", err)
		}
		boards = append(boards, board)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("failed to iterate boards: %v", err)
	}

	return boards, nil
}

func (r *BoardRepo) UpdateBoard(id string, projectID string, name string) error {
	_, err := r.sql.ExecContext(r.ctx, `
		UPDATE board
		SET project_id = $1, name = $2
		WHERE id = $3
	`, projectID, name, id)
	if err != nil {
		return fmt.Errorf("failed to update board: %v", err)
	}

	return nil
}

func (r *BoardRepo) DeleteBoard(id string) error {
	_, err := r.sql.ExecContext(r.ctx, `
		DELETE FROM board
		WHERE id = $1
	`, id)
	if err != nil {
		return fmt.Errorf("failed to delete board: %v", err)
	}

	return nil
}
