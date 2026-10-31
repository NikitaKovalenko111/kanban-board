package profile_handler

import (
	"net/http"
	"strings"

	profile_service "kanban-board/internal/services/profile"
	handlercommon "kanban-board/internal/transport/http/handlers/common"
	"kanban-board/pkg"
)

type Handler struct{ service *profile_service.Service }

func Init(service *profile_service.Service) *Handler { return &Handler{service: service} }

func (h *Handler) Start(mux *http.ServeMux) {
	mux.HandleFunc("GET /users/me", h.getMe)
	mux.HandleFunc("PATCH /users/me", h.updateMe)
	mux.HandleFunc("GET /users/{userId}", h.getUser)
}

type profileRequest struct {
	DisplayName  string `json:"displayName"`
	Position     string `json:"position"`
	StudentGroup string `json:"studentGroup"`
	ImageURL     string `json:"imageUrl"`
}

func (h *Handler) getMe(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	profile, err := h.service.GetProfile(r.Context(), id)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusOK, profile)
}
func (h *Handler) getUser(w http.ResponseWriter, r *http.Request) {
	if _, ok := handlercommon.UserID(r); !ok {
		return
	}
	profile, err := h.service.GetProfile(r.Context(), r.PathValue("userId"))
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	profile.Email = ""
	profile.Memberships = nil
	pkg.WriteJSON(w, http.StatusOK, profile)
}
func (h *Handler) updateMe(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var req profileRequest
	if !handlercommon.Decode(w, r, &req) {
		return
	}
	if strings.TrimSpace(req.DisplayName) == "" {
		pkg.WriteError(w, 400, "invalid_request", "displayName is required")
		return
	}
	if err := h.service.UpdateProfile(r.Context(), id, req.DisplayName, req.Position, req.StudentGroup, req.ImageURL); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	h.getMe(w, r)
}
