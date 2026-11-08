package board_service

import (
	"context"

	"kanban-board/internal/models"
	servicecommon "kanban-board/internal/services/common"
	board_repo "kanban-board/internal/storage/repositories/board"
)

type Service struct {
	repo *board_repo.Repository
}

func Init(repo *board_repo.Repository) *Service {
	return &Service{repo: repo}
}

func (s *Service) ListColumns(ctx context.Context, userID, projectID string) ([]models.BoardColumn, error) {
	if err := servicecommon.ValidateUUID(userID, projectID); err != nil {
		return nil, err
	}
	return s.repo.ListColumns(ctx, userID, projectID)
}
