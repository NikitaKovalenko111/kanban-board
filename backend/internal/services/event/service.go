package event_service

import (
	"context"
	"time"

	"kanban-board/internal/models"
	servicecommon "kanban-board/internal/services/common"
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
	if err := servicecommon.ValidateUUID(uid); err != nil {
		return nil, err
	}

	return s.repo.ListEventTypes(ctx, uid)
}

func (s *Service) GetEventType(ctx context.Context, uid, eid string) (*EventType, error) {
	if err := servicecommon.ValidateUUID(uid, eid); err != nil {
		return nil, err
	}

	return s.repo.GetEventType(ctx, uid, eid)
}

func (s *Service) CreateEventType(ctx context.Context, uid, title, color, scope string, pid *string, minutes []int) (string, error) {
	if err := validateEventType(uid, nil, title, color, scope, pid, minutes); err != nil {
		return "", err
	}

	return s.repo.CreateEventType(ctx, uid, title, color, scope, pid, minutes)
}

func (s *Service) UpdateEventType(ctx context.Context, uid, eid, title, color string, minutes []int) error {
	if err := validateEventType(uid, &eid, title, color, "", nil, minutes); err != nil {
		return err
	}

	return s.repo.UpdateEventType(ctx, uid, eid, title, color, minutes)
}

func (s *Service) DeleteEventType(ctx context.Context, uid, eid string) error {
	if err := servicecommon.ValidateUUID(uid, eid); err != nil {
		return err
	}
	return s.repo.DeleteEventType(ctx, uid, eid)
}

func (s *Service) ListEvents(ctx context.Context, uid string, pid *string, group string, from, to time.Time) ([]Event, error) {
	if err := servicecommon.ValidateUUID(uid); err != nil {
		return nil, err
	}
	if err := servicecommon.ValidateOptionalUUID(pid); err != nil {
		return nil, err
	}
	if err := servicecommon.ValidateTimeRange(from, to); err != nil {
		return nil, err
	}

	return s.repo.ListEvents(ctx, uid, pid, group, from, to)
}

func (s *Service) GetEvent(ctx context.Context, uid, eid string) (*Event, error) {
	if err := servicecommon.ValidateUUID(uid, eid); err != nil {
		return nil, err
	}

	return s.repo.GetEvent(ctx, uid, eid)
}

func (s *Service) CreateEvent(ctx context.Context, uid, typeID, title, description, location, visibility string, pid *string, group string, starts, ends time.Time, minutes []int) (string, error) {
	if err := validateEvent(uid, nil, typeID, title, description, location, visibility, pid, group, starts, ends, minutes); err != nil {
		return "", err
	}

	return s.repo.CreateEvent(ctx, uid, typeID, title, description, location, visibility, pid, group, starts, ends, minutes)
}

func (s *Service) UpdateEvent(ctx context.Context, uid, eid, title, description, location string, starts, ends time.Time, minutes []int) error {
	if err := validateEvent(uid, &eid, "", title, description, location, "", nil, "", starts, ends, minutes); err != nil {
		return err
	}

	return s.repo.UpdateEvent(ctx, uid, eid, title, description, location, starts, ends, minutes)
}

func (s *Service) DeleteEvent(ctx context.Context, uid, eid string) error {
	if err := servicecommon.ValidateUUID(uid, eid); err != nil {
		return err
	}

	return s.repo.DeleteEvent(ctx, uid, eid)
}
