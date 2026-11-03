package task_service

import (
	"context"
	"time"

	"kanban-board/internal/models"
	servicecommon "kanban-board/internal/services/common"
	task_repo "kanban-board/internal/storage/repositories/task"
)

type Task = models.Task
type History = models.TaskHistory
type Service struct {
	repo *task_repo.Repository
}

func Init(repo *task_repo.Repository) *Service {
	return &Service{
		repo: repo,
	}
}

func (s *Service) ListTasks(ctx context.Context, uid, pid string) ([]Task, error) {
	if err := servicecommon.ValidateUUID(uid, pid); err != nil {
		return nil, err
	}

	return s.repo.ListTasks(ctx, uid, pid)
}

func (s *Service) GetTask(ctx context.Context, uid, tid string) (*Task, error) {
	if err := servicecommon.ValidateUUID(uid, tid); err != nil {
		return nil, err
	}

	return s.repo.GetTask(ctx, uid, tid)
}

func (s *Service) CreateTask(ctx context.Context, uid, pid, title, description, priority string, due *time.Time, assignees []string) (string, error) {
	if err := servicecommon.ValidateUUID(uid, pid); err != nil {
		return "", err
	}
	if err := validateTask(title, priority, assignees); err != nil {
		return "", err
	}

	return s.repo.CreateTask(ctx, uid, pid, title, description, priority, due, assignees)
}

func (s *Service) UpdateTask(ctx context.Context, uid, tid, title, description, priority string, due *time.Time) error {
	if err := servicecommon.ValidateUUID(uid, tid); err != nil {
		return err
	}
	if err := validateTask(title, priority, nil); err != nil {
		return err
	}

	return s.repo.UpdateTask(ctx, uid, tid, title, description, priority, due)
}

func (s *Service) DeleteTask(ctx context.Context, uid, tid string) error {
	if err := servicecommon.ValidateUUID(uid, tid); err != nil {
		return err
	}

	return s.repo.DeleteTask(ctx, uid, tid)
}

func (s *Service) TakeTask(ctx context.Context, uid, tid string) error {
	if err := servicecommon.ValidateUUID(uid, tid); err != nil {
		return err
	}

	return s.repo.TakeTask(ctx, uid, tid)
}

func (s *Service) MoveTask(ctx context.Context, uid, tid, status, comment string) error {
	if err := servicecommon.ValidateUUID(uid, tid); err != nil {
		return err
	}
	if err := validateTaskStatus(status); err != nil {
		return err
	}

	return s.repo.MoveTask(ctx, uid, tid, status, comment)
}

func (s *Service) AddTaskAssignee(ctx context.Context, actor, tid, uid string) error {
	if err := servicecommon.ValidateUUID(actor, tid, uid); err != nil {
		return err
	}

	return s.repo.AddTaskAssignee(ctx, actor, tid, uid)
}

func (s *Service) RemoveTaskAssignee(ctx context.Context, actor, tid, uid string) error {
	if err := servicecommon.ValidateUUID(actor, tid, uid); err != nil {
		return err
	}

	return s.repo.RemoveTaskAssignee(ctx, actor, tid, uid)
}

func (s *Service) TaskHistory(ctx context.Context, uid, tid string) ([]History, error) {
	if err := servicecommon.ValidateUUID(uid, tid); err != nil {
		return nil, err
	}

	return s.repo.TaskHistory(ctx, uid, tid)
}
