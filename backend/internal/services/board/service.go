package board_service

import (
	"context"

	workspace_repo "kanban-board/internal/storage/repositories/workspace"
)

type Board = workspace_repo.Board
type Service struct{ repo *workspace_repo.Repository }

func Init(repo *workspace_repo.Repository) *Service { return &Service{repo: repo} }
func (s *Service) GetBoard(ctx context.Context, uid, pid string) (*Board, error) {
	return s.repo.GetBoard(ctx, uid, pid)
}
func (s *Service) CreateColumn(ctx context.Context, uid, pid, code, name string, position int) (string, error) {
	return s.repo.CreateColumn(ctx, uid, pid, code, name, position)
}
func (s *Service) UpdateColumn(ctx context.Context, uid, cid, code, name string, position int) error {
	return s.repo.UpdateColumn(ctx, uid, cid, code, name, position)
}
func (s *Service) DeleteColumn(ctx context.Context, uid, cid string) error {
	return s.repo.DeleteColumn(ctx, uid, cid)
}
