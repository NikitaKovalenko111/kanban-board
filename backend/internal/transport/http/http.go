package transport

import (
	"log/slog"
	"net/http"

	"kanban-board/internal/middleware"
	"kanban-board/internal/transport/http/handlers"
)

type HTTP struct {
	handlers     *handlers.Handlers
	accessSecret string
	logger       *slog.Logger
}

func Init(handlers *handlers.Handlers, accessSecret string, logger *slog.Logger) *HTTP {
	return &HTTP{handlers: handlers, accessSecret: accessSecret, logger: logger}
}

func (h *HTTP) Start(mux *http.ServeMux) {
	systemRouter := http.NewServeMux()
	h.handlers.System.Start(systemRouter)
	mux.Handle("/system/", http.StripPrefix("/system", systemRouter))

	authRouter := http.NewServeMux()
	h.handlers.Auth.Start(authRouter)
	mux.Handle("/auth/", http.StripPrefix("/auth", authRouter))
	mux.Handle("/api/v1/auth/", http.StripPrefix("/api/v1/auth", authRouter))

	workspaceRouter := http.NewServeMux()
	h.handlers.Profile.Start(workspaceRouter)
	h.handlers.Project.Start(workspaceRouter)
	h.handlers.Board.Start(workspaceRouter)
	h.handlers.Task.Start(workspaceRouter)
	h.handlers.Comment.Start(workspaceRouter)
	h.handlers.Attachment.Start(workspaceRouter)
	h.handlers.Schedule.Start(workspaceRouter)
	h.handlers.Event.Start(workspaceRouter)
	protectedWorkspace := middleware.Authorization(h.accessSecret, h.logger)(workspaceRouter)
	mux.Handle("/api/v1/", http.StripPrefix("/api/v1", protectedWorkspace))
}
