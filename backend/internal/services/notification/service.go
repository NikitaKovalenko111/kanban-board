package notification_service

import (
	"context"
	"fmt"
	"strings"

	"kanban-board/internal/models"
	"kanban-board/internal/push"
	servicecommon "kanban-board/internal/services/common"
	notification_repo "kanban-board/internal/storage/repositories/notification"
)

type Service struct {
	repo *notification_repo.Repository
	push push.Sender
}

func Init(repo *notification_repo.Repository, senders ...push.Sender) *Service {
	service := &Service{repo: repo}
	if len(senders) > 0 {
		service.push = senders[0]
	}
	return service
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

func (s *Service) DispatchPush(ctx context.Context) (int, error) {
	if s.push == nil {
		return 0, nil
	}
	items, err := s.repo.ListPendingPush(ctx, 500)
	if err != nil {
		return 0, err
	}
	sentNotifications := make(map[string]struct{})
	for _, item := range items {
		data := map[string]string{"kind": item.Kind}
		if item.TaskID != nil {
			data["taskId"] = *item.TaskID
		}
		if item.EventID != nil {
			data["eventId"] = *item.EventID
		}
		if err := s.push.Send(ctx, push.Message{Token: item.Token, Title: item.Title, Body: item.Text, Data: data}); err != nil {
			return len(sentNotifications), fmt.Errorf("send push: %w", err)
		}
		sentNotifications[item.NotificationID] = struct{}{}
	}
	for notificationID := range sentNotifications {
		if err := s.repo.MarkPushSent(ctx, notificationID); err != nil {
			return len(sentNotifications), err
		}
	}
	return len(sentNotifications), nil
}
