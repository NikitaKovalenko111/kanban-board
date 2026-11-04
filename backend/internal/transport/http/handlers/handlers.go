package handlers

import (
	"kanban-board/internal/objectstorage"
	"kanban-board/internal/services"
	attachment_handler "kanban-board/internal/transport/http/handlers/attachment"
	auth_handler "kanban-board/internal/transport/http/handlers/auth"
	board_handler "kanban-board/internal/transport/http/handlers/board"
	comment_handler "kanban-board/internal/transport/http/handlers/comment"
	event_handler "kanban-board/internal/transport/http/handlers/event"
	notification_handler "kanban-board/internal/transport/http/handlers/notification"
	profile_handler "kanban-board/internal/transport/http/handlers/profile"
	project_handler "kanban-board/internal/transport/http/handlers/project"
	schedule_handler "kanban-board/internal/transport/http/handlers/schedule"
	system_handler "kanban-board/internal/transport/http/handlers/system"
	task_handler "kanban-board/internal/transport/http/handlers/task"
)

type Handlers struct {
	System       *system_handler.SystemHandler
	Auth         *auth_handler.AuthHandler
	Profile      *profile_handler.Handler
	Project      *project_handler.Handler
	Board        *board_handler.Handler
	Task         *task_handler.Handler
	Comment      *comment_handler.Handler
	Attachment   *attachment_handler.Handler
	Schedule     *schedule_handler.Handler
	Event        *event_handler.Handler
	Notification *notification_handler.Handler
}

func Init(services *services.Services, objects ...objectstorage.Storage) *Handlers {
	var storage objectstorage.Storage
	if len(objects) > 0 {
		storage = objects[0]
	}
	return &Handlers{
		System:       system_handler.Init(),
		Auth:         auth_handler.Init(services.Auth, services.Profile),
		Profile:      profile_handler.Init(services.Profile, storage),
		Project:      project_handler.Init(services.Project, storage),
		Board:        board_handler.Init(services.Board),
		Task:         task_handler.Init(services.Task, storage),
		Comment:      comment_handler.Init(services.Comment),
		Attachment:   attachment_handler.Init(services.Attachment, services.Project, storage),
		Schedule:     schedule_handler.Init(services.Schedule),
		Event:        event_handler.Init(services.Event),
		Notification: notification_handler.Init(services.Notification),
	}
}
