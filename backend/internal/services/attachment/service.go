package attachment_service

import (
	"context"

	"kanban-board/internal/models"
	servicecommon "kanban-board/internal/services/common"
	attachment_repo "kanban-board/internal/storage/repositories/attachment"
)

type Service struct {
	repo *attachment_repo.Repository
}

func Init(repo *attachment_repo.Repository) *Service {
	return &Service{repo: repo}
}

func (s *Service) AddAttachment(ctx context.Context, userID, taskID, kind, name, storageKey, mimeType string, size int64) (*models.Attachment, error) {
	if err := servicecommon.ValidateUUID(userID, taskID); err != nil {
		return nil, err
	}
	if err := validateAttachment(kind, name, storageKey, mimeType, size); err != nil {
		return nil, err
	}
	return s.repo.AddAttachment(ctx, userID, taskID, kind, name, storageKey, mimeType, size)
}

func (s *Service) GetAttachment(ctx context.Context, userID, attachmentID string) (*models.Attachment, error) {
	if err := servicecommon.ValidateUUID(userID, attachmentID); err != nil {
		return nil, err
	}
	return s.repo.GetAttachment(ctx, userID, attachmentID)
}

func (s *Service) DeleteAttachment(ctx context.Context, userID, taskID, attachmentID string) (*models.Attachment, error) {
	if err := servicecommon.ValidateUUID(userID, taskID, attachmentID); err != nil {
		return nil, err
	}
	return s.repo.DeleteAttachment(ctx, userID, taskID, attachmentID)
}
