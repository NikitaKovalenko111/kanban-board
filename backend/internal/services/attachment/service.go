package attachment_service

import (
	"context"

	"kanban-board/internal/models"
	attachment_repo "kanban-board/internal/storage/repositories/attachment"
)

type Attachment = models.Attachment
type Service struct {
	repo *attachment_repo.Repository
}

func Init(repo *attachment_repo.Repository) *Service {
	return &Service{
		repo: repo,
	}
}

func (s *Service) ListAttachments(ctx context.Context, uid, tid string) ([]Attachment, error) {
	return s.repo.ListAttachments(ctx, uid, tid)
}

func (s *Service) AddAttachment(ctx context.Context, uid, tid, kind, name, key, mime string, size int64) (string, error) {
	return s.repo.AddAttachment(ctx, uid, tid, kind, name, key, mime, size)
}

func (s *Service) GetAttachment(ctx context.Context, uid, aid string) (*Attachment, error) {
	return s.repo.GetAttachment(ctx, uid, aid)
}

func (s *Service) DeleteAttachment(ctx context.Context, uid, aid string) (*Attachment, error) {
	return s.repo.DeleteAttachment(ctx, uid, aid)
}
