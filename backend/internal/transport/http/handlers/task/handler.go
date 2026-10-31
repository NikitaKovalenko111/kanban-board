package task_handler

import (
	"net/http"
	"time"

	task_service "kanban-board/internal/services/task"
	handlercommon "kanban-board/internal/transport/http/handlers/common"
	"kanban-board/pkg"
)

type Handler struct{ service *task_service.Service }

func Init(service *task_service.Service) *Handler { return &Handler{service: service} }

func (h *Handler) Start(mux *http.ServeMux) {
	mux.HandleFunc("GET /projects/{projectId}/tasks", h.listTasks)
	mux.HandleFunc("POST /projects/{projectId}/tasks", h.createTask)
	mux.HandleFunc("GET /tasks/{taskId}", h.getTask)
	mux.HandleFunc("PATCH /tasks/{taskId}", h.updateTask)
	mux.HandleFunc("DELETE /tasks/{taskId}", h.deleteTask)
	mux.HandleFunc("POST /tasks/{taskId}/take", h.takeTask)
	mux.HandleFunc("PATCH /tasks/{taskId}/status", h.moveTask)
	mux.HandleFunc("POST /tasks/{taskId}/assignees", h.addTaskAssignee)
	mux.HandleFunc("GET /tasks/{taskId}/assignees", h.listTaskAssignees)
	mux.HandleFunc("DELETE /tasks/{taskId}/assignees/{assigneeId}", h.removeTaskAssignee)
	mux.HandleFunc("GET /tasks/{taskId}/history", h.taskHistory)
}

type taskRequest struct {
	Title       string     `json:"title"`
	Description string     `json:"description"`
	Priority    string     `json:"priority"`
	DueDate     *time.Time `json:"dueDate"`
	AssigneeIDs []string   `json:"assigneeIds"`
}

type statusRequest struct {
	Status  string `json:"status"`
	Comment string `json:"comment"`
}

type memberRequest struct {
	UserID string `json:"userId"`
}

func (h *Handler) listTasks(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	items, err := h.service.ListTasks(r.Context(), id, r.PathValue("projectId"))
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, 200, items)
}
func (h *Handler) getTask(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	item, err := h.service.GetTask(r.Context(), id, r.PathValue("taskId"))
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, 200, item)
}
func (h *Handler) createTask(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var req taskRequest
	if !handlercommon.Decode(w, r, &req) {
		return
	}
	if req.Priority == "" {
		req.Priority = "MEDIUM"
	}
	taskID, err := h.service.CreateTask(r.Context(), id, r.PathValue("projectId"), req.Title, req.Description, req.Priority, req.DueDate, req.AssigneeIDs)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, 201, map[string]string{"id": taskID})
}
func (h *Handler) updateTask(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var req taskRequest
	if !handlercommon.Decode(w, r, &req) {
		return
	}
	if req.Priority == "" {
		req.Priority = "MEDIUM"
	}
	if err := h.service.UpdateTask(r.Context(), id, r.PathValue("taskId"), req.Title, req.Description, req.Priority, req.DueDate); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	h.getTask(w, r)
}
func (h *Handler) deleteTask(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	if err := h.service.DeleteTask(r.Context(), id, r.PathValue("taskId")); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteEmpty(w, 204)
}
func (h *Handler) takeTask(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	if err := h.service.TakeTask(r.Context(), id, r.PathValue("taskId")); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	h.getTask(w, r)
}
func (h *Handler) moveTask(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var req statusRequest
	if !handlercommon.Decode(w, r, &req) {
		return
	}
	if err := h.service.MoveTask(r.Context(), id, r.PathValue("taskId"), req.Status, req.Comment); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	h.getTask(w, r)
}
func (h *Handler) addTaskAssignee(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	var req memberRequest
	if !handlercommon.Decode(w, r, &req) {
		return
	}
	if err := h.service.AddTaskAssignee(r.Context(), id, r.PathValue("taskId"), req.UserID); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteEmpty(w, 204)
}
func (h *Handler) listTaskAssignees(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	task, err := h.service.GetTask(r.Context(), id, r.PathValue("taskId"))
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, http.StatusOK, task.AssigneeIDs)
}
func (h *Handler) removeTaskAssignee(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	if err := h.service.RemoveTaskAssignee(r.Context(), id, r.PathValue("taskId"), r.PathValue("assigneeId")); err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteEmpty(w, 204)
}
func (h *Handler) taskHistory(w http.ResponseWriter, r *http.Request) {
	id, ok := handlercommon.UserID(r)
	if !ok {
		return
	}
	items, err := h.service.TaskHistory(r.Context(), id, r.PathValue("taskId"))
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, 200, items)
}
