package event_service

import (
	"context"
	"time"

	"kanban-board/internal/models"
	event_repo "kanban-board/internal/storage/repositories/event"
)

type EventType = models.EventType
type Event = models.Event
type Service struct {
	repo *event_repo.Repository
}

func Init(repo *event_repo.Repository) *Service {
	return &Service{
		repo: repo,
	}
}

func (s *Service) ListEventTypes(ctx context.Context, uid string) ([]EventType, error) {
	return s.repo.ListEventTypes(ctx, uid)
}

func (s *Service) GetEventType(ctx context.Context, uid, eid string) (*EventType, error) {
	return s.repo.GetEventType(ctx, uid, eid)
}

func (s *Service) CreateEventType(ctx context.Context, uid, title, color, scope string, pid *string, minutes []int) (string, error) {
	return s.repo.CreateEventType(ctx, uid, title, color, scope, pid, minutes)
}

func (s *Service) UpdateEventType(ctx context.Context, uid, eid, title, color string, minutes []int) error {
	return s.repo.UpdateEventType(ctx, uid, eid, title, color, minutes)
}

func (s *Service) DeleteEventType(ctx context.Context, uid, eid string) error {
	return s.repo.DeleteEventType(ctx, uid, eid)
}

func (s *Service) ListEvents(ctx context.Context, uid string, pid *string, group string, from, to time.Time) ([]Event, error) {
	return s.repo.ListEvents(ctx, uid, pid, group, from, to)
}

func (s *Service) GetEvent(ctx context.Context, uid, eid string) (*Event, error) {
	return s.repo.GetEvent(ctx, uid, eid)
}

func (s *Service) CreateEvent(ctx context.Context, uid, typeID, title, description, location, visibility string, pid *string, group string, starts, ends time.Time, minutes []int) (string, error) {
	return s.repo.CreateEvent(ctx, uid, typeID, title, description, location, visibility, pid, group, starts, ends, minutes)
}

func (s *Service) UpdateEvent(ctx context.Context, uid, eid, title, description, location string, starts, ends time.Time, minutes []int) error {
	return s.repo.UpdateEvent(ctx, uid, eid, title, description, location, starts, ends, minutes)
}

func (s *Service) DeleteEvent(ctx context.Context, uid, eid string) error {
	return s.repo.DeleteEvent(ctx, uid, eid)
}
