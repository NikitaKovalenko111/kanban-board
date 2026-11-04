package handlers

import (
	"kanban-board/internal/services"
	auth_handler "kanban-board/internal/transport/http/handlers/auth"
	system_handler "kanban-board/internal/transport/http/handlers/system"
)

type Handlers struct {
	System *system_handler.SystemHandler
	Auth   *auth_handler.AuthHandler
}

func Init(services *services.Services) *Handlers {
	return &Handlers{
		System: system_handler.Init(),
		Auth:   auth_handler.Init(services.Auth),
	}
}
