package notification_handler

import (
	"net/http"
	"strconv"

	notification_service "kanban-board/internal/services/notification"
	handlercommon "kanban-board/internal/transport/http/handlers/common"
	"kanban-board/pkg"
)

type Handler struct {
	service *notification_service.Service
}

func Init(service *notification_service.Service) *Handler {
	return &Handler{service: service}
}

func (h *Handler) Start(mux *http.ServeMux) {
	mux.HandleFunc("GET /notifications", h.list)
	mux.HandleFunc("POST /notifications/read-all", h.readAll)
	mux.HandleFunc("POST /me/push-tokens", h.addPushToken)
	mux.HandleFunc("DELETE /me/push-tokens/{token}", h.deletePushToken)
}

type pushTokenRequest struct {
	Token    string `json:"token"`
	Platform string `json:"platform"`
}

func (h *Handler) list(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	limit, _ := strconv.Atoi(r.URL.Query().Get("limit"))
	items, err := h.service.List(r.Context(), userID, limit)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusOK, items)
}

func (h *Handler) readAll(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	if err := h.service.ReadAll(r.Context(), userID); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteEmpty(w, http.StatusNoContent)
}

func (h *Handler) addPushToken(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var request pushTokenRequest
	if !handlercommon.Decode(w, r, &request) {
		return
	}
	if err := h.service.AddPushToken(r.Context(), userID, request.Token, request.Platform); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteEmpty(w, http.StatusNoContent)
}

func (h *Handler) deletePushToken(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	if err := h.service.DeletePushToken(r.Context(), userID, r.PathValue("token")); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteEmpty(w, http.StatusNoContent)
}
