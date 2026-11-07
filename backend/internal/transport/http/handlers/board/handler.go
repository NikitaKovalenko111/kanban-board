package board_handler

import (
	"net/http"

	board_service "kanban-board/internal/services/board"
	handlercommon "kanban-board/internal/transport/http/handlers/common"
	"kanban-board/pkg"
)

type Handler struct {
	service *board_service.Service
}

func Init(service *board_service.Service) *Handler {
	return &Handler{service: service}
}

func (h *Handler) Start(mux *http.ServeMux) {
	mux.HandleFunc("GET /projects/{projectId}/columns", h.listColumns)
}

func (h *Handler) listColumns(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	columns, err := h.service.ListColumns(r.Context(), userID, r.PathValue("projectId"))
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusOK, columns)
}
