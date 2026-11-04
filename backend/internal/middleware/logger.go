package middleware

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"sync"
	"time"
)

type responseWriter struct {
	http.ResponseWriter
	status int
}

type errorLog struct {
	message string
	err     error
}

type requestErrorLogs struct {
	mu      sync.Mutex
	entries []errorLog
}

type requestErrorLogsContextKey struct{}

func (rw *responseWriter) WriteHeader(code int) {
	if rw.status != 0 {
		return
	}

	rw.status = code
	rw.ResponseWriter.WriteHeader(code)
}

func (rw *responseWriter) Write(data []byte) (int, error) {
	if rw.status == 0 {
		rw.WriteHeader(http.StatusOK)
	}

	return rw.ResponseWriter.Write(data)
}

func Logger(log *slog.Logger) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			start := time.Now()
			errorLogs := &requestErrorLogs{}
			ctx := context.WithValue(r.Context(), requestErrorLogsContextKey{}, errorLogs)

			rw := &responseWriter{
				ResponseWriter: w,
			}

			next.ServeHTTP(rw, r.WithContext(ctx))

			status := rw.status
			if status == 0 {
				status = http.StatusOK
			}

			attrs := []any{
				slog.String("method", r.Method),
				slog.String("path", r.URL.Path),
				slog.Int("status", status),
				slog.Duration("duration", time.Since(start)),
			}
			attrs = appendErrorLogAttributes(attrs, errorLogs.all())

			switch {
			case status >= 500:
				log.Error("REQUEST FAILED", attrs...)
			case status >= 400:
				log.Warn("REQUEST FAILED", attrs...)
			default:
				log.Info("REQUEST COMPLETED", attrs...)
			}
		})
	}
}

func QueueErrorLog(ctx context.Context, message string, err error) bool {
	errorLogs, ok := ctx.Value(requestErrorLogsContextKey{}).(*requestErrorLogs)
	if !ok {
		return false
	}

	errorLogs.mu.Lock()
	defer errorLogs.mu.Unlock()

	errorLogs.entries = append(errorLogs.entries, errorLog{
		message: message,
		err:     err,
	})

	return true
}

func (logs *requestErrorLogs) all() []errorLog {
	logs.mu.Lock()
	defer logs.mu.Unlock()

	entries := make([]errorLog, len(logs.entries))
	copy(entries, logs.entries)

	return entries
}

func appendErrorLogAttributes(attrs []any, entries []errorLog) []any {
	if len(entries) == 1 {
		return append(
			attrs,
			slog.String("detail", entries[0].message),
			slog.Any("error", entries[0].err),
		)
	}

	for index, entry := range entries {
		suffix := index + 1
		attrs = append(
			attrs,
			slog.String(fmt.Sprintf("detail_%d", suffix), entry.message),
			slog.Any(fmt.Sprintf("error_%d", suffix), entry.err),
		)
	}

	return attrs
}
