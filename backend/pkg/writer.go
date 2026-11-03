package pkg

import (
	"encoding/json"
	"net/http"
	"strings"

	"kanban-board/internal/models"
)

func WriteJSON(w http.ResponseWriter, status int, data any) error {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)

	return json.NewEncoder(w).Encode(data)
}

func WriteEmpty(w http.ResponseWriter, status int) {
	w.WriteHeader(status)
}

func WriteError(w http.ResponseWriter, status int, code, message string, fields ...map[string]string) {
	var validationFields map[string]string
	if len(fields) > 0 {
		validationFields = fields[0]
	}
	response := models.ErrorResponse{
		Error: models.ErrorDetail{
			Code:    strings.ToUpper(code),
			Message: message,
			Fields:  validationFields,
		},
	}

	WriteJSON(w, status, response)
}
