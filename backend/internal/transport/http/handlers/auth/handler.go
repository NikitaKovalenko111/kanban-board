package auth_handler

import (
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"

	auth_service "kanban-board/internal/services/auth"
	"kanban-board/pkg"
)

type AuthHandler struct {
	authService *auth_service.AuthService
	logger      *slog.Logger
}

func Init(authService *auth_service.AuthService, logger *slog.Logger) *AuthHandler {
	return &AuthHandler{
		authService: authService,
		logger:      logger,
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
		pkg.WriteError(w, http.StatusBadRequest, "invalid_request", "Invalid request payload")
		return
	}

	userID, err := h.authService.RegisterUser(r.Context(), req.Username, req.Email, req.Password, req.GroupName)
	if err != nil {
		if errors.Is(err, context.DeadlineExceeded) {
			pkg.WriteError(w, http.StatusGatewayTimeout, "request_timeout", "Request timed out")
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
		pkg.WriteError(w, http.StatusBadRequest, "invalid_request", "Invalid request payload")
		return
	}

	userID, accessToken, refreshToken, err := h.authService.LoginUser(r.Context(), req.Login, req.Password)
	if err != nil {
		if errors.Is(err, context.DeadlineExceeded) {
			pkg.WriteError(w, http.StatusGatewayTimeout, "request_timeout", "Request timed out")
			return
		}
		if errors.Is(err, auth_service.ErrInvalidCredentials) {
			pkg.WriteError(w, http.StatusUnauthorized, "invalid_credentials", "Invalid login or password")
			return
		}

		h.logger.Error("failed to login user", "error", err)
		pkg.WriteError(w, http.StatusInternalServerError, "login_failed", "Failed to login")
		return
	}

	pkg.WriteJSON(w, http.StatusOK, newTokenResponse(userID, accessToken, refreshToken))
}

func (h *AuthHandler) Refresh(w http.ResponseWriter, r *http.Request) {
	var req RefreshRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		pkg.WriteError(w, http.StatusBadRequest, "invalid_request", "Invalid request payload")
		return
	}

	userID, accessToken, refreshToken, err := h.authService.RefreshTokens(r.Context(), req.RefreshToken)
	if err != nil {
		if errors.Is(err, context.DeadlineExceeded) {
			pkg.WriteError(w, http.StatusGatewayTimeout, "request_timeout", "Request timed out")
			return
		}
		if errors.Is(err, auth_service.ErrInvalidRefreshToken) {
			pkg.WriteError(w, http.StatusUnauthorized, "invalid_refresh_token", "Invalid refresh token")
			return
		}

		h.logger.Error("failed to refresh tokens", "error", err)
		pkg.WriteError(w, http.StatusInternalServerError, "refresh_failed", "Failed to refresh tokens")
		return
	}

	pkg.WriteJSON(w, http.StatusOK, newTokenResponse(userID, accessToken, refreshToken))
}

func (h *AuthHandler) Logout(w http.ResponseWriter, r *http.Request) {
	var req LogoutRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		pkg.WriteError(w, http.StatusBadRequest, "invalid_request", "Invalid request payload")
		return
	}

	if err := h.authService.Logout(r.Context(), req.RefreshToken); err != nil {
		if errors.Is(err, context.DeadlineExceeded) {
			pkg.WriteError(w, http.StatusGatewayTimeout, "request_timeout", "Request timed out")
			return
		}
		if errors.Is(err, auth_service.ErrInvalidRefreshToken) {
			pkg.WriteError(w, http.StatusUnauthorized, "invalid_refresh_token", "Invalid refresh token")
			return
		}

		h.logger.Error("failed to logout user", "error", err)
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
