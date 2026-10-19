package handlers

import (
	"kanban-board/internal/services"
	system_handler "kanban-board/internal/transport/http/handlers/system"
	"log/slog"
)

type Handlers struct {
	System *system_handler.SystemHandler
}

func Init(services *services.Services, logger *slog.Logger) *Handlers {
	return &Handlers{
		System: system_handler.Init(),
	}
}
