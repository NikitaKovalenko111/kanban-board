package profile_service

import (
	"context"

	workspace_repo "kanban-board/internal/storage/repositories/workspace"
)

type Profile = workspace_repo.Profile
type Service struct{ repo *workspace_repo.Repository }

func Init(repo *workspace_repo.Repository) *Service { return &Service{repo: repo} }
func (s *Service) GetProfile(ctx context.Context, id string) (*Profile, error) {
	return s.repo.GetProfile(ctx, id)
}
func (s *Service) UpdateProfile(ctx context.Context, id, name, position, group, image string) error {
	return s.repo.UpdateProfile(ctx, id, name, position, group, image)
}
