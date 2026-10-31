package event_handler

import (
	"net/http"
	"time"

	event_service "kanban-board/internal/services/event"
	handlercommon "kanban-board/internal/transport/http/handlers/common"
	"kanban-board/pkg"
)

type Handler struct {
	service *event_service.Service
}

func Init(service *event_service.Service) *Handler {
	return &Handler{
		service: service,
	}
}

func (h *Handler) Start(mux *http.ServeMux) {
	mux.HandleFunc("GET /event-types", h.listEventTypes)
	mux.HandleFunc("POST /event-types", h.createEventType)
	mux.HandleFunc("GET /event-types/{eventTypeId}", h.getEventType)
	mux.HandleFunc("PATCH /event-types/{eventTypeId}", h.updateEventType)
	mux.HandleFunc("DELETE /event-types/{eventTypeId}", h.deleteEventType)
	mux.HandleFunc("GET /events", h.listEvents)
	mux.HandleFunc("POST /events", h.createEvent)
	mux.HandleFunc("GET /events/{eventId}", h.getEvent)
	mux.HandleFunc("PATCH /events/{eventId}", h.updateEvent)
	mux.HandleFunc("DELETE /events/{eventId}", h.deleteEvent)
}

type eventTypeRequest struct {
	Title                  string  `json:"title"`
	Color                  string  `json:"color"`
	Scope                  string  `json:"scope"`
	ProjectID              *string `json:"projectId"`
	DefaultReminderMinutes []int   `json:"defaultReminderMinutes"`
}

type eventRequest struct {
	EventTypeID     string    `json:"eventTypeId"`
	ProjectID       *string   `json:"projectId"`
	StudentGroup    string    `json:"studentGroup"`
	Title           string    `json:"title"`
	Description     string    `json:"description"`
	Location        string    `json:"location"`
	Visibility      string    `json:"visibility"`
	StartsAt        time.Time `json:"startsAt"`
	EndsAt          time.Time `json:"endsAt"`
	ReminderMinutes []int     `json:"reminderMinutes"`
}

func (h *Handler) listEventTypes(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	items, err := h.service.ListEventTypes(r.Context(), id)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, 200, items)
}

func (h *Handler) createEventType(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var req eventTypeRequest
	if !handlercommon.Decode(w, r, &req) {
		return
	}
	if req.Scope == "" {
		req.Scope = "PERSONAL"
	}
	eventTypeID, err := h.service.CreateEventType(r.Context(), id, req.Title, req.Color, req.Scope, req.ProjectID, req.DefaultReminderMinutes)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, 201, map[string]string{"id": eventTypeID})
}

func (h *Handler) getEventType(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	item, err := h.service.GetEventType(r.Context(), id, r.PathValue("eventTypeId"))
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusOK, item)
}

func (h *Handler) updateEventType(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var req eventTypeRequest
	if !handlercommon.Decode(w, r, &req) {
		return
	}
	if err := h.service.UpdateEventType(r.Context(), id, r.PathValue("eventTypeId"), req.Title, req.Color, req.DefaultReminderMinutes); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteEmpty(w, 204)
}

func (h *Handler) deleteEventType(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	if err := h.service.DeleteEventType(r.Context(), id, r.PathValue("eventTypeId")); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteEmpty(w, 204)
}

func (h *Handler) listEvents(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	from, to, ok := handlercommon.DateRange(w, r)
	if !ok {
		return
	}
	var projectID *string
	if value := r.URL.Query().Get("projectId"); value != "" {
		projectID = &value
	}
	items, err := h.service.ListEvents(r.Context(), id, projectID, r.URL.Query().Get("group"), from, to)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, 200, items)
}

func (h *Handler) createEvent(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var req eventRequest
	if !handlercommon.Decode(w, r, &req) {
		return
	}
	eventID, err := h.service.CreateEvent(r.Context(), id, req.EventTypeID, req.Title, req.Description, req.Location, req.Visibility, req.ProjectID, req.StudentGroup, req.StartsAt, req.EndsAt, req.ReminderMinutes)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, 201, map[string]string{"id": eventID})
}

func (h *Handler) getEvent(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	item, err := h.service.GetEvent(r.Context(), id, r.PathValue("eventId"))
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusOK, item)
}

func (h *Handler) updateEvent(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var req eventRequest
	if !handlercommon.Decode(w, r, &req) {
		return
	}
	if err := h.service.UpdateEvent(r.Context(), id, r.PathValue("eventId"), req.Title, req.Description, req.Location, req.StartsAt, req.EndsAt, req.ReminderMinutes); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteEmpty(w, 204)
}

func (h *Handler) deleteEvent(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	if err := h.service.DeleteEvent(r.Context(), id, r.PathValue("eventId")); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteEmpty(w, 204)
}
