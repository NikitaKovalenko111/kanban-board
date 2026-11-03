package attachment_service

import (
	"context"

	"kanban-board/internal/models"
	servicecommon "kanban-board/internal/services/common"
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
	if err := servicecommon.ValidateUUID(uid, tid); err != nil {
		return nil, err
	}

	return s.repo.ListAttachments(ctx, uid, tid)
}

func (s *Service) AddAttachment(ctx context.Context, uid, tid, kind, name, key, mime string, size int64) (string, error) {
	if err := servicecommon.ValidateUUID(uid, tid); err != nil {
		return "", err
	}
	if err := validateAttachment(kind, name, key, mime, size); err != nil {
		return "", err
	}

	return s.repo.AddAttachment(ctx, uid, tid, kind, name, key, mime, size)
}

func (s *Service) GetAttachment(ctx context.Context, uid, aid string) (*Attachment, error) {
	if err := servicecommon.ValidateUUID(uid, aid); err != nil {
		return nil, err
	}

	return s.repo.GetAttachment(ctx, uid, aid)
}

func (s *Service) DeleteAttachment(ctx context.Context, uid, aid string) (*Attachment, error) {
	if err := servicecommon.ValidateUUID(uid, aid); err != nil {
		return nil, err
	}

	return s.repo.DeleteAttachment(ctx, uid, aid)
}
