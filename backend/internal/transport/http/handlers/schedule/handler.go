package schedule_handler

import (
	"net/http"

	schedule_service "kanban-board/internal/services/schedule"
	handlercommon "kanban-board/internal/transport/http/handlers/common"
	"kanban-board/pkg"
)

type Handler struct{ service *schedule_service.Service }

func Init(service *schedule_service.Service) *Handler { return &Handler{service: service} }

func (h *Handler) Start(mux *http.ServeMux) {
	mux.HandleFunc("GET /student-groups", h.listStudentGroups)
	mux.HandleFunc("GET /schedule/lessons", h.listLessons)
}

func (h *Handler) listStudentGroups(w http.ResponseWriter, r *http.Request) {
	if _, ok := handlercommon.UserID(r); !ok {
		return
	}
	items, err := h.service.ListStudentGroups(r.Context())
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, 200, items)
}
func (h *Handler) listLessons(w http.ResponseWriter, r *http.Request) {
	if _, ok := handlercommon.UserID(r); !ok {
		return
	}
	from, to, ok := handlercommon.DateRange(w, r)
	if !ok {
		return
	}
	items, err := h.service.ListLessons(r.Context(), r.URL.Query().Get("group"), from, to)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	pkg.WriteJSON(w, 200, items)
}
