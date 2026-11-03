package notification_service

import (
	"context"
	"strings"

	"kanban-board/internal/models"
	servicecommon "kanban-board/internal/services/common"
	notification_repo "kanban-board/internal/storage/repositories/notification"
)

type Service struct {
	repo *notification_repo.Repository
}

func Init(repo *notification_repo.Repository) *Service {
	return &Service{repo: repo}
}

func (s *Service) List(ctx context.Context, userID string, limit int) ([]models.Notification, error) {
	if err := servicecommon.ValidateUUID(userID); err != nil {
		return nil, err
	}
	if limit <= 0 || limit > 100 {
		limit = 100
	}
	return s.repo.List(ctx, userID, limit)
}

func (s *Service) ReadAll(ctx context.Context, userID string) error {
	if err := servicecommon.ValidateUUID(userID); err != nil {
		return err
	}
	return s.repo.ReadAll(ctx, userID)
}

func (s *Service) AddPushToken(ctx context.Context, userID, token, platform string) error {
	if err := servicecommon.ValidateUUID(userID); err != nil {
		return err
	}
	if strings.TrimSpace(token) == "" || platform != "android" {
		return servicecommon.InvalidInput("token and android platform are required")
	}
	return s.repo.AddPushToken(ctx, userID, token, platform)
}

func (s *Service) DeletePushToken(ctx context.Context, userID, token string) error {
	if err := servicecommon.ValidateUUID(userID); err != nil {
		return err
	}
	return s.repo.DeletePushToken(ctx, userID, token)
}

func (s *Service) CreateUpcomingEventNotifications(ctx context.Context) (int64, error) {
	return s.repo.CreateUpcomingEventNotifications(ctx)
}
