package middleware

import (
	"context"
	"net/http"
	"time"
)

func Timeout(timeout time.Duration) func(http.Handler) http.Handler {
	return TimeoutExcept(timeout)
}

func TimeoutExcept(timeout time.Duration, excludedPaths ...string) func(http.Handler) http.Handler {
	excluded := make(map[string]struct{}, len(excludedPaths))
	for _, path := range excludedPaths {
		excluded[path] = struct{}{}
	}

	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if _, ok := excluded[r.URL.Path]; ok {
				next.ServeHTTP(w, r)
				return
			}
			ctx, cancel := context.WithTimeout(r.Context(), timeout)
			defer cancel()

			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}
