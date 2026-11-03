package profile_service

import (
	"context"
	"crypto/rand"
	"encoding/base64"
	"regexp"
	"strings"

	"kanban-board/internal/models"
	auth_service "kanban-board/internal/services/auth"
	servicecommon "kanban-board/internal/services/common"
	profile_repo "kanban-board/internal/storage/repositories/profile"
)

var usernamePattern = regexp.MustCompile(`^[A-Za-z0-9._-]{3,32}$`)

type Profile = models.Profile

type Service struct {
	repo *profile_repo.Repository
}

func Init(repo *profile_repo.Repository) *Service {
	return &Service{repo: repo}
}

func (s *Service) GetProfile(ctx context.Context, userID string) (*Profile, error) {
	if err := servicecommon.ValidateUUID(userID); err != nil {
		return nil, err
	}

	return s.repo.GetProfile(ctx, userID)
}

func (s *Service) ListUsers(ctx context.Context, actorID, search string) ([]Profile, error) {
	if err := servicecommon.ValidateUUID(actorID); err != nil {
		return nil, err
	}

	return s.repo.ListUsers(ctx, actorID, strings.TrimSpace(search))
}

func (s *Service) UpdateProfile(ctx context.Context, userID string, fullName, studentGroup, schedulePrivacy *string) (*Profile, error) {
	if err := servicecommon.ValidateUUID(userID); err != nil {
		return nil, err
	}
	if fullName != nil && strings.TrimSpace(*fullName) == "" {
		return nil, servicecommon.InvalidInput("fullName is required")
	}
	if schedulePrivacy != nil {
		switch *schedulePrivacy {
		case "NOTHING", "BUSY", "TITLE", "TITLE_AND_DESCRIPTION":
		default:
			return nil, servicecommon.InvalidInput("schedulePrivacy has an invalid value")
		}
	}
	if err := s.repo.UpdateProfile(ctx, userID, fullName, studentGroup, schedulePrivacy); err != nil {
		return nil, err
	}

	return s.repo.GetProfile(ctx, userID)
}

func (s *Service) SetPhoto(ctx context.Context, userID, imageURL string) (*Profile, error) {
	if err := servicecommon.ValidateUUID(userID); err != nil {
		return nil, err
	}
	if err := s.repo.SetPhoto(ctx, userID, imageURL); err != nil {
		return nil, err
	}

	return s.repo.GetProfile(ctx, userID)
}

func (s *Service) CreateUser(ctx context.Context, actorID, fullName, username, temporaryPassword, studentGroup, role string) (*Profile, error) {
	if err := servicecommon.ValidateUUID(actorID); err != nil {
		return nil, err
	}
	if strings.TrimSpace(fullName) == "" {
		return nil, servicecommon.InvalidInput("fullName is required")
	}
	if !usernamePattern.MatchString(username) {
		return nil, servicecommon.InvalidInput("username must contain 3 to 32 Latin letters, digits, dots, underscores, or hyphens")
	}
	if len(temporaryPassword) < 8 {
		return nil, servicecommon.InvalidInput("temporaryPassword must contain at least 8 characters")
	}
	if role != "CAPTAIN" && role != "MEMBER" {
		return nil, servicecommon.InvalidInput("role must be CAPTAIN or MEMBER")
	}
	if strings.TrimSpace(studentGroup) == "" {
		return nil, servicecommon.InvalidInput("studentGroup is required")
	}

	passwordHash, err := auth_service.HashPassword(temporaryPassword)
	if err != nil {
		return nil, err
	}
	userID, err := s.repo.CreateUser(ctx, actorID, fullName, strings.ToLower(username), passwordHash, studentGroup, role)
	if err != nil {
		return nil, servicecommon.DatabaseError(err)
	}

	return s.repo.GetProfile(ctx, userID)
}

func (s *Service) SetStatus(ctx context.Context, actorID, userID, status string) (*Profile, error) {
	if err := servicecommon.ValidateUUID(actorID, userID); err != nil {
		return nil, err
	}
	if status != "ACTIVE" && status != "BLOCKED" {
		return nil, servicecommon.InvalidInput("status must be ACTIVE or BLOCKED")
	}
	if err := s.repo.SetStatus(ctx, actorID, userID, status); err != nil {
		return nil, err
	}

	return s.repo.GetProfile(ctx, userID)
}

func (s *Service) SetRole(ctx context.Context, actorID, userID, role string) (*Profile, error) {
	if err := servicecommon.ValidateUUID(actorID, userID); err != nil {
		return nil, err
	}
	if role != "CAPTAIN" && role != "MEMBER" {
		return nil, servicecommon.InvalidInput("role must be CAPTAIN or MEMBER")
	}
	if err := s.repo.SetRole(ctx, actorID, userID, role); err != nil {
		return nil, err
	}

	return s.repo.GetProfile(ctx, userID)
}

func (s *Service) ResetPassword(ctx context.Context, actorID, userID string) (string, error) {
	if err := servicecommon.ValidateUUID(actorID, userID); err != nil {
		return "", err
	}
	temporaryPassword, err := generateTemporaryPassword()
	if err != nil {
		return "", err
	}
	passwordHash, err := auth_service.HashPassword(temporaryPassword)
	if err != nil {
		return "", err
	}
	if err := s.repo.ResetPassword(ctx, actorID, userID, passwordHash); err != nil {
		return "", err
	}

	return temporaryPassword, nil
}

func (s *Service) DeleteUser(ctx context.Context, actorID, userID string) error {
	if err := servicecommon.ValidateUUID(actorID, userID); err != nil {
		return err
	}

	return s.repo.DeleteUser(ctx, actorID, userID)
}

func generateTemporaryPassword() (string, error) {
	data := make([]byte, 12)
	if _, err := rand.Read(data); err != nil {
		return "", err
	}

	return base64.RawURLEncoding.EncodeToString(data), nil
}
