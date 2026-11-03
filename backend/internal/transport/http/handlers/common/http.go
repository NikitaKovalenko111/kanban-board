package handlercommon

import (
	"encoding/json"
	"errors"
	"net/http"
	"strings"
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
		pkg.WriteError(w, http.StatusBadRequest, "VALIDATION", "Некорректное тело запроса")
		return false
	}
	return true
}

func DateRange(w http.ResponseWriter, r *http.Request) (time.Time, time.Time, bool) {
	from, err := time.Parse("2006-01-02", r.URL.Query().Get("from"))
	if err != nil {
		pkg.WriteError(w, http.StatusBadRequest, "VALIDATION", "Параметр from должен иметь формат YYYY-MM-DD")
		return time.Time{}, time.Time{}, false
	}
	to, err := time.Parse("2006-01-02", r.URL.Query().Get("to"))
	if err != nil || to.Before(from) {
		pkg.WriteError(w, http.StatusBadRequest, "VALIDATION", "Параметр to должен быть позже from и иметь формат YYYY-MM-DD")
		return time.Time{}, time.Time{}, false
	}
	return from, to.AddDate(0, 0, 1), true
}

func WriteError(w http.ResponseWriter, r *http.Request, err error) {
	middleware.QueueErrorLog(r.Context(), "workspace request failed", err)
	switch {
	case errors.Is(err, servicecommon.ErrInvalidInput):
		pkg.WriteError(w, http.StatusBadRequest, "VALIDATION", err.Error())
	case errors.Is(err, servicecommon.ErrNotFound):
		pkg.WriteError(w, http.StatusNotFound, "NOT_FOUND", "Объект не найден")
	case errors.Is(err, servicecommon.ErrForbidden):
		pkg.WriteError(w, http.StatusForbidden, "FORBIDDEN", "Недостаточно прав для выполнения операции")
	case errors.Is(err, servicecommon.ErrConflict):
		pkg.WriteError(w, http.StatusConflict, "CONFLICT", errorDetail(err, "Операция конфликтует с текущим состоянием"))
	case errors.Is(err, r.Context().Err()):
		pkg.WriteError(w, http.StatusGatewayTimeout, "REQUEST_TIMEOUT", "Время ожидания запроса истекло")
	default:
		pkg.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", "Не удалось выполнить запрос")
	}
}

func errorDetail(err error, fallback string) string {
	message := err.Error()
	if separator := strings.Index(message, ": "); separator >= 0 && separator+2 < len(message) {
		return message[separator+2:]
	}

	return fallback
}
