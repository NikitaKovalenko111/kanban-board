package task_service

import (
	"context"
	"strings"
	"time"

	"kanban-board/internal/models"
	servicecommon "kanban-board/internal/services/common"
	task_repo "kanban-board/internal/storage/repositories/task"
)

type Task = models.Task

type Service struct {
	repo *task_repo.Repository
}

func Init(repo *task_repo.Repository) *Service {
	return &Service{repo: repo}
}

func (s *Service) ListTasks(ctx context.Context, userID, projectID string) ([]Task, error) {
	if err := servicecommon.ValidateUUID(userID, projectID); err != nil {
		return nil, err
	}
	return s.repo.ListTasks(ctx, userID, projectID)
}

func (s *Service) GetTask(ctx context.Context, userID, taskID string) (*Task, error) {
	if err := servicecommon.ValidateUUID(userID, taskID); err != nil {
		return nil, err
	}
	return s.repo.GetTask(ctx, userID, taskID)
}

func (s *Service) CreateTask(
	ctx context.Context,
	userID, projectID, title, description, priority string,
	assigneeID *string,
	dueAt *time.Time,
	columnID *string,
) (*Task, error) {
	if err := servicecommon.ValidateUUID(userID, projectID); err != nil {
		return nil, err
	}
	if assigneeID != nil {
		if err := servicecommon.ValidateUUID(*assigneeID); err != nil {
			return nil, err
		}
	}
	if columnID != nil {
		if err := servicecommon.ValidateUUID(*columnID); err != nil {
			return nil, err
		}
	}
	if err := validateTaskData(title, priority); err != nil {
		return nil, err
	}
	taskID, err := s.repo.CreateTask(ctx, userID, projectID, title, description, priority, assigneeID, dueAt, columnID)
	if err != nil {
		return nil, err
	}
	return s.repo.GetTask(ctx, userID, taskID)
}

func (s *Service) UpdateTask(
	ctx context.Context,
	userID, taskID string,
	title, description, priority *string,
	dueAt *time.Time,
	dueAtProvided bool,
	assigneeID *string,
	assigneeProvided bool,
) (*Task, error) {
	if err := servicecommon.ValidateUUID(userID, taskID); err != nil {
		return nil, err
	}
	if title != nil && strings.TrimSpace(*title) == "" {
		return nil, servicecommon.InvalidInput("title is required")
	}
	if priority != nil && !validPriority(*priority) {
		return nil, servicecommon.InvalidInput("priority must be LOW, NORMAL, or HIGH")
	}
	if assigneeID != nil {
		if err := servicecommon.ValidateUUID(*assigneeID); err != nil {
			return nil, err
		}
	}
	if err := s.repo.UpdateTask(ctx, userID, taskID, title, description, priority, dueAt, dueAtProvided, assigneeID, assigneeProvided); err != nil {
		return nil, err
	}
	return s.repo.GetTask(ctx, userID, taskID)
}

func (s *Service) DeleteTask(ctx context.Context, userID, taskID string) ([]string, error) {
	if err := servicecommon.ValidateUUID(userID, taskID); err != nil {
		return nil, err
	}
	return s.repo.DeleteTask(ctx, userID, taskID)
}

func (s *Service) TakeTask(ctx context.Context, userID, taskID string) (*Task, error) {
	if err := servicecommon.ValidateUUID(userID, taskID); err != nil {
		return nil, err
	}
	if err := s.repo.TakeTask(ctx, userID, taskID); err != nil {
		return nil, err
	}
	return s.repo.GetTask(ctx, userID, taskID)
}

func (s *Service) AssignToMe(ctx context.Context, userID, taskID string) (*Task, error) {
	if err := servicecommon.ValidateUUID(userID, taskID); err != nil {
		return nil, err
	}
	if err := s.repo.AssignToMe(ctx, userID, taskID); err != nil {
		return nil, err
	}
	return s.repo.GetTask(ctx, userID, taskID)
}

func (s *Service) MoveTask(ctx context.Context, userID, taskID, columnID, comment string) (*Task, error) {
	if err := servicecommon.ValidateUUID(userID, taskID, columnID); err != nil {
		return nil, err
	}
	if err := s.repo.MoveTask(ctx, userID, taskID, columnID, strings.TrimSpace(comment)); err != nil {
		return nil, err
	}
	return s.repo.GetTask(ctx, userID, taskID)
}

func (s *Service) ListCompleted(ctx context.Context, userID string, from, to *time.Time, projectID, assigneeID, search string) ([]Task, error) {
	if err := servicecommon.ValidateUUID(userID); err != nil {
		return nil, err
	}
	if projectID != "" {
		if err := servicecommon.ValidateUUID(projectID); err != nil {
			return nil, err
		}
	}
	if assigneeID != "" {
		if err := servicecommon.ValidateUUID(assigneeID); err != nil {
			return nil, err
		}
	}
	return s.repo.ListCompleted(ctx, userID, from, to, projectID, assigneeID, strings.TrimSpace(search))
}

func (s *Service) ListAssigned(ctx context.Context, userID, requestedUserID string) ([]Task, error) {
	if requestedUserID == "" {
		requestedUserID = userID
	}
	if err := servicecommon.ValidateUUID(userID, requestedUserID); err != nil {
		return nil, err
	}
	return s.repo.ListAssigned(ctx, userID, requestedUserID)
}

func validateTaskData(title, priority string) error {
	if strings.TrimSpace(title) == "" {
		return servicecommon.InvalidInput("title is required")
	}
	if !validPriority(priority) {
		return servicecommon.InvalidInput("priority must be LOW, NORMAL, or HIGH")
	}
	return nil
}

func validPriority(priority string) bool {
	return priority == "LOW" || priority == "NORMAL" || priority == "HIGH"
}
