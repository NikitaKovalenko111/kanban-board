package comment_service

import (
	"context"

	"kanban-board/internal/models"
	comment_repo "kanban-board/internal/storage/repositories/comment"
)

type Comment = models.Comment
type Service struct {
	repo *comment_repo.Repository
}

func Init(repo *comment_repo.Repository) *Service {
	return &Service{
		repo: repo,
	}
}

func (s *Service) ListComments(ctx context.Context, uid, tid string) ([]Comment, error) {
	return s.repo.ListComments(ctx, uid, tid)
}

func (s *Service) AddComment(ctx context.Context, uid, tid, text string) (string, error) {
	return s.repo.AddComment(ctx, uid, tid, text)
}

func (s *Service) UpdateComment(ctx context.Context, uid, cid, text string) error {
	return s.repo.UpdateComment(ctx, uid, cid, text)
}

func (s *Service) DeleteComment(ctx context.Context, uid, cid string) error {
	return s.repo.DeleteComment(ctx, uid, cid)
}
