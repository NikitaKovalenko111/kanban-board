package auth_handler

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"

	"kanban-board/internal/middleware"
	auth_service "kanban-board/internal/services/auth"
	"kanban-board/pkg"
)

type AuthHandler struct {
	authService *auth_service.AuthService
}

func Init(authService *auth_service.AuthService) *AuthHandler {
	return &AuthHandler{
		authService: authService,
	}
}

func (h *AuthHandler) Start(mux *http.ServeMux) {
	mux.HandleFunc("POST /register", h.Register)
	mux.HandleFunc("POST /login", h.Login)
	mux.HandleFunc("POST /refresh", h.Refresh)
	mux.HandleFunc("POST /logout", h.Logout)
}

func (h *AuthHandler) Register(w http.ResponseWriter, r *http.Request) {
	var req RegisterRequest

	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		middleware.QueueErrorLog(r.Context(), "failed to decode register request", err)
		pkg.WriteError(w, http.StatusBadRequest, "invalid_request", "Invalid request payload")
		return
	}

	groupName := req.GroupName
	if groupName == "" {
		groupName = req.LegacyGroupName
	}
	userID, err := h.authService.RegisterUser(r.Context(), req.Username, req.Email, req.Password, groupName)
	if err != nil {
		middleware.QueueErrorLog(r.Context(), "registration failed", err)
		if errors.Is(err, context.DeadlineExceeded) {
			pkg.WriteError(w, http.StatusGatewayTimeout, "request_timeout", "Request timed out")
			return
		}
		if errors.Is(err, auth_service.ErrUsernameTaken) {
			pkg.WriteError(w, http.StatusConflict, "username_taken", "Username is already in use")
			return
		}
		if errors.Is(err, auth_service.ErrEmailTaken) {
			pkg.WriteError(w, http.StatusConflict, "email_taken", "Email is already in use")
			return
		}

		pkg.WriteError(w, http.StatusInternalServerError, "registration_failed", "Failed to register user")
		return
	}

	response := RegisterResponse{
		UserID:  userID,
		Message: "User registered successfully",
	}

	pkg.WriteJSON(w, http.StatusCreated, response)
}

func (h *AuthHandler) Login(w http.ResponseWriter, r *http.Request) {
	var req LoginRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		middleware.QueueErrorLog(r.Context(), "failed to decode login request", err)
		pkg.WriteError(w, http.StatusBadRequest, "invalid_request", "Invalid request payload")
		return
	}

	userID, accessToken, refreshToken, err := h.authService.LoginUser(r.Context(), req.Login, req.Password)
	if err != nil {
		middleware.QueueErrorLog(r.Context(), "login failed", err)
		if errors.Is(err, context.DeadlineExceeded) {
			pkg.WriteError(w, http.StatusGatewayTimeout, "request_timeout", "Request timed out")
			return
		}
		if errors.Is(err, auth_service.ErrInvalidCredentials) {
			pkg.WriteError(w, http.StatusUnauthorized, "invalid_credentials", "Invalid login or password")
			return
		}

		pkg.WriteError(w, http.StatusInternalServerError, "login_failed", "Failed to login")
		return
	}

	pkg.WriteJSON(w, http.StatusOK, newTokenResponse(userID, accessToken, refreshToken))
}

func (h *AuthHandler) Refresh(w http.ResponseWriter, r *http.Request) {
	var req RefreshRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		middleware.QueueErrorLog(r.Context(), "failed to decode refresh request", err)
		pkg.WriteError(w, http.StatusBadRequest, "invalid_request", "Invalid request payload")
		return
	}

	refreshTokenValue := req.RefreshToken
	if refreshTokenValue == "" {
		refreshTokenValue = req.LegacyRefreshToken
	}
	userID, accessToken, refreshToken, err := h.authService.RefreshTokens(r.Context(), refreshTokenValue)
	if err != nil {
		middleware.QueueErrorLog(r.Context(), "token refresh failed", err)
		if errors.Is(err, context.DeadlineExceeded) {
			pkg.WriteError(w, http.StatusGatewayTimeout, "request_timeout", "Request timed out")
			return
		}
		if errors.Is(err, auth_service.ErrInvalidRefreshToken) {
			pkg.WriteError(w, http.StatusUnauthorized, "invalid_refresh_token", "Invalid refresh token")
			return
		}

		pkg.WriteError(w, http.StatusInternalServerError, "refresh_failed", "Failed to refresh tokens")
		return
	}

	pkg.WriteJSON(w, http.StatusOK, newTokenResponse(userID, accessToken, refreshToken))
}

func (h *AuthHandler) Logout(w http.ResponseWriter, r *http.Request) {
	var req LogoutRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		middleware.QueueErrorLog(r.Context(), "failed to decode logout request", err)
		pkg.WriteError(w, http.StatusBadRequest, "invalid_request", "Invalid request payload")
		return
	}

	refreshTokenValue := req.RefreshToken
	if refreshTokenValue == "" {
		refreshTokenValue = req.LegacyRefreshToken
	}
	if err := h.authService.Logout(r.Context(), refreshTokenValue); err != nil {
		middleware.QueueErrorLog(r.Context(), "logout failed", err)
		if errors.Is(err, context.DeadlineExceeded) {
			pkg.WriteError(w, http.StatusGatewayTimeout, "request_timeout", "Request timed out")
			return
		}
		if errors.Is(err, auth_service.ErrInvalidRefreshToken) {
			pkg.WriteError(w, http.StatusUnauthorized, "invalid_refresh_token", "Invalid refresh token")
			return
		}

		pkg.WriteError(w, http.StatusInternalServerError, "logout_failed", "Failed to logout")
		return
	}

	pkg.WriteEmpty(w, http.StatusNoContent)
}

func newTokenResponse(userID, accessToken, refreshToken string) TokenResponse {
	return TokenResponse{
		UserID:       userID,
		AccessToken:  accessToken,
		RefreshToken: refreshToken,
		TokenType:    "Bearer",
	}
}
