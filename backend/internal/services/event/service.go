package event_service

import (
	"context"
	"regexp"
	"strings"
	"time"
	"unicode/utf8"

	"kanban-board/internal/models"
	servicecommon "kanban-board/internal/services/common"
	event_repo "kanban-board/internal/storage/repositories/event"
)

var colorPattern = regexp.MustCompile(`^#[0-9A-Fa-f]{6}$`)

type Service struct {
	repo *event_repo.Repository
}

func Init(repo *event_repo.Repository) *Service {
	return &Service{repo: repo}
}

func (s *Service) ListEventTypes(ctx context.Context, userID string) ([]models.EventType, error) {
	if err := servicecommon.ValidateUUID(userID); err != nil {
		return nil, err
	}
	return s.repo.ListEventTypes(ctx, userID)
}

func (s *Service) CreateEventType(ctx context.Context, userID, title, color string, reminders []int) (*models.EventType, error) {
	if err := servicecommon.ValidateUUID(userID); err != nil {
		return nil, err
	}
	if err := validateType(title, color, reminders); err != nil {
		return nil, err
	}
	id, err := s.repo.CreateEventType(ctx, userID, title, color, reminders)
	if err != nil {
		return nil, err
	}
	items, err := s.repo.ListEventTypes(ctx, userID)
	if err != nil {
		return nil, err
	}
	for index := range items {
		if items[index].ID == id {
			return &items[index], nil
		}
	}
	return nil, servicecommon.ErrNotFound
}

func (s *Service) UpdateEventType(ctx context.Context, userID, eventTypeID string, title, color *string, reminders *[]int) (*models.EventType, error) {
	if err := servicecommon.ValidateUUID(userID, eventTypeID); err != nil {
		return nil, err
	}
	if title != nil && strings.TrimSpace(*title) == "" {
		return nil, servicecommon.InvalidInput("title is required")
	}
	if color != nil && !colorPattern.MatchString(*color) {
		return nil, servicecommon.InvalidInput("color must use #RRGGBB format")
	}
	if reminders != nil {
		if err := validateReminders(*reminders); err != nil {
			return nil, err
		}
	}
	if err := s.repo.UpdateEventType(ctx, userID, eventTypeID, title, color, reminders); err != nil {
		return nil, err
	}
	items, err := s.repo.ListEventTypes(ctx, userID)
	if err != nil {
		return nil, err
	}
	for index := range items {
		if items[index].ID == eventTypeID {
			return &items[index], nil
		}
	}
	return nil, servicecommon.ErrNotFound
}

func (s *Service) DeleteEventType(ctx context.Context, userID, eventTypeID string) error {
	if err := servicecommon.ValidateUUID(userID, eventTypeID); err != nil {
		return err
	}
	return s.repo.DeleteEventType(ctx, userID, eventTypeID)
}

func (s *Service) ListEvents(ctx context.Context, userID, personID string, groups []string, from, to time.Time) ([]models.Event, error) {
	if personID == "" {
		personID = userID
	}
	if err := servicecommon.ValidateUUID(userID, personID); err != nil {
		return nil, err
	}
	if !to.After(from) {
		return nil, servicecommon.InvalidInput("to must be after from")
	}
	return s.repo.ListEvents(ctx, userID, personID, groups, from, to)
}

func (s *Service) GetEvent(ctx context.Context, userID, eventID string) (*models.Event, error) {
	if err := servicecommon.ValidateUUID(userID, eventID); err != nil {
		return nil, err
	}
	return s.repo.GetEvent(ctx, userID, eventID)
}

func (s *Service) CreateEvent(
	ctx context.Context,
	userID, title, description, location string,
	startsAt time.Time,
	endsAt *time.Time,
	visibility, group string,
	typeID *string,
	reminders []int,
) (*models.Event, error) {
	if err := servicecommon.ValidateUUID(userID); err != nil {
		return nil, err
	}
	if typeID != nil {
		if err := servicecommon.ValidateUUID(*typeID); err != nil {
			return nil, err
		}
	}
	if err := validateEventData(title, location, startsAt, endsAt, visibility, group); err != nil {
		return nil, err
	}
	if err := validateReminders(reminders); err != nil {
		return nil, err
	}
	eventID, err := s.repo.CreateEvent(ctx, userID, title, description, location, startsAt, endsAt, visibility, group, typeID, reminders)
	if err != nil {
		return nil, err
	}
	return s.repo.GetEvent(ctx, userID, eventID)
}

