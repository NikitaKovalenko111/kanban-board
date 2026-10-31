package project_service

import (
	"context"

	workspace_repo "kanban-board/internal/storage/repositories/workspace"
)

type Project = workspace_repo.Project
type ProjectMember = workspace_repo.ProjectMember
type Service struct{ repo *workspace_repo.Repository }

func Init(repo *workspace_repo.Repository) *Service { return &Service{repo: repo} }
func (s *Service) ListProjects(ctx context.Context, id string) ([]Project, error) {
	return s.repo.ListProjects(ctx, id)
}
func (s *Service) GetProject(ctx context.Context, uid, pid string) (*Project, error) {
	return s.repo.GetProject(ctx, uid, pid)
}
func (s *Service) CreateProject(ctx context.Context, uid, name, description, policy string) (string, error) {
	return s.repo.CreateProject(ctx, uid, name, description, policy)
}
func (s *Service) UpdateProject(ctx context.Context, uid, pid, name, description, policy string, archived bool) error {
	return s.repo.UpdateProject(ctx, uid, pid, name, description, policy, archived)
}
func (s *Service) DeleteProject(ctx context.Context, uid, pid string) error {
	return s.repo.DeleteProject(ctx, uid, pid)
}
func (s *Service) ListProjectMembers(ctx context.Context, uid, pid string) ([]ProjectMember, error) {
	return s.repo.ListProjectMembers(ctx, uid, pid)
}
func (s *Service) AddProjectMember(ctx context.Context, actor, pid, uid, role string) error {
	return s.repo.AddProjectMember(ctx, actor, pid, uid, role)
}
func (s *Service) UpdateProjectMember(ctx context.Context, actor, pid, uid, role, status string) error {
	return s.repo.UpdateProjectMember(ctx, actor, pid, uid, role, status)
}
func (s *Service) RemoveProjectMember(ctx context.Context, actor, pid, uid string) error {
	return s.repo.RemoveProjectMember(ctx, actor, pid, uid)
}
func (s *Service) JoinProject(ctx context.Context, uid, pid string) error {
	return s.repo.JoinProject(ctx, uid, pid)
}
