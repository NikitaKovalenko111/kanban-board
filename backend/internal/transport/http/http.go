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
	apiRouter := http.NewServeMux()

	docsRouter := http.NewServeMux()
	apiRouter.Handle("/docs/", http.StripPrefix("/docs", docsRouter))
	docs.Start(docsRouter)

	systemRouter := http.NewServeMux()
	h.handlers.System.Start(systemRouter)
	apiRouter.Handle("/system/", http.StripPrefix("/system", systemRouter))

	authRouter := http.NewServeMux()

	h.handlers.Auth.StartPublic(authRouter)

	authProtectedRouter := http.NewServeMux()
	h.handlers.Auth.StartProtected(authProtectedRouter)

	authRouter.Handle("/", middleware.Authorization(h.accessSecret, h.logger, h.activeChecker)(authProtectedRouter))

	apiRouter.Handle("/auth/", http.StripPrefix("/auth", authRouter))

	privateRouter := http.NewServeMux()
	h.handlers.Profile.Start(privateRouter)
	h.handlers.Project.Start(privateRouter)
	h.handlers.Board.Start(privateRouter)
	h.handlers.Task.Start(privateRouter)
	h.handlers.Comment.Start(privateRouter)
	h.handlers.Attachment.Start(privateRouter)
	h.handlers.Schedule.Start(privateRouter)
	h.handlers.Event.Start(privateRouter)
	h.handlers.Notification.Start(privateRouter)

	protectedAPI := middleware.Authorization(h.accessSecret, h.logger, h.activeChecker)(privateRouter)
	apiRouter.Handle("/", protectedAPI)

	mux.Handle("/api/v1/", http.StripPrefix("/api/v1", apiRouter))
}
