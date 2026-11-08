package project_service

import (
	"context"
	"net/url"
	"regexp"
	"strings"
	"unicode/utf8"

	"kanban-board/internal/models"
	servicecommon "kanban-board/internal/services/common"
	project_repo "kanban-board/internal/storage/repositories/project"
)

var projectColorPattern = regexp.MustCompile(`^#[0-9A-Fa-f]{6}$`)

type Project = models.Project
type ProjectMember = models.ProjectMember

type Service struct {
	repo *project_repo.Repository
}

func Init(repo *project_repo.Repository) *Service {
	return &Service{repo: repo}
}

func (s *Service) ListProjects(ctx context.Context, userID string) ([]Project, error) {
	if err := servicecommon.ValidateUUID(userID); err != nil {
		return nil, err
	}
	return s.repo.ListProjects(ctx, userID)
}

func (s *Service) GetProject(ctx context.Context, userID, projectID string) (*Project, error) {
	if err := servicecommon.ValidateUUID(userID, projectID); err != nil {
		return nil, err
	}
	return s.repo.GetProject(ctx, userID, projectID)
}

func (s *Service) CreateProject(ctx context.Context, userID, name, description, color string, members []ProjectMember) (*Project, error) {
	if err := servicecommon.ValidateUUID(userID); err != nil {
		return nil, err
	}
	if err := validateProjectName(name); err != nil {
		return nil, err
	}
	if err := validateMembers(members); err != nil {
		return nil, err
	}
	if !projectColorPattern.MatchString(color) {
		return nil, servicecommon.InvalidInput("color must use #RRGGBB format")
	}
	projectID, err := s.repo.CreateProject(ctx, userID, strings.TrimSpace(name), description, color, members)
	if err != nil {
		return nil, servicecommon.DatabaseError(err)
	}
	return s.repo.GetProject(ctx, userID, projectID)
}

func (s *Service) UpdateProject(ctx context.Context, userID, projectID string, name, description *string, members *[]ProjectMember, color *string) (*Project, error) {
	if err := servicecommon.ValidateUUID(userID, projectID); err != nil {
		return nil, err
	}
	if name != nil {
		if err := validateProjectName(*name); err != nil {
			return nil, err
		}
	}
	if members != nil {
		if err := validateMembers(*members); err != nil {
			return nil, err
		}
	}
	if color != nil && !projectColorPattern.MatchString(*color) {
		return nil, servicecommon.InvalidInput("color must use #RRGGBB format")
	}
	if err := s.repo.UpdateProject(ctx, userID, projectID, name, description, members, color); err != nil {
		return nil, servicecommon.DatabaseError(err)
	}
	return s.repo.GetProject(ctx, userID, projectID)
}

func (s *Service) DeleteProject(ctx context.Context, userID, projectID string) ([]string, error) {
	if err := servicecommon.ValidateUUID(userID, projectID); err != nil {
		return nil, err
	}
	return s.repo.DeleteProject(ctx, userID, projectID)
}

func (s *Service) AddLinkSource(ctx context.Context, userID, projectID, title, sourceURL string) (*models.ProjectSource, error) {
	if err := servicecommon.ValidateUUID(userID, projectID); err != nil {
		return nil, err
	}
	if strings.TrimSpace(title) == "" {
		return nil, servicecommon.InvalidInput("title is required")
	}
	parsedURL, err := url.ParseRequestURI(sourceURL)
	if err != nil || parsedURL.Host == "" || (parsedURL.Scheme != "http" && parsedURL.Scheme != "https") || len(sourceURL) > 2000 {
		return nil, servicecommon.InvalidInput("url must be a valid HTTP or HTTPS URL")
	}
	sourceID, err := s.repo.AddLinkSource(ctx, userID, projectID, title, sourceURL)
	if err != nil {
		return nil, err
	}
	project, err := s.repo.GetProject(ctx, userID, projectID)
	if err != nil {
		return nil, err
	}
	for index := range project.Sources {
		if project.Sources[index].ID == sourceID {
			return &project.Sources[index], nil
		}
	}
	return nil, servicecommon.ErrNotFound
}

func (s *Service) AddFileSource(ctx context.Context, userID, projectID, name, storageKey, mimeType string, size int64) (*models.ProjectSource, error) {
	if err := servicecommon.ValidateUUID(userID, projectID); err != nil {
		return nil, err
	}
	sourceID, err := s.repo.AddFileSource(ctx, userID, projectID, name, storageKey, mimeType, size)
	if err != nil {
		return nil, err
	}
	project, err := s.repo.GetProject(ctx, userID, projectID)
	if err != nil {
		return nil, err
	}
	for index := range project.Sources {
		if project.Sources[index].ID == sourceID {
			return &project.Sources[index], nil
		}
	}
	return nil, servicecommon.ErrNotFound
}

func (s *Service) DeleteSource(ctx context.Context, userID, projectID, sourceID string) (string, error) {
	if err := servicecommon.ValidateUUID(userID, projectID, sourceID); err != nil {
		return "", err
	}
	return s.repo.DeleteSource(ctx, userID, projectID, sourceID)
}

func (s *Service) GetSourceFile(ctx context.Context, userID, sourceID string) (string, string, string, error) {
	if err := servicecommon.ValidateUUID(userID, sourceID); err != nil {
		return "", "", "", err
	}
	return s.repo.GetSourceFile(ctx, userID, sourceID)
}

func validateProjectName(name string) error {
	length := utf8.RuneCountInString(strings.TrimSpace(name))
	if length < 1 || length > 80 {
		return servicecommon.InvalidInput("name must contain from 1 to 80 characters")
	}
	return nil
}

func validateMembers(members []ProjectMember) error {
	seen := make(map[string]struct{}, len(members))
	for _, member := range members {
		if err := servicecommon.ValidateUUID(member.UserID); err != nil {
			return err
		}
		if _, exists := seen[member.UserID]; exists {
			return servicecommon.InvalidInput("members must not contain duplicates")
		}
		seen[member.UserID] = struct{}{}
		if strings.TrimSpace(member.Role) == "" || utf8.RuneCountInString(member.Role) > 60 {
			return servicecommon.InvalidInput("member role must contain from 1 to 60 characters")
		}
	}
	return nil
}
