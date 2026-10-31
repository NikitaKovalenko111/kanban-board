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
	attachment_repo "kanban-board/internal/storage/repositories/attachment"
	board_repo "kanban-board/internal/storage/repositories/board"
	comment_repo "kanban-board/internal/storage/repositories/comment"
	event_repo "kanban-board/internal/storage/repositories/event"
	profile_repo "kanban-board/internal/storage/repositories/profile"
	project_repo "kanban-board/internal/storage/repositories/project"
	schedule_repo "kanban-board/internal/storage/repositories/schedule"
	session_repo "kanban-board/internal/storage/repositories/session"
	task_repo "kanban-board/internal/storage/repositories/task"
	users_repo "kanban-board/internal/storage/repositories/user"
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

	return &Services{
		Auth:       auth_service.Init(sessionRepo, userRepo, jwtConfig),
		Profile:    profile_service.Init(profile_repo.Init(appStorage.Db)),
		Project:    project_service.Init(project_repo.Init(appStorage.Db)),
		Board:      board_service.Init(board_repo.Init(appStorage.Db)),
		Task:       task_service.Init(task_repo.Init(appStorage.Db)),
		Comment:    comment_service.Init(comment_repo.Init(appStorage.Db)),
		Attachment: attachment_service.Init(attachment_repo.Init(appStorage.Db)),
		Schedule:   schedule_service.Init(schedule_repo.Init(appStorage.Db)),
		Event:      event_service.Init(event_repo.Init(appStorage.Db)),
	}
}
