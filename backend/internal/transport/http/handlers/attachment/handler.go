package attachment_handler

import (
	"bytes"
	"errors"
	"fmt"
	"io"
	"net/http"
	"path/filepath"
	"time"

	"kanban-board/internal/objectstorage"
	attachment_service "kanban-board/internal/services/attachment"
	servicecommon "kanban-board/internal/services/common"
	project_service "kanban-board/internal/services/project"
	handlercommon "kanban-board/internal/transport/http/handlers/common"
	"kanban-board/pkg"
)

const maxAttachmentSize = 10 << 20

type Handler struct {
	service  *attachment_service.Service
	projects *project_service.Service
	objects  objectstorage.Storage
}

func Init(service *attachment_service.Service, projects *project_service.Service, objects ...objectstorage.Storage) *Handler {
	handler := &Handler{service: service, projects: projects}
	if len(objects) > 0 {
		handler.objects = objects[0]
	}
	return handler
}

func (h *Handler) Start(mux *http.ServeMux) {
	mux.HandleFunc("POST /tasks/{taskId}/attachments", h.uploadAttachment)
	mux.HandleFunc("POST /tasks/{taskId}/attachments/links", h.addLinkAttachment)
	mux.HandleFunc("DELETE /tasks/{taskId}/attachments/{attachmentId}", h.deleteAttachment)
	mux.HandleFunc("GET /files/{fileId}", h.downloadFile)
}

type linkAttachmentRequest struct {
	Kind  string `json:"kind"`
	Title string `json:"title"`
	URL   string `json:"url"`
}

func (h *Handler) addLinkAttachment(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var request linkAttachmentRequest
	if !handlercommon.Decode(w, r, &request) {
		return
	}
	attachment, err := h.service.AddLinkAttachment(
		r.Context(), userID, r.PathValue("taskId"), request.Kind, request.Title, request.URL,
	)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusCreated, attachment)
}

func (h *Handler) uploadAttachment(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	r.Body = http.MaxBytesReader(w, r.Body, maxAttachmentSize+(1<<20))
	file, header, err := r.FormFile("file")
	if err != nil {
		pkg.WriteError(w, http.StatusBadRequest, "VALIDATION", "Выберите файл")
		return
	}
	defer file.Close()
	kind := r.FormValue("kind")
	if kind != "SOURCE" && kind != "RESULT" {
		pkg.WriteError(w, http.StatusBadRequest, "VALIDATION", "kind должен быть SOURCE или RESULT")
		return
	}
	name := filepath.Base(header.Filename)
	storageKey := filepath.ToSlash(filepath.Join("tasks", r.PathValue("taskId"), fmt.Sprintf("%d-%s", time.Now().UnixNano(), name)))
	data, err := io.ReadAll(io.LimitReader(file, maxAttachmentSize+1))
	if err != nil || len(data) > maxAttachmentSize {
		pkg.WriteError(w, http.StatusRequestEntityTooLarge, "FILE_TOO_LARGE", "Файл превышает 10 МБ")
		return
	}
	if err := h.objects.Put(r.Context(), storageKey, bytes.NewReader(data), int64(len(data)), header.Header.Get("Content-Type")); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	attachment, err := h.service.AddAttachment(
		r.Context(), userID, r.PathValue("taskId"), kind,
		name, storageKey, header.Header.Get("Content-Type"), int64(len(data)),
	)
	if err != nil {
		_ = h.objects.Delete(r.Context(), storageKey)
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusCreated, attachment)
}

func (h *Handler) downloadFile(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	fileID := r.PathValue("fileId")
	attachment, err := h.service.GetAttachment(r.Context(), userID, fileID)
	if err == nil {
		if attachment.File == nil {
			pkg.WriteError(w, http.StatusNotFound, "NOT_FOUND", "Файл не найден")
			return
		}
		h.serveFile(w, r, attachment.StorageKey, attachment.File.Name, attachment.File.MimeType)
		return
	}
	if !errors.Is(err, servicecommon.ErrNotFound) {
		handlercommon.WriteError(w, r, err)
		return
	}
	storageKey, name, mimeType, err := h.projects.GetSourceFile(r.Context(), userID, fileID)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	h.serveFile(w, r, storageKey, name, mimeType)
}

func (h *Handler) serveFile(w http.ResponseWriter, r *http.Request, storageKey, name, mimeType string) {
	object, err := h.objects.Get(r.Context(), storageKey)
	if err != nil {
		handlercommon.WriteError(w, r, servicecommon.ErrNotFound)
		return
	}
	defer object.Reader.Close()
	w.Header().Set("Content-Type", mimeType)
	w.Header().Set("Content-Disposition", fmt.Sprintf("attachment; filename=%q", name))
	w.Header().Set("Content-Length", fmt.Sprint(object.Size))
	_, _ = io.Copy(w, object.Reader)
}

func (h *Handler) deleteAttachment(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	attachment, err := h.service.DeleteAttachment(
		r.Context(), userID, r.PathValue("taskId"), r.PathValue("attachmentId"),
	)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	if attachment.StorageKey != "" {
		_ = h.objects.Delete(r.Context(), attachment.StorageKey)
	}
	pkg.WriteEmpty(w, http.StatusNoContent)
}
