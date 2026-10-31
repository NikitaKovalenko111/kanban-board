package attachment_handler

import (
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"time"

	attachment_service "kanban-board/internal/services/attachment"
	handlercommon "kanban-board/internal/transport/http/handlers/common"
	"kanban-board/pkg"
)

type Handler struct {
	service *attachment_service.Service
}

func Init(service *attachment_service.Service) *Handler {
	return &Handler{
		service: service,
	}
}

func (h *Handler) Start(mux *http.ServeMux) {
	mux.HandleFunc("GET /tasks/{taskId}/attachments", h.listAttachments)
	mux.HandleFunc("POST /tasks/{taskId}/attachments", h.uploadAttachment)
	mux.HandleFunc("GET /attachments/{attachmentId}/download", h.downloadAttachment)
	mux.HandleFunc("DELETE /attachments/{attachmentId}", h.deleteAttachment)
}

const maxAttachmentSize = 10 << 20

func (h *Handler) listAttachments(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	items, err := h.service.ListAttachments(r.Context(), id, r.PathValue("taskId"))
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, 200, items)
}

func (h *Handler) uploadAttachment(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	r.Body = http.MaxBytesReader(w, r.Body, maxAttachmentSize+(1<<20))
	if err := r.ParseMultipartForm(maxAttachmentSize); err != nil {
		pkg.WriteError(w, 400, "invalid_attachment", "Attachment is too large or malformed")
		return
	}
	file, header, err := r.FormFile("file")
	if err != nil {
		pkg.WriteError(w, 400, "invalid_attachment", "file is required")
		return
	}
	defer file.Close()
	kind := r.FormValue("attachmentType")
	if kind == "" {
		kind = r.FormValue("kind")
	}
	if kind != "SOURCE" && kind != "RESULT" {
		pkg.WriteError(w, 400, "invalid_attachment_type", "attachmentType must be SOURCE or RESULT")
		return
	}
	safeName := filepath.Base(header.Filename)
	storageKey := filepath.Join("data", "uploads", r.PathValue("taskId"), fmt.Sprintf("%d-%s", time.Now().UnixNano(), safeName))
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
		pkg.WriteError(w, 400, "invalid_attachment", "Failed to save attachment")
		return
	}
	attachmentID, err := h.service.AddAttachment(r.Context(), id, r.PathValue("taskId"), kind, safeName, storageKey, header.Header.Get("Content-Type"), size)
	if err != nil {
		_ = os.Remove(storageKey)
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, 201, map[string]string{"id": attachmentID})
}

func (h *Handler) downloadAttachment(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	a, err := h.service.GetAttachment(r.Context(), id, r.PathValue("attachmentId"))
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	w.Header().Set("Content-Type", a.MimeType)
	w.Header().Set("Content-Disposition", fmt.Sprintf("attachment; filename=%q", a.FileName))
	http.ServeFile(w, r, a.StorageKey)
}

func (h *Handler) deleteAttachment(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	a, err := h.service.DeleteAttachment(r.Context(), id, r.PathValue("attachmentId"))
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	_ = os.Remove(a.StorageKey)
	pkg.WriteEmpty(w, 204)
}
