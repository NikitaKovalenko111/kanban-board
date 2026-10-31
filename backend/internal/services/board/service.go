package board_service

import (
	"context"

	"kanban-board/internal/models"
	servicecommon "kanban-board/internal/services/common"
	board_repo "kanban-board/internal/storage/repositories/board"
)

type Board = models.Board
type Service struct {
	repo *board_repo.Repository
}

func Init(repo *board_repo.Repository) *Service {
	return &Service{
		repo: repo,
	}
}

// переписать uid, pid, code, cid в полные названия типа userID (везде)
func (s *Service) GetBoard(ctx context.Context, uid, pid string) (*Board, error) {
	if err := servicecommon.ValidateUUID(uid, pid); err != nil {
		return nil, err
	}

	board, err := s.repo.GetBoard(ctx, uid, pid)
	if err != nil {
		return nil, err
	}

	return board, nil
}

func (s *Service) CreateColumn(ctx context.Context, uid, pid, code, name string, position int) (string, error) {
	if err := VallidateBoard(name, position, code, uid, pid); err != nil {
		return "", err
	}

	id, err := s.repo.CreateColumn(ctx, uid, pid, code, name, position)
	if err != nil {
		return "", err
	}

	return id, nil
}

func (s *Service) UpdateColumn(ctx context.Context, uid, cid, code, name string, position int) error {
	if err := VallidateBoard(name, position, code, uid, cid); err != nil {
		return err
	}

	err := s.repo.UpdateColumn(ctx, uid, cid, code, name, position)
	if err != nil {
		return err
	}

	return nil
}

func (s *Service) DeleteColumn(ctx context.Context, uid, cid string) error {
	if err := servicecommon.ValidateUUID(uid, cid); err != nil {
		return err
	}

	err := s.repo.DeleteColumn(ctx, uid, cid)
	if err != nil {
		return err
	}

	return nil
}
