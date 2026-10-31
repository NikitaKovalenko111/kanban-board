package handlers

import (
	"kanban-board/internal/services"
	attachment_handler "kanban-board/internal/transport/http/handlers/attachment"
	auth_handler "kanban-board/internal/transport/http/handlers/auth"
	board_handler "kanban-board/internal/transport/http/handlers/board"
	comment_handler "kanban-board/internal/transport/http/handlers/comment"
	event_handler "kanban-board/internal/transport/http/handlers/event"
	profile_handler "kanban-board/internal/transport/http/handlers/profile"
	project_handler "kanban-board/internal/transport/http/handlers/project"
	schedule_handler "kanban-board/internal/transport/http/handlers/schedule"
	system_handler "kanban-board/internal/transport/http/handlers/system"
	task_handler "kanban-board/internal/transport/http/handlers/task"
)

type Handlers struct {
	System     *system_handler.SystemHandler
	Auth       *auth_handler.AuthHandler
	Profile    *profile_handler.Handler
	Project    *project_handler.Handler
	Board      *board_handler.Handler
	Task       *task_handler.Handler
	Comment    *comment_handler.Handler
	Attachment *attachment_handler.Handler
	Schedule   *schedule_handler.Handler
	Event      *event_handler.Handler
}

func Init(services *services.Services) *Handlers {
	return &Handlers{
		System:     system_handler.Init(),
		Auth:       auth_handler.Init(services.Auth),
		Profile:    profile_handler.Init(services.Profile),
		Project:    project_handler.Init(services.Project),
		Board:      board_handler.Init(services.Board),
		Task:       task_handler.Init(services.Task),
		Comment:    comment_handler.Init(services.Comment),
		Attachment: attachment_handler.Init(services.Attachment),
		Schedule:   schedule_handler.Init(services.Schedule),
		Event:      event_handler.Init(services.Event),
	}
}
