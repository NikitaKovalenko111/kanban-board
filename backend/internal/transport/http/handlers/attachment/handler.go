package attachment_handler

import (
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"time"

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
}

func Init(service *attachment_service.Service, projects *project_service.Service) *Handler {
	return &Handler{service: service, projects: projects}
}

func (h *Handler) Start(mux *http.ServeMux) {
	mux.HandleFunc("POST /tasks/{taskId}/attachments", h.uploadAttachment)
	mux.HandleFunc("DELETE /tasks/{taskId}/attachments/{attachmentId}", h.deleteAttachment)
	mux.HandleFunc("GET /files/{fileId}", h.downloadFile)
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
	storageKey := filepath.Join("data", "tasks", r.PathValue("taskId"), fmt.Sprintf("%d-%s", time.Now().UnixNano(), name))
	if err := os.MkdirAll(filepath.Dir(storageKey), 0750); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	target, err := os.OpenFile(storageKey, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0640)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	size, copyErr := io.Copy(target, io.LimitReader(file, maxAttachmentSize+1))
	closeErr := target.Close()
	if copyErr != nil || closeErr != nil || size > maxAttachmentSize {
		_ = os.Remove(storageKey)
		pkg.WriteError(w, http.StatusRequestEntityTooLarge, "FILE_TOO_LARGE", "Файл превышает 10 МБ")
		return
	}
	attachment, err := h.service.AddAttachment(
		r.Context(), userID, r.PathValue("taskId"), kind,
		name, storageKey, header.Header.Get("Content-Type"), size,
	)
	if err != nil {
		_ = os.Remove(storageKey)
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
		serveFile(w, r, attachment.StorageKey, attachment.File.Name, attachment.File.MimeType)
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
	serveFile(w, r, storageKey, name, mimeType)
}

func serveFile(w http.ResponseWriter, r *http.Request, storageKey, name, mimeType string) {
	w.Header().Set("Content-Type", mimeType)
	w.Header().Set("Content-Disposition", fmt.Sprintf("attachment; filename=%q", name))
	http.ServeFile(w, r, storageKey)
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
	_ = os.Remove(attachment.StorageKey)
	pkg.WriteEmpty(w, http.StatusNoContent)
}
