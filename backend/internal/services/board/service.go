package board_service

import (
	"context"
	"regexp"
	"strings"

	"kanban-board/internal/models"
	servicecommon "kanban-board/internal/services/common"
	board_repo "kanban-board/internal/storage/repositories/board"
)

var colorPattern = regexp.MustCompile(`^#[0-9A-Fa-f]{6}$`)

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

func (s *Service) ReplaceColumns(ctx context.Context, userID, projectID string, columns []models.BoardColumn) ([]models.BoardColumn, error) {
	if err := servicecommon.ValidateUUID(userID, projectID); err != nil {
		return nil, err
	}
	if len(columns) < 4 {
		return nil, servicecommon.InvalidInput("at least four columns are required")
	}
	for index := range columns {
		if columns[index].ID != "" {
			if err := servicecommon.ValidateUUID(columns[index].ID); err != nil {
				return nil, err
			}
		}
		if strings.TrimSpace(columns[index].Title) == "" {
			return nil, servicecommon.InvalidInput("column title is required")
		}
		if !colorPattern.MatchString(columns[index].Color) {
			return nil, servicecommon.InvalidInput("column color must use #RRGGBB format")
		}
	}
	return s.repo.ReplaceColumns(ctx, userID, projectID, columns)
}
