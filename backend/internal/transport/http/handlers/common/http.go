package handlercommon

import (
	"encoding/json"
	"errors"
	"net/http"
	"time"

	"kanban-board/internal/middleware"
	servicecommon "kanban-board/internal/services/common"
	"kanban-board/pkg"
)

func UserID(r *http.Request) (string, bool) {
	return middleware.UserIDFromContext(r.Context())
}

func Decode(w http.ResponseWriter, r *http.Request, destination any) bool {
	decoder := json.NewDecoder(r.Body)
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(destination); err != nil {
		middleware.QueueErrorLog(r.Context(), "failed to decode request", err)
		pkg.WriteError(w, http.StatusBadRequest, "invalid_request", "Invalid request payload")
		return false
	}
	return true
}

func DateRange(w http.ResponseWriter, r *http.Request) (time.Time, time.Time, bool) {
	from, err := time.Parse("2006-01-02", r.URL.Query().Get("from"))
	if err != nil {
		pkg.WriteError(w, http.StatusBadRequest, "invalid_date_range", "from must use YYYY-MM-DD")
		return time.Time{}, time.Time{}, false
	}
	to, err := time.Parse("2006-01-02", r.URL.Query().Get("to"))
	if err != nil || to.Before(from) {
		pkg.WriteError(w, http.StatusBadRequest, "invalid_date_range", "to must use YYYY-MM-DD and be after from")
		return time.Time{}, time.Time{}, false
	}
	return from, to.AddDate(0, 0, 1), true
}

func WriteError(w http.ResponseWriter, r *http.Request, err error) {
	middleware.QueueErrorLog(r.Context(), "workspace request failed", err)
	switch {
	case errors.Is(err, servicecommon.ErrNotFound):
		pkg.WriteError(w, http.StatusNotFound, "not_found", "Resource not found")
	case errors.Is(err, servicecommon.ErrForbidden):
		pkg.WriteError(w, http.StatusForbidden, "forbidden", "Operation is forbidden")
	case errors.Is(err, servicecommon.ErrConflict):
		pkg.WriteError(w, http.StatusConflict, "conflict", "Operation conflicts with current state")
	case errors.Is(err, r.Context().Err()):
		pkg.WriteError(w, http.StatusGatewayTimeout, "request_timeout", "Request timed out")
	default:
		pkg.WriteError(w, http.StatusInternalServerError, "internal_error", "Request failed")
	}
}
