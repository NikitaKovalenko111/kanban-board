package pkg

import (
	"encoding/json"
	"kanban-board/internal/models"
	"net/http"
)

func WriteJSON(w http.ResponseWriter, status int, data any) error {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)

	return json.NewEncoder(w).Encode(data)
}

func WriteEmpty(w http.ResponseWriter, status int) {
	w.WriteHeader(status)
}

func WriteError(w http.ResponseWriter, status int, code string, message string) {
	response := models.ErrorResponse{
		Message: message,
		Error:   code,
	}

	WriteJSON(w, status, response)
}
