package services

import (
	"kanban-board/internal/config"
	attachment_service "kanban-board/internal/services/attachment"
	auth_service "kanban-board/internal/services/auth"
	board_service "kanban-board/internal/services/board"
	comment_service "kanban-board/internal/services/comment"
	event_service "kanban-board/internal/services/event"
	profile_service "kanban-board/internal/services/profile"
	project_service "kanban-board/internal/services/project"
	schedule_service "kanban-board/internal/services/schedule"
	task_service "kanban-board/internal/services/task"
	"kanban-board/internal/storage"
	session_repo "kanban-board/internal/storage/repositories/session"
	users_repo "kanban-board/internal/storage/repositories/user"
	workspace_repo "kanban-board/internal/storage/repositories/workspace"
)

type Services struct {
	Auth       *auth_service.AuthService
	Profile    *profile_service.Service
	Project    *project_service.Service
	Board      *board_service.Service
	Task       *task_service.Service
	Comment    *comment_service.Service
	Attachment *attachment_service.Service
	Schedule   *schedule_service.Service
	Event      *event_service.Service
}

func Init(appStorage *storage.Storage, jwtConfig config.JWT) *Services {
	userRepo := users_repo.Init(appStorage.Db)
	sessionRepo := session_repo.Init(appStorage.Db)
	workspaceRepo := workspace_repo.Init(appStorage.Db)

	return &Services{
		Auth:       auth_service.Init(sessionRepo, userRepo, jwtConfig),
		Profile:    profile_service.Init(workspaceRepo),
		Project:    project_service.Init(workspaceRepo),
		Board:      board_service.Init(workspaceRepo),
		Task:       task_service.Init(workspaceRepo),
		Comment:    comment_service.Init(workspaceRepo),
		Attachment: attachment_service.Init(workspaceRepo),
		Schedule:   schedule_service.Init(workspaceRepo),
		Event:      event_service.Init(workspaceRepo),
	}
}
