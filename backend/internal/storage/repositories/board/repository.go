package board

import (
	"context"
	"database/sql"
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

func (r *BoardRepo) CreateBoard(name string) (string, error) {
	var id string
	err := r.sql.QueryRow(`
		INSERT INTO board (name)
		VALUES ($1)
		RETURNING id
	`, name).Scan(&id)

	if err != nil {
		return "", err
	}

	return id, nil
}

func (r *BoardRepo) GetBoardByID(id string) (*models.Board, error) {
	var board models.Board
	err := r.sql.QueryRowContext(r.ctx, `
		SELECT id, name 
		FROM board 
		WHERE id = $1
	`, id).Scan(&board.ID, &board.Name)

	if err != nil {
		return nil, err
	}

	return &board, nil
}

func (r *BoardRepo) GetBoardByName(name string) (*models.Board, error) {
	var board models.Board
	err := r.sql.QueryRowContext(r.ctx, `
		SELECT id, name
		FROM board
		WHERE name = $1
	`, name).Scan(&board.ID, &board.Name)

	if err != nil {
		return nil, err
	}
	return &board, nil
}
