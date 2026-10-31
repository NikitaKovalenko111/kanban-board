package system_handler

import (
	"kanban-board/pkg"
	"net/http"
)

type SystemHandler struct {
}

func Init() *SystemHandler {
	return new(SystemHandler)
}

func (h *SystemHandler) Start(mux *http.ServeMux) {
	mux.HandleFunc("GET /health", h.Health)
}

func (h *SystemHandler) Health(w http.ResponseWriter, r *http.Request) {
	pkg.WriteEmpty(w, http.StatusOK)
}
