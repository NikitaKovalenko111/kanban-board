package repositories

import (
	"context"
	"kanban-board/internal/storage"
	project_repo "kanban-board/internal/storage/repositories/project"
	team_repo "kanban-board/internal/storage/repositories/team"
	team_member_repo "kanban-board/internal/storage/repositories/team_member"
	users_repository "kanban-board/internal/storage/repositories/user"
)

type Repositories struct {
	UsersRepo      *users_repository.UsersRepo
	TeamRepo       *team_repo.TeamRepo
	ProjectRepo    *project_repo.ProjectRepo
	TeamMemberRepo *team_member_repo.TeamMemberRepo
}

func Init(storage *storage.Storage, ctx context.Context) *Repositories {
	return &Repositories{
		UsersRepo:      users_repository.Init(storage.Db, ctx),
		TeamRepo:       team_repo.Init(storage.Db, ctx),
		ProjectRepo:    project_repo.Init(storage.Db, ctx),
		TeamMemberRepo: team_member_repo.Init(storage.Db, ctx),
	}
}
