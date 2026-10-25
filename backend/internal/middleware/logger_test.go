package middleware

import (
	"bytes"
	"errors"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestLoggerWritesRequestFailureBeforeDetailedError(t *testing.T) {
	var output bytes.Buffer
	logger := slog.New(slog.NewTextHandler(&output, nil))
	handler := Logger(logger)(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !QueueErrorLog(r.Context(), "database operation failed", errors.New("connection refused")) {
			t.Fatal("QueueErrorLog() did not find request error log storage")
		}
		http.Error(w, "internal error", http.StatusInternalServerError)
	}))

	request := httptest.NewRequest(http.MethodGet, "/test", nil)
	recorder := httptest.NewRecorder()
	handler.ServeHTTP(recorder, request)

	logs := output.String()
	requestFailedIndex := strings.Index(logs, "REQUEST FAILED")
	detailedErrorIndex := strings.Index(logs, "database operation failed")
	if requestFailedIndex == -1 {
		t.Fatal("logs do not contain REQUEST FAILED")
	}
	if detailedErrorIndex == -1 {
		t.Fatal("logs do not contain the detailed error")
	}
	if requestFailedIndex > detailedErrorIndex {
		t.Fatalf("detailed error was logged before REQUEST FAILED:\n%s", logs)
	}
}
