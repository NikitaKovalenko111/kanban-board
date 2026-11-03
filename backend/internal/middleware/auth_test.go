package middleware

import (
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"kanban-board/pkg"
)

const (
	testAccessSecret  = "test-access-secret"
	testRefreshSecret = "test-refresh-secret"
	testUserID        = "user-123"
)

func TestAuthorizationAllowsValidAccessToken(t *testing.T) {
	accessToken, _, err := pkg.CreateAccessToken(testUserID, testAccessSecret, time.Minute)
	if err != nil {
		t.Fatalf("CreateAccessToken() error = %v", err)
	}

	handlerCalled := false
	handler := Authorization(testAccessSecret, testLogger())(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		handlerCalled = true
		userID, ok := UserIDFromContext(r.Context())
		if !ok {
			t.Fatal("UserIDFromContext() did not find user id")
		}
		if userID != testUserID {
			t.Fatalf("UserIDFromContext() = %q, want %q", userID, testUserID)
		}

		w.WriteHeader(http.StatusNoContent)
	}))

	request := httptest.NewRequest(http.MethodGet, "/protected", nil)
	request.Header.Set("Authorization", "Bearer "+accessToken)
	recorder := httptest.NewRecorder()
	handler.ServeHTTP(recorder, request)

	if !handlerCalled {
		t.Fatal("protected handler was not called")
	}
	if recorder.Code != http.StatusNoContent {
		t.Fatalf("response status = %d, want %d", recorder.Code, http.StatusNoContent)
	}
}

func TestAuthorizationRejectsInvalidTokens(t *testing.T) {
	refreshToken, _, err := pkg.CreateRefreshToken(testUserID, testRefreshSecret, time.Hour)
	if err != nil {
		t.Fatalf("CreateRefreshToken() error = %v", err)
	}

	tests := []struct {
		name                string
		authorizationHeader string
	}{
		{name: "missing header"},
		{name: "missing bearer scheme", authorizationHeader: "token"},
		{name: "wrong scheme", authorizationHeader: "Basic token"},
		{name: "invalid signature", authorizationHeader: "Bearer invalid-token"},
		{name: "refresh token", authorizationHeader: "Bearer " + refreshToken},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			handlerCalled := false
			handler := Authorization(testAccessSecret, testLogger())(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				handlerCalled = true
			}))

			request := httptest.NewRequest(http.MethodGet, "/protected", nil)
			if test.authorizationHeader != "" {
				request.Header.Set("Authorization", test.authorizationHeader)
			}
			recorder := httptest.NewRecorder()
			handler.ServeHTTP(recorder, request)

			if handlerCalled {
				t.Fatal("protected handler was called for an invalid token")
			}
			if recorder.Code != http.StatusUnauthorized {
				t.Fatalf("response status = %d, want %d", recorder.Code, http.StatusUnauthorized)
			}
		})
	}
}

func testLogger() *slog.Logger {
	return slog.New(slog.NewTextHandler(io.Discard, nil))
}
