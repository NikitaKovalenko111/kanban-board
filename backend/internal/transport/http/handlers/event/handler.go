package event_handler

import (
	"net/http"
	"strings"
	"time"

	event_service "kanban-board/internal/services/event"
	handlercommon "kanban-board/internal/transport/http/handlers/common"
	"kanban-board/pkg"
)

type Handler struct {
	service *event_service.Service
}

func Init(service *event_service.Service) *Handler {
	return &Handler{service: service}
}

func (h *Handler) Start(mux *http.ServeMux) {
	mux.HandleFunc("GET /events", h.listEvents)
	mux.HandleFunc("POST /events", h.createEvent)
	mux.HandleFunc("GET /events/{eventId}", h.getEvent)
	mux.HandleFunc("PATCH /events/{eventId}", h.updateEvent)
	mux.HandleFunc("DELETE /events/{eventId}", h.deleteEvent)
	mux.HandleFunc("GET /event-types", h.listEventTypes)
	mux.HandleFunc("POST /event-types", h.createEventType)
	mux.HandleFunc("PATCH /event-types/{eventTypeId}", h.updateEventType)
	mux.HandleFunc("DELETE /event-types/{eventTypeId}", h.deleteEventType)
	mux.HandleFunc("GET /me/reminders", h.getReminders)
	mux.HandleFunc("PUT /me/reminders/{itemKey}", h.putReminders)
	mux.HandleFunc("DELETE /me/reminders/{itemKey}", h.deleteReminderOverride)
}

func (h *Handler) deleteReminderOverride(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	if err := h.service.DeleteReminderOverride(r.Context(), userID, r.PathValue("itemKey")); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteEmpty(w, http.StatusNoContent)
}

type eventTypeRequest struct {
	Title            *string `json:"title"`
	Color            *string `json:"color"`
	DefaultReminders *[]int  `json:"defaultReminders"`
}

type createEventRequest struct {
	Title       string     `json:"title"`
	Description string     `json:"description"`
	Location    string     `json:"location"`
	StartsAt    time.Time  `json:"startsAt"`
	EndsAt      *time.Time `json:"endsAt"`
	Visibility  string     `json:"visibility"`
	Group       string     `json:"group"`
	TypeID      *string    `json:"typeId"`
	ProjectID   *string    `json:"projectId"`
	MyReminders []int      `json:"myReminders"`
}

type updateEventRequest struct {
	Title       *string                           `json:"title"`
	Description *string                           `json:"description"`
	Location    *string                           `json:"location"`
	StartsAt    *time.Time                        `json:"startsAt"`
	EndsAt      handlercommon.Optional[time.Time] `json:"endsAt"`
	Visibility  *string                           `json:"visibility"`
	Group       *string                           `json:"group"`
	TypeID      handlercommon.Optional[string]    `json:"typeId"`
	ProjectID   handlercommon.Optional[string]    `json:"projectId"`
}

type remindersRequest struct {
	Minutes []int `json:"minutes"`
}

func (h *Handler) listEventTypes(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	items, err := h.service.ListEventTypes(r.Context(), userID)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusOK, items)
}

func (h *Handler) createEventType(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var request eventTypeRequest
	if !handlercommon.Decode(w, r, &request) {
		return
	}
	if request.Title == nil || request.Color == nil {
		pkg.WriteError(w, http.StatusBadRequest, "VALIDATION", "Укажите название и цвет типа события")
		return
	}
	reminders := make([]int, 0)
	if request.DefaultReminders != nil {
		reminders = *request.DefaultReminders
	}
	item, err := h.service.CreateEventType(r.Context(), userID, *request.Title, *request.Color, reminders)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusCreated, item)
}

func (h *Handler) updateEventType(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var request eventTypeRequest
	if !handlercommon.Decode(w, r, &request) {
		return
	}
	item, err := h.service.UpdateEventType(
		r.Context(), userID, r.PathValue("eventTypeId"),
		request.Title, request.Color, request.DefaultReminders,
	)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusOK, item)
}

func (h *Handler) deleteEventType(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	if err := h.service.DeleteEventType(r.Context(), userID, r.PathValue("eventTypeId")); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteEmpty(w, http.StatusNoContent)
}

func (h *Handler) listEvents(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	from, to, ok := handlercommon.DateRange(w, r)
	if !ok {
		return
	}
	personID := strings.TrimSpace(r.URL.Query().Get("personId"))
	group := strings.TrimSpace(r.URL.Query().Get("group"))
	if group == "" && personID == "" {
		group = strings.TrimSpace(strings.Split(r.URL.Query().Get("groups"), ",")[0])
	}
	items, err := h.service.ListEvents(r.Context(), userID, personID, group, from, to)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusOK, items)
}

func (h *Handler) getEvent(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	item, err := h.service.GetEvent(r.Context(), userID, r.PathValue("eventId"))
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusOK, item)
}

func (h *Handler) createEvent(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var request createEventRequest
	if !handlercommon.Decode(w, r, &request) {
		return
	}
	item, err := h.service.CreateEvent(
		r.Context(), userID, request.Title, request.Description, request.Location,
		request.StartsAt, request.EndsAt, request.Visibility, request.Group,
		request.TypeID, request.ProjectID, request.MyReminders,
	)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusCreated, item)
}

func (h *Handler) updateEvent(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var request updateEventRequest
	if !handlercommon.Decode(w, r, &request) {
		return
	}
	item, err := h.service.UpdateEvent(
		r.Context(), userID, r.PathValue("eventId"), request.Title,
		request.Description, request.Location, request.StartsAt,
		request.EndsAt.Value, request.EndsAt.Set, request.Visibility,
		request.Group, request.TypeID.Value, request.TypeID.Set,
		request.ProjectID.Value, request.ProjectID.Set,
	)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusOK, item)
}

func (h *Handler) deleteEvent(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	if err := h.service.DeleteEvent(r.Context(), userID, r.PathValue("eventId")); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteEmpty(w, http.StatusNoContent)
}

func (h *Handler) getReminders(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	items, err := h.service.GetReminders(r.Context(), userID)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusOK, items)
}

func (h *Handler) putReminders(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var request remindersRequest
	if !handlercommon.Decode(w, r, &request) {
		return
	}
	if err := h.service.PutReminders(r.Context(), userID, r.PathValue("itemKey"), request.Minutes); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteEmpty(w, http.StatusNoContent)
}
