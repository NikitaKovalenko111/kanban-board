package project_handler

import (
	"net/http"

	project_service "kanban-board/internal/services/project"
	handlercommon "kanban-board/internal/transport/http/handlers/common"
	"kanban-board/pkg"
)

type Handler struct {
	service *project_service.Service
}

func Init(service *project_service.Service) *Handler {
	return &Handler{
		service: service,
	}
}

func (h *Handler) Start(mux *http.ServeMux) {
	mux.HandleFunc("GET /projects", h.listProjects)
	mux.HandleFunc("POST /projects", h.createProject)
	mux.HandleFunc("GET /projects/{projectId}", h.getProject)
	mux.HandleFunc("PATCH /projects/{projectId}", h.updateProject)
	mux.HandleFunc("DELETE /projects/{projectId}", h.deleteProject)
	mux.HandleFunc("GET /projects/{projectId}/members", h.listProjectMembers)
	mux.HandleFunc("POST /projects/{projectId}/members", h.addProjectMember)
	mux.HandleFunc("PATCH /projects/{projectId}/members/{memberId}", h.updateProjectMember)
	mux.HandleFunc("DELETE /projects/{projectId}/members/{memberId}", h.removeProjectMember)
	mux.HandleFunc("POST /projects/{projectId}/members/me", h.joinProject)
	mux.HandleFunc("DELETE /projects/{projectId}/members/me", h.leaveProject)
}

type projectRequest struct {
	Name        string `json:"name"`
	Description string `json:"description"`
	JoinPolicy  string `json:"joinPolicy"`
	Archived    bool   `json:"archived"`
}

type memberRequest struct {
	UserID string `json:"userId"`
	Role   string `json:"role"`
	Status string `json:"status"`
}

func (h *Handler) listProjects(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	items, err := h.service.ListProjects(r.Context(), id)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, 200, items)
}

func (h *Handler) getProject(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	item, err := h.service.GetProject(r.Context(), id, r.PathValue("projectId"))
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, 200, item)
}

func (h *Handler) createProject(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var req projectRequest
	if !handlercommon.Decode(w, r, &req) {
		return
	}
	if req.JoinPolicy == "" {
		req.JoinPolicy = "INVITE_ONLY"
	}
	projectID, err := h.service.CreateProject(r.Context(), id, req.Name, req.Description, req.JoinPolicy)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, 201, map[string]string{"id": projectID})
}

func (h *Handler) updateProject(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var req projectRequest
	if !handlercommon.Decode(w, r, &req) {
		return
	}
	if req.JoinPolicy == "" {
		req.JoinPolicy = "INVITE_ONLY"
	}
	if err := h.service.UpdateProject(r.Context(), id, r.PathValue("projectId"), req.Name, req.Description, req.JoinPolicy, req.Archived); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	h.getProject(w, r)
}

func (h *Handler) deleteProject(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	if err := h.service.DeleteProject(r.Context(), id, r.PathValue("projectId")); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteEmpty(w, 204)
}

func (h *Handler) listProjectMembers(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	items, err := h.service.ListProjectMembers(r.Context(), id, r.PathValue("projectId"))
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, 200, items)
}

func (h *Handler) addProjectMember(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var req memberRequest
	if !handlercommon.Decode(w, r, &req) {
		return
	}
	if req.Role == "" {
		req.Role = "MEMBER"
	}
	if err := h.service.AddProjectMember(r.Context(), id, r.PathValue("projectId"), req.UserID, req.Role); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteEmpty(w, 204)
}

func (h *Handler) updateProjectMember(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var req memberRequest
	if !handlercommon.Decode(w, r, &req) {
		return
	}
	if err := h.service.UpdateProjectMember(r.Context(), id, r.PathValue("projectId"), r.PathValue("memberId"), req.Role, req.Status); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteEmpty(w, 204)
}

func (h *Handler) removeProjectMember(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	if err := h.service.RemoveProjectMember(r.Context(), id, r.PathValue("projectId"), r.PathValue("memberId")); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteEmpty(w, 204)
}

func (h *Handler) joinProject(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	if err := h.service.JoinProject(r.Context(), id, r.PathValue("projectId")); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteEmpty(w, 204)
}

func (h *Handler) leaveProject(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	if err := h.service.RemoveProjectMember(r.Context(), id, r.PathValue("projectId"), id); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteEmpty(w, 204)
}
