package task_handler

import (
	"context"
	"net/http"
	"os"
	"time"

	task_service "kanban-board/internal/services/task"
	handlercommon "kanban-board/internal/transport/http/handlers/common"
	"kanban-board/pkg"
)

type Handler struct {
	service *task_service.Service
}

func Init(service *task_service.Service) *Handler {
	return &Handler{service: service}
}

func (h *Handler) Start(mux *http.ServeMux) {
	mux.HandleFunc("GET /projects/{projectId}/tasks", h.listTasks)
	mux.HandleFunc("POST /projects/{projectId}/tasks", h.createTask)
	mux.HandleFunc("GET /tasks/completed", h.listCompleted)
	mux.HandleFunc("GET /tasks/assigned", h.listAssigned)
	mux.HandleFunc("GET /tasks/{taskId}", h.getTask)
	mux.HandleFunc("PATCH /tasks/{taskId}", h.updateTask)
	mux.HandleFunc("DELETE /tasks/{taskId}", h.deleteTask)
	mux.HandleFunc("POST /tasks/{taskId}/take", h.takeTask)
	mux.HandleFunc("POST /tasks/{taskId}/move", h.moveTask)
	mux.HandleFunc("POST /tasks/{taskId}/assign-to-me", h.assignToMe)
}

type createTaskRequest struct {
	Title       string     `json:"title"`
	Description string     `json:"description"`
	Priority    string     `json:"priority"`
	AssigneeID  *string    `json:"assigneeId"`
	DueAt       *time.Time `json:"dueAt"`
	ColumnID    *string    `json:"columnId"`
}

type updateTaskRequest struct {
	Title       *string                           `json:"title"`
	Description *string                           `json:"description"`
	Priority    *string                           `json:"priority"`
	AssigneeID  handlercommon.Optional[string]    `json:"assigneeId"`
	DueAt       handlercommon.Optional[time.Time] `json:"dueAt"`
}

type moveRequest struct {
	ColumnID string `json:"columnId"`
	Comment  string `json:"comment"`
}

func (h *Handler) listTasks(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	tasks, err := h.service.ListTasks(r.Context(), userID, r.PathValue("projectId"))
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusOK, tasks)
}

func (h *Handler) getTask(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	task, err := h.service.GetTask(r.Context(), userID, r.PathValue("taskId"))
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusOK, task)
}

func (h *Handler) createTask(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var request createTaskRequest
	if !handlercommon.Decode(w, r, &request) {
		return
	}
	if request.Priority == "" {
		request.Priority = "NORMAL"
	}
	task, err := h.service.CreateTask(
		r.Context(), userID, r.PathValue("projectId"), request.Title,
		request.Description, request.Priority, request.AssigneeID, request.DueAt, request.ColumnID,
	)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusCreated, task)
}

func (h *Handler) updateTask(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var request updateTaskRequest
	if !handlercommon.Decode(w, r, &request) {
		return
	}
	task, err := h.service.UpdateTask(
		r.Context(), userID, r.PathValue("taskId"), request.Title,
		request.Description, request.Priority, request.DueAt.Value, request.DueAt.Set,
		request.AssigneeID.Value, request.AssigneeID.Set,
	)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusOK, task)
}

func (h *Handler) deleteTask(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	storageKeys, err := h.service.DeleteTask(r.Context(), userID, r.PathValue("taskId"))
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	for _, storageKey := range storageKeys {
		_ = os.Remove(storageKey)
	}
	pkg.WriteEmpty(w, http.StatusNoContent)
}

func (h *Handler) takeTask(w http.ResponseWriter, r *http.Request) {
	h.writeTaskAction(w, r, h.service.TakeTask)
}

func (h *Handler) assignToMe(w http.ResponseWriter, r *http.Request) {
	h.writeTaskAction(w, r, h.service.AssignToMe)
}

func (h *Handler) writeTaskAction(w http.ResponseWriter, r *http.Request, action func(context.Context, string, string) (*task_service.Task, error)) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	task, err := action(r.Context(), userID, r.PathValue("taskId"))
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusOK, task)
}

func (h *Handler) moveTask(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var request moveRequest
	if !handlercommon.Decode(w, r, &request) {
		return
	}
	task, err := h.service.MoveTask(r.Context(), userID, r.PathValue("taskId"), request.ColumnID, request.Comment)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusOK, task)
}

func (h *Handler) listCompleted(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	from, err := optionalDate(r.URL.Query().Get("from"))
	if err != nil {
		pkg.WriteError(w, http.StatusBadRequest, "VALIDATION", "Некорректная дата from")
		return
	}
	to, err := optionalDate(r.URL.Query().Get("to"))
	if err != nil {
		pkg.WriteError(w, http.StatusBadRequest, "VALIDATION", "Некорректная дата to")
		return
	}
	if to != nil {
		value := to.AddDate(0, 0, 1)
		to = &value
	}
	tasks, err := h.service.ListCompleted(
		r.Context(), userID, from, to,
		r.URL.Query().Get("projectId"), r.URL.Query().Get("assigneeId"), r.URL.Query().Get("search"),
	)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusOK, tasks)
}

func (h *Handler) listAssigned(w http.ResponseWriter, r *http.Request) {
	userID, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	tasks, err := h.service.ListAssigned(r.Context(), userID, r.URL.Query().Get("userId"))
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusOK, tasks)
}

func optionalDate(value string) (*time.Time, error) {
	if value == "" {
		return nil, nil
	}
	parsed, err := time.Parse("2006-01-02", value)
	return &parsed, err
}
