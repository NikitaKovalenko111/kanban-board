package profile_service

import (
	"context"

	"kanban-board/internal/models"
	servicecommon "kanban-board/internal/services/common"
	profile_repo "kanban-board/internal/storage/repositories/profile"
)

type Profile = models.Profile
type Service struct {
	repo *profile_repo.Repository
}

func Init(repo *profile_repo.Repository) *Service {
	return &Service{
		repo: repo,
	}
}

func (s *Service) GetProfile(ctx context.Context, id string) (*Profile, error) {
	if err := servicecommon.ValidateUUID(id); err != nil {
		return nil, err
	}

	return s.repo.GetProfile(ctx, id)
}

func (s *Service) UpdateProfile(ctx context.Context, id, name, position, group, image string) error {
	if err := servicecommon.ValidateUUID(id); err != nil {
		return err
	}
	if err := validateProfile(name, image); err != nil {
		return err
	}

	return s.repo.UpdateProfile(ctx, id, name, position, group, image)
}
