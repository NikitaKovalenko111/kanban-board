package notification_handler

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"time"

	"kanban-board/internal/middleware"
	"kanban-board/internal/models"
	notification_service "kanban-board/internal/services/notification"
	handlercommon "kanban-board/internal/transport/http/handlers/common"
	"kanban-board/pkg"

	"github.com/google/uuid"
)

type Handler struct {
	service *notification_service.Service
}

func Init(service *notification_service.Service) *Handler {
	return &Handler{service: service}
}

func (h *Handler) Start(mux *http.ServeMux) {
	mux.HandleFunc("GET /notifications", h.list)
	mux.HandleFunc("GET /notifications/stream", h.stream)
	mux.HandleFunc("POST /notifications/read-all", h.readAll)
	mux.HandleFunc("POST /notifications/{notificationId}/read", h.read)
	mux.HandleFunc("POST /me/push-tokens", h.addPushToken)
	mux.HandleFunc("DELETE /me/push-tokens/{token}", h.deletePushToken)
}

func (h *Handler) read(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	if err := h.service.Read(r.Context(), userID, r.PathValue("notificationId")); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteEmpty(w, http.StatusNoContent)
}

func (h *Handler) stream(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	lastEventID := strings.TrimSpace(r.Header.Get("Last-Event-ID"))
	if lastEventID != "" {
		if err := uuid.Validate(lastEventID); err != nil {
			pkg.WriteError(w, http.StatusBadRequest, "VALIDATION", "Некорректный Last-Event-ID")
			return
		}
	}
	events, unsubscribe, err := h.service.Subscribe(userID)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	defer unsubscribe()

	controller := http.NewResponseController(w)
	if err := controller.SetWriteDeadline(time.Time{}); err != nil {
		pkg.WriteError(w, http.StatusInternalServerError, "SSE_UNSUPPORTED", "Поток уведомлений не поддерживается")
		return
	}
	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-cache, no-transform")
	w.Header().Set("Connection", "keep-alive")
	w.Header().Set("X-Accel-Buffering", "no")

	if lastEventID == "" {
		items, err := h.service.List(r.Context(), userID, 100)
		if err != nil {
			return
		}
		for left, right := 0, len(items)-1; left < right; left, right = left+1, right-1 {
			items[left], items[right] = items[right], items[left]
		}
		lastEventID, err = writeNotifications(w, items, lastEventID)
		if err != nil {
			return
		}
	} else {
		lastEventID, err = h.writeNewNotifications(r, w, userID, lastEventID)
		if err != nil {
			middleware.QueueErrorLog(r.Context(), "load missed SSE notifications", err)
			return
		}
	}

	if err := writeSSE(w, "ready", "", map[string]string{"status": "connected"}); err != nil {
		return
	}
	if err := controller.Flush(); err != nil {
		return
	}

	heartbeat := time.NewTicker(15 * time.Second)
	defer heartbeat.Stop()
	expires := tokenExpiry(r)
	if expires != nil {
		defer expires.Stop()
	}

	for {
		select {
		case <-events:
			lastEventID, err = h.writeNewNotifications(r, w, userID, lastEventID)
			if err != nil {
				middleware.QueueErrorLog(r.Context(), "load SSE notifications", err)
				return
			}
			if err := writeSSE(w, "refresh", "", map[string]string{"reason": "notifications_changed"}); err != nil {
				return
			}
			if err := controller.Flush(); err != nil {
				return
			}
		case <-heartbeat.C:
			lastEventID, err = h.writeNewNotifications(r, w, userID, lastEventID)
			if err != nil {
				middleware.QueueErrorLog(r.Context(), "poll SSE notifications", err)
				return
			}
			if _, err := fmt.Fprint(w, ": keep-alive\n\n"); err != nil {
				return
			}
			if err := controller.Flush(); err != nil {
				return
			}
		case <-expiryChannel(expires):
			return
		case <-r.Context().Done():
			return
		}
	}
}

func (h *Handler) writeNewNotifications(
	r *http.Request,
	w http.ResponseWriter,
	userID string,
	lastEventID string,
) (string, error) {
	if lastEventID == "" {
		items, err := h.service.List(r.Context(), userID, 100)
		if err != nil {
			return lastEventID, err
		}
		for left, right := 0, len(items)-1; left < right; left, right = left+1, right-1 {
			items[left], items[right] = items[right], items[left]
		}

		return writeNotifications(w, items, lastEventID)
	}

	for {
		items, err := h.service.ListAfter(r.Context(), userID, lastEventID, 100)
		if err != nil {
			return lastEventID, err
		}
		lastEventID, err = writeNotifications(w, items, lastEventID)
		if err != nil || len(items) < 100 {
			return lastEventID, err
		}
	}
}

func writeNotifications(w http.ResponseWriter, items []models.Notification, lastEventID string) (string, error) {
	for _, item := range items {
		if err := writeSSE(w, "notification", item.ID, item); err != nil {
			return lastEventID, err
		}
		lastEventID = item.ID
	}

	return lastEventID, nil
}

func writeSSE(w http.ResponseWriter, event, id string, value any) error {
	data, err := json.Marshal(value)
	if err != nil {
		return err
	}
	if id != "" {
		if _, err := fmt.Fprintf(w, "id: %s\n", id); err != nil {
			return err
		}
	}
	_, err = fmt.Fprintf(w, "event: %s\ndata: %s\n\n", event, data)

	return err
}

func tokenExpiry(r *http.Request) *time.Timer {
	claims, ok := middleware.TokenClaimsFromContext(r.Context())
	if !ok || claims.ExpiresAt == nil {
		return nil
	}
	duration := time.Until(claims.ExpiresAt.Time)
	if duration < 0 {
		duration = 0
	}

	return time.NewTimer(duration)
}

func expiryChannel(timer *time.Timer) <-chan time.Time {
	if timer == nil {
		return nil
	}

	return timer.C
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
