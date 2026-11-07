package attachment_service

import (
	"context"
	"net/url"
	"strings"
	"unicode/utf8"

	"kanban-board/internal/models"
	servicecommon "kanban-board/internal/services/common"
	attachment_repo "kanban-board/internal/storage/repositories/attachment"
)

type Service struct {
	repo *attachment_repo.Repository
}

func (s *Service) AddLinkAttachment(ctx context.Context, userID, taskID, kind, title, linkURL string) (*models.Attachment, error) {
	if err := servicecommon.ValidateUUID(userID, taskID); err != nil {
		return nil, err
	}
	if kind != "SOURCE" && kind != "RESULT" {
		return nil, servicecommon.InvalidInput("kind must be SOURCE or RESULT")
	}
	title = strings.TrimSpace(title)
	if title == "" || utf8.RuneCountInString(title) > 200 {
		return nil, servicecommon.InvalidInput("title must contain from 1 to 200 characters")
	}
	parsedURL, err := url.ParseRequestURI(linkURL)
	if err != nil || parsedURL.Host == "" || (parsedURL.Scheme != "http" && parsedURL.Scheme != "https") || len(linkURL) > 2000 {
		return nil, servicecommon.InvalidInput("url must be a valid HTTP or HTTPS URL")
	}
	return s.repo.AddLinkAttachment(ctx, userID, taskID, kind, title, linkURL)
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
