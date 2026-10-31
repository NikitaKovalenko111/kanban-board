package board_handler

import (
	"net/http"

	board_service "kanban-board/internal/services/board"
	handlercommon "kanban-board/internal/transport/http/handlers/common"
	"kanban-board/pkg"
)

type Handler struct{ service *board_service.Service }

func Init(service *board_service.Service) *Handler { return &Handler{service: service} }

func (h *Handler) Start(mux *http.ServeMux) {
	mux.HandleFunc("GET /projects/{projectId}/board", h.getBoard)
	mux.HandleFunc("POST /projects/{projectId}/columns", h.createColumn)
	mux.HandleFunc("PATCH /columns/{columnId}", h.updateColumn)
	mux.HandleFunc("DELETE /columns/{columnId}", h.deleteColumn)
}

type columnRequest struct {
	Code     string `json:"code"`
	Name     string `json:"name"`
	Position int    `json:"position"`
}

func (h *Handler) getBoard(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	board, err := h.service.GetBoard(r.Context(), id, r.PathValue("projectId"))
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, 200, board)
}
func (h *Handler) createColumn(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var req columnRequest
	if !handlercommon.Decode(w, r, &req) {
		return
	}
	columnID, err := h.service.CreateColumn(r.Context(), id, r.PathValue("projectId"), req.Code, req.Name, req.Position)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, 201, map[string]string{"id": columnID})
}
func (h *Handler) updateColumn(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var req columnRequest
	if !handlercommon.Decode(w, r, &req) {
		return
	}
	if err := h.service.UpdateColumn(r.Context(), id, r.PathValue("columnId"), req.Code, req.Name, req.Position); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteEmpty(w, 204)
}
func (h *Handler) deleteColumn(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	if err := h.service.DeleteColumn(r.Context(), id, r.PathValue("columnId")); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteEmpty(w, 204)
}
