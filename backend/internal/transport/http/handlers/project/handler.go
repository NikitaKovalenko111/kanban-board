package project_handler

import (
	"bytes"
	"fmt"
	"io"
	"net/http"
	"path/filepath"
	"time"

	"kanban-board/internal/models"
	"kanban-board/internal/objectstorage"
	project_service "kanban-board/internal/services/project"
	handlercommon "kanban-board/internal/transport/http/handlers/common"
	"kanban-board/pkg"
)

const maxSourceFileSize = 10 << 20

type Handler struct {
	service *project_service.Service
	objects objectstorage.Storage
}

func Init(service *project_service.Service, objects ...objectstorage.Storage) *Handler {
	handler := &Handler{service: service}
	if len(objects) > 0 {
		handler.objects = objects[0]
	}
	return handler
}

func (h *Handler) Start(mux *http.ServeMux) {
	mux.HandleFunc("GET /projects", h.listProjects)
	mux.HandleFunc("POST /projects", h.createProject)
	mux.HandleFunc("GET /projects/{projectId}", h.getProject)
	mux.HandleFunc("PATCH /projects/{projectId}", h.updateProject)
	mux.HandleFunc("DELETE /projects/{projectId}", h.deleteProject)
	mux.HandleFunc("POST /projects/{projectId}/sources/links", h.addLinkSource)
	mux.HandleFunc("POST /projects/{projectId}/sources/files", h.addFileSource)
	mux.HandleFunc("DELETE /projects/{projectId}/sources/{sourceId}", h.deleteSource)
}

type projectRequest struct {
	Name        *string                 `json:"name"`
	Description *string                 `json:"description"`
	Color       *string                 `json:"color"`
	Members     *[]models.ProjectMember `json:"members"`
}

type linkSourceRequest struct {
	Title string `json:"title"`
	URL   string `json:"url"`
}

func (h *Handler) listProjects(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	projects, err := h.service.ListProjects(r.Context(), userID)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusOK, projects)
}

func (h *Handler) getProject(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	project, err := h.service.GetProject(r.Context(), userID, r.PathValue("projectId"))
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusOK, project)
}

func (h *Handler) createProject(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var request projectRequest
	if !handlercommon.Decode(w, r, &request) {
		return
	}
	if request.Name == nil {
		pkg.WriteError(w, http.StatusBadRequest, "VALIDATION", "Укажите название проекта")
		return
	}
	description := ""
	if request.Description != nil {
		description = *request.Description
	}
	color := "#6576DF"
	if request.Color != nil {
		color = *request.Color
	}
	members := make([]models.ProjectMember, 0)
	if request.Members != nil {
		members = *request.Members
	}
	project, err := h.service.CreateProject(r.Context(), userID, *request.Name, description, color, members)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusCreated, project)
}

func (h *Handler) updateProject(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var request projectRequest
	if !handlercommon.Decode(w, r, &request) {
		return
	}
	project, err := h.service.UpdateProject(
		r.Context(), userID, r.PathValue("projectId"),
		request.Name, request.Description, request.Members,
		request.Color,
	)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusOK, project)
}

func (h *Handler) deleteProject(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	storageKeys, err := h.service.DeleteProject(r.Context(), userID, r.PathValue("projectId"))
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	for _, storageKey := range storageKeys {
		_ = h.objects.Delete(r.Context(), storageKey)
	}
	pkg.WriteEmpty(w, http.StatusNoContent)
}

func (h *Handler) addLinkSource(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var request linkSourceRequest
	if !handlercommon.Decode(w, r, &request) {
		return
	}
	source, err := h.service.AddLinkSource(r.Context(), userID, r.PathValue("projectId"), request.Title, request.URL)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusCreated, source)
}

func (h *Handler) addFileSource(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	r.Body = http.MaxBytesReader(w, r.Body, maxSourceFileSize+(1<<20))
	file, header, err := r.FormFile("file")
	if err != nil {
		pkg.WriteError(w, http.StatusBadRequest, "VALIDATION", "Выберите файл")
		return
	}
	defer file.Close()

	name := filepath.Base(header.Filename)
	storageKey := filepath.ToSlash(filepath.Join("projects", r.PathValue("projectId"), fmt.Sprintf("%d-%s", time.Now().UnixNano(), name)))
	data, err := io.ReadAll(io.LimitReader(file, maxSourceFileSize+1))
	if err != nil || len(data) > maxSourceFileSize {
		pkg.WriteError(w, http.StatusRequestEntityTooLarge, "FILE_TOO_LARGE", "Файл превышает 10 МБ")
		return
	}
	if err := h.objects.Put(r.Context(), storageKey, bytes.NewReader(data), int64(len(data)), header.Header.Get("Content-Type")); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	source, err := h.service.AddFileSource(
		r.Context(), userID, r.PathValue("projectId"), name,
		storageKey, header.Header.Get("Content-Type"), int64(len(data)),
	)
	if err != nil {
		_ = h.objects.Delete(r.Context(), storageKey)
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusCreated, source)
}

func (h *Handler) deleteSource(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	storageKey, err := h.service.DeleteSource(r.Context(), userID, r.PathValue("projectId"), r.PathValue("sourceId"))
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	if storageKey != "" {
		_ = h.objects.Delete(r.Context(), storageKey)
	}
	pkg.WriteEmpty(w, http.StatusNoContent)
}
