package transport

import (
	"log/slog"
	"net/http"

	"kanban-board/docs"
	"kanban-board/internal/middleware"
	"kanban-board/internal/transport/http/handlers"
)

type HTTP struct {
	handlers      *handlers.Handlers
	accessSecret  string
	logger        *slog.Logger
	activeChecker middleware.UserActiveChecker
}

func Init(handlers *handlers.Handlers, accessSecret string, logger *slog.Logger, activeCheckers ...middleware.UserActiveChecker) *HTTP {
	httpTransport := &HTTP{
		handlers:     handlers,
		accessSecret: accessSecret,
		logger:       logger,
	}
	if len(activeCheckers) > 0 {
		httpTransport.activeChecker = activeCheckers[0]
	}

	return httpTransport
}

func (h *HTTP) Start(mux *http.ServeMux) {
	docs.Start(mux)

	systemRouter := http.NewServeMux()
	h.handlers.System.Start(systemRouter)
	mux.Handle("/api/v1/system/", http.StripPrefix("/api/v1/system", systemRouter))

	authRouter := http.NewServeMux()
	h.handlers.Auth.StartPublic(authRouter)
	mux.Handle("/auth/", http.StripPrefix("/auth", authRouter))
	mux.Handle("/api/v1/auth/", http.StripPrefix("/api/v1/auth", authRouter))

	apiRouter := http.NewServeMux()
	h.handlers.Auth.StartProtected(apiRouter)
	h.handlers.Profile.Start(apiRouter)
	h.handlers.Project.Start(apiRouter)
	h.handlers.Board.Start(apiRouter)
	h.handlers.Task.Start(apiRouter)
	h.handlers.Comment.Start(apiRouter)
	h.handlers.Attachment.Start(apiRouter)
	h.handlers.Schedule.Start(apiRouter)
	h.handlers.Event.Start(apiRouter)
	h.handlers.Notification.Start(apiRouter)
	protectedAPI := middleware.Authorization(h.accessSecret, h.logger, h.activeChecker)(apiRouter)
	mux.Handle("/api/v1/", http.StripPrefix("/api/v1", protectedAPI))
}
