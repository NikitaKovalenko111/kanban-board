package comment_handler

import (
	"net/http"

	comment_service "kanban-board/internal/services/comment"
	handlercommon "kanban-board/internal/transport/http/handlers/common"
	"kanban-board/pkg"
)

type Handler struct{ service *comment_service.Service }

func Init(service *comment_service.Service) *Handler { return &Handler{service: service} }

func (h *Handler) Start(mux *http.ServeMux) {
	mux.HandleFunc("GET /tasks/{taskId}/comments", h.listComments)
	mux.HandleFunc("POST /tasks/{taskId}/comments", h.addComment)
	mux.HandleFunc("PATCH /comments/{commentId}", h.updateComment)
	mux.HandleFunc("DELETE /comments/{commentId}", h.deleteComment)
}

type commentRequest struct {
	Text string `json:"text"`
}

func (h *Handler) listComments(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	items, err := h.service.ListComments(r.Context(), id, r.PathValue("taskId"))
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, 200, items)
}
func (h *Handler) addComment(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var req commentRequest
	if !handlercommon.Decode(w, r, &req) {
		return
	}
	commentID, err := h.service.AddComment(r.Context(), id, r.PathValue("taskId"), req.Text)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, 201, map[string]string{"id": commentID})
}
func (h *Handler) updateComment(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var req commentRequest
	if !handlercommon.Decode(w, r, &req) {
		return
	}
	if err := h.service.UpdateComment(r.Context(), id, r.PathValue("commentId"), req.Text); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteEmpty(w, 204)
}
func (h *Handler) deleteComment(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	if err := h.service.DeleteComment(r.Context(), id, r.PathValue("commentId")); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteEmpty(w, 204)
}