func (s *Service) UpdateEvent(
	ctx context.Context,
	userID, eventID string,
	title, description, location *string,
	startsAt *time.Time,
	endsAt *time.Time,
	endsAtProvided bool,
	visibility, group *string,
	typeID *string,
	typeProvided bool,
) (*models.Event, error) {
	if err := servicecommon.ValidateUUID(userID, eventID); err != nil {
		return nil, err
	}
	if typeID != nil {
		if err := servicecommon.ValidateUUID(*typeID); err != nil {
			return nil, err
		}
	}
	if title != nil && (strings.TrimSpace(*title) == "" || utf8.RuneCountInString(*title) > 200) {
		return nil, servicecommon.InvalidInput("title must contain from 1 to 200 characters")
	}
	if location != nil && utf8.RuneCountInString(*location) > 300 {
		return nil, servicecommon.InvalidInput("location must not exceed 300 characters")
	}
	if visibility != nil {
		if *visibility != "TEAM" && *visibility != "GROUP" && *visibility != "PERSONAL" {
			return nil, servicecommon.InvalidInput("visibility must be TEAM, GROUP, or PERSONAL")
		}
		if *visibility == "GROUP" && (group == nil || strings.TrimSpace(*group) == "") {
			return nil, servicecommon.InvalidInput("group is required when visibility changes to GROUP")
		}
	}
	if err := s.repo.UpdateEvent(ctx, userID, eventID, title, description, location, startsAt, endsAt, endsAtProvided, visibility, group, typeID, typeProvided); err != nil {
		return nil, err
	}
	return s.repo.GetEvent(ctx, userID, eventID)
}

func (s *Service) DeleteEvent(ctx context.Context, userID, eventID string) error {
	if err := servicecommon.ValidateUUID(userID, eventID); err != nil {
		return err
	}
	return s.repo.DeleteEvent(ctx, userID, eventID)
}

func (s *Service) GetReminders(ctx context.Context, userID string) (map[string][]int, error) {
	if err := servicecommon.ValidateUUID(userID); err != nil {
		return nil, err
	}
	return s.repo.GetReminders(ctx, userID)
}

func (s *Service) PutReminders(ctx context.Context, userID, itemKey string, reminders []int) error {
	if err := servicecommon.ValidateUUID(userID); err != nil {
		return err
	}
	if !strings.HasPrefix(itemKey, "event:") && !strings.HasPrefix(itemKey, "lesson:") {
		return servicecommon.InvalidInput("itemKey must start with event: or lesson:")
	}
	if err := validateReminders(reminders); err != nil {
		return err
	}
	return s.repo.PutReminders(ctx, userID, itemKey, reminders)
}

func validateType(title, color string, reminders []int) error {
	if strings.TrimSpace(title) == "" {
		return servicecommon.InvalidInput("title is required")
	}
	if !colorPattern.MatchString(color) {
		return servicecommon.InvalidInput("color must use #RRGGBB format")
	}
	return validateReminders(reminders)
}

func validateEventData(title, location string, startsAt time.Time, endsAt *time.Time, visibility, group string) error {
	if strings.TrimSpace(title) == "" || utf8.RuneCountInString(title) > 200 {
		return servicecommon.InvalidInput("title must contain from 1 to 200 characters")
	}
	if utf8.RuneCountInString(location) > 300 {
		return servicecommon.InvalidInput("location must not exceed 300 characters")
	}
	if startsAt.IsZero() {
		return servicecommon.InvalidInput("startsAt is required")
	}
	if endsAt != nil {
		if !endsAt.After(startsAt) {
			return servicecommon.InvalidInput("endsAt must be after startsAt")
		}
		if endsAt.Sub(startsAt) > 14*24*time.Hour {
			return servicecommon.InvalidInput("event duration must not exceed 14 days")
		}
	}
	if visibility != "TEAM" && visibility != "GROUP" && visibility != "PERSONAL" {
		return servicecommon.InvalidInput("visibility must be TEAM, GROUP, or PERSONAL")
	}
	if visibility == "GROUP" && strings.TrimSpace(group) == "" {
		return servicecommon.InvalidInput("group is required for GROUP visibility")
	}
	return nil
}

func validateReminders(reminders []int) error {
	seen := make(map[int]struct{}, len(reminders))
	for _, minute := range reminders {
		if minute < 1 || minute > 40320 {
			return servicecommon.InvalidInput("reminder must be between 1 and 40320 minutes")
		}
		if _, exists := seen[minute]; exists {
			return servicecommon.InvalidInput("reminders must not contain duplicates")
		}
		seen[minute] = struct{}{}
	}
	return nil
}
