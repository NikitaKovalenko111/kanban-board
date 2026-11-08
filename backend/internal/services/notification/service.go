package notification_service

import (
	"context"
	"fmt"
	"strings"
	"sync"

	"kanban-board/internal/models"
	"kanban-board/internal/push"
	servicecommon "kanban-board/internal/services/common"
	notification_repo "kanban-board/internal/storage/repositories/notification"
)

type Service struct {
	repo        *notification_repo.Repository
	push        push.Sender
	subscribers map[string]map[chan struct{}]struct{}
	mu          sync.Mutex
}

func Init(repo *notification_repo.Repository, senders ...push.Sender) *Service {
	service := &Service{
		repo:        repo,
		subscribers: make(map[string]map[chan struct{}]struct{}),
	}
	if len(senders) > 0 {
		service.push = senders[0]
	}
	return service
}

func (s *Service) ListAfter(ctx context.Context, userID, notificationID string, limit int) ([]models.Notification, error) {
	if err := servicecommon.ValidateUUID(userID, notificationID); err != nil {
		return nil, err
	}
	if limit <= 0 || limit > 100 {
		limit = 100
	}

	return s.repo.ListAfter(ctx, userID, notificationID, limit)
}

func (s *Service) Subscribe(userID string) (<-chan struct{}, func(), error) {
	if err := servicecommon.ValidateUUID(userID); err != nil {
		return nil, nil, err
	}

	events := make(chan struct{}, 1)
	s.mu.Lock()
	if s.subscribers[userID] == nil {
		s.subscribers[userID] = make(map[chan struct{}]struct{})
	}
	s.subscribers[userID][events] = struct{}{}
	s.mu.Unlock()

	unsubscribe := func() {
		s.mu.Lock()
		delete(s.subscribers[userID], events)
		if len(s.subscribers[userID]) == 0 {
			delete(s.subscribers, userID)
		}
		s.mu.Unlock()
	}

	return events, unsubscribe, nil
}

func (s *Service) Listen(ctx context.Context) error {
	return s.repo.Listen(ctx, func(userID, _ string) {
		s.publish(userID)
	})
}

func (s *Service) publish(userID string) {
	s.mu.Lock()
	defer s.mu.Unlock()

	for subscriber := range s.subscribers[userID] {
		select {
		case subscriber <- struct{}{}:
		default:
		}
	}
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

func (s *Service) Read(ctx context.Context, userID, notificationID string) error {
	if err := servicecommon.ValidateUUID(userID, notificationID); err != nil {
		return err
	}
	return s.repo.Read(ctx, userID, notificationID)
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
		if item.LessonID != nil {
			data["lessonId"] = *item.LessonID
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
