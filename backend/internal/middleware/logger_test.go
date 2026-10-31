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

func TestLoggerWritesRequestFailureAndDetailsInOneRecord(t *testing.T) {
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

	logs := strings.TrimSpace(output.String())
	if !strings.Contains(logs, "REQUEST FAILED") {
		t.Fatal("logs do not contain REQUEST FAILED")
	}
	if !strings.Contains(logs, "detail=\"database operation failed\"") {
		t.Fatalf("logs do not contain the detailed message:\n%s", logs)
	}
	if !strings.Contains(logs, "error=\"connection refused\"") {
		t.Fatalf("logs do not contain the error:\n%s", logs)
	}
	if strings.Contains(logs, "\n") {
		t.Fatalf("request failure was written as multiple log records:\n%s", logs)
	}
}
