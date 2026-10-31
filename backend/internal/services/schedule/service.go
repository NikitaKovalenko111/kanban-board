package schedule_service

import (
	"context"
	"time"

	"kanban-board/internal/models"
	schedule_repo "kanban-board/internal/storage/repositories/schedule"
)

type Service struct {
	repo *schedule_repo.Repository
}

func Init(repo *schedule_repo.Repository) *Service {
	return &Service{
		repo: repo,
	}
}

func (s *Service) ListStudentGroups(ctx context.Context) ([]models.StudentGroup, error) {
	return s.repo.ListStudentGroups(ctx)
}

func (s *Service) ListLessons(ctx context.Context, group string, from, to time.Time) ([]models.Lesson, error) {
	return s.repo.ListLessons(ctx, group, from, to)
}
