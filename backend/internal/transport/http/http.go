package transport

import (
	"kanban-board/internal/transport/http/handlers"
	"net/http"
)

type HTTP struct {
	handlers *handlers.Handlers
}

func Init(handlers *handlers.Handlers) *HTTP {
	return &HTTP{
		handlers: handlers,
	}
}

func (h *HTTP) Start(mux *http.ServeMux) {
	systemRouter := http.NewServeMux()
	mux.Handle(
		"/system/",
		http.StripPrefix("/system", systemRouter),
	)
	h.handlers.System.Start(systemRouter)

	authRouter := http.NewServeMux()
	mux.Handle(
		"/auth/",
		http.StripPrefix("/auth", authRouter),
	)
	h.handlers.Auth.Start(authRouter)
}
