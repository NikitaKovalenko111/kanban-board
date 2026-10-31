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
	return &HTTP{
		handlers:     handlers,
		accessSecret: accessSecret,
		logger:       logger,
	}
}

func (h *HTTP) Start(mux *http.ServeMux) {
	systemRouter := http.NewServeMux()
	h.handlers.System.Start(systemRouter)
	mux.Handle("/system/", http.StripPrefix("/system", systemRouter))

	authRouter := http.NewServeMux()
	h.handlers.Auth.Start(authRouter)
	mux.Handle("/auth/", http.StripPrefix("/auth", authRouter))
	mux.Handle("/api/v1/auth/", http.StripPrefix("/api/v1/auth", authRouter))

	apiRouter := http.NewServeMux()
	h.handlers.Profile.Start(apiRouter)
	h.handlers.Project.Start(apiRouter)
	h.handlers.Board.Start(apiRouter)
	h.handlers.Task.Start(apiRouter)
	h.handlers.Comment.Start(apiRouter)
	h.handlers.Attachment.Start(apiRouter)
	h.handlers.Schedule.Start(apiRouter)
	h.handlers.Event.Start(apiRouter)
	protectedAPI := middleware.Authorization(h.accessSecret, h.logger)(apiRouter)
	mux.Handle("/api/v1/", http.StripPrefix("/api/v1", protectedAPI))
}
