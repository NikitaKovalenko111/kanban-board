package board_handler

import (
	"net/http"

	"kanban-board/internal/models"
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
	mux.HandleFunc("PUT /projects/{projectId}/columns", h.replaceColumns)
}

type columnRequest struct {
	ID    *string `json:"id"`
	Title string  `json:"title"`
	Color string  `json:"color"`
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

func (h *Handler) replaceColumns(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var request []columnRequest
	if !handlercommon.Decode(w, r, &request) {
		return
	}
	columns := make([]models.BoardColumn, len(request))
	for index, item := range request {
		if item.ID != nil {
			columns[index].ID = *item.ID
		}
		columns[index].Title = item.Title
		columns[index].Color = item.Color
	}
	columns, err := h.service.ReplaceColumns(r.Context(), userID, r.PathValue("projectId"), columns)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusOK, columns)
}
