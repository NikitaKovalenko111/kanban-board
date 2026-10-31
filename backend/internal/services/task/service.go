package task_service

import (
	"context"
	"time"

	"kanban-board/internal/models"
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
	return s.repo.ListTasks(ctx, uid, pid)
}

func (s *Service) GetTask(ctx context.Context, uid, tid string) (*Task, error) {
	return s.repo.GetTask(ctx, uid, tid)
}

func (s *Service) CreateTask(ctx context.Context, uid, pid, title, description, priority string, due *time.Time, assignees []string) (string, error) {
	return s.repo.CreateTask(ctx, uid, pid, title, description, priority, due, assignees)
}

func (s *Service) UpdateTask(ctx context.Context, uid, tid, title, description, priority string, due *time.Time) error {
	return s.repo.UpdateTask(ctx, uid, tid, title, description, priority, due)
}

func (s *Service) DeleteTask(ctx context.Context, uid, tid string) error {
	return s.repo.DeleteTask(ctx, uid, tid)
}

func (s *Service) TakeTask(ctx context.Context, uid, tid string) error {
	return s.repo.TakeTask(ctx, uid, tid)
}

func (s *Service) MoveTask(ctx context.Context, uid, tid, status, comment string) error {
	return s.repo.MoveTask(ctx, uid, tid, status, comment)
}

func (s *Service) AddTaskAssignee(ctx context.Context, actor, tid, uid string) error {
	return s.repo.AddTaskAssignee(ctx, actor, tid, uid)
}

func (s *Service) RemoveTaskAssignee(ctx context.Context, actor, tid, uid string) error {
	return s.repo.RemoveTaskAssignee(ctx, actor, tid, uid)
}

func (s *Service) TaskHistory(ctx context.Context, uid, tid string) ([]History, error) {
	return s.repo.TaskHistory(ctx, uid, tid)
}
