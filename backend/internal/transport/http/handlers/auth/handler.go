package auth_handler

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"

	"kanban-board/internal/middleware"
	auth_service "kanban-board/internal/services/auth"
	profile_service "kanban-board/internal/services/profile"
	handlercommon "kanban-board/internal/transport/http/handlers/common"
	"kanban-board/pkg"
)

type AuthHandler struct {
	authService *auth_service.AuthService
	profile     *profile_service.Service
}

func Init(authService *auth_service.AuthService, profiles ...*profile_service.Service) *AuthHandler {
	handler := &AuthHandler{authService: authService}
	if len(profiles) > 0 {
		handler.profile = profiles[0]
	}

	return handler
}

func (h *AuthHandler) StartPublic(mux *http.ServeMux) {
	// Самостоятельная регистрация отключена: аккаунты создаёт капитан через POST /api/v1/users.
	// mux.HandleFunc("POST /register", h.Register)
	mux.HandleFunc("POST /login", h.Login)
	mux.HandleFunc("POST /refresh", h.Refresh)
}

func (h *AuthHandler) StartProtected(mux *http.ServeMux) {
	mux.HandleFunc("POST /auth/logout", h.Logout)
	mux.HandleFunc("POST /auth/change-password", h.ChangePassword)
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
		pkg.WriteError(w, http.StatusBadRequest, "VALIDATION", "Некорректное тело запроса")
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
			pkg.WriteError(w, http.StatusUnauthorized, "INVALID_CREDENTIALS", "Неверный логин или пароль")
			return
		}
		if errors.Is(err, auth_service.ErrAccountBlocked) {
			pkg.WriteError(w, http.StatusForbidden, "FORBIDDEN", "Доступ к аккаунту закрыт")
			return
		}

		pkg.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", "Не удалось выполнить вход")
		return
	}

	profile, err := h.profile.GetProfile(r.Context(), userID)
	if err != nil {
		handlercommon.WriteError(w, r, err)
		return
	}
	response := newTokenResponse(accessToken, refreshToken, h.authService.AccessTokenExpiresIn())
	response.User = profile
	pkg.WriteJSON(w, http.StatusOK, response)
}

func (h *AuthHandler) Refresh(w http.ResponseWriter, r *http.Request) {
	var req RefreshRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		middleware.QueueErrorLog(r.Context(), "failed to decode refresh request", err)
		pkg.WriteError(w, http.StatusBadRequest, "VALIDATION", "Некорректное тело запроса")
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
			pkg.WriteError(w, http.StatusUnauthorized, "INVALID_REFRESH_TOKEN", "Refresh-токен недействителен")
			return
		}

		pkg.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", "Не удалось обновить токены")
		return
	}

	_ = userID
	pkg.WriteJSON(w, http.StatusOK, newTokenResponse(accessToken, refreshToken, h.authService.AccessTokenExpiresIn()))
}

func (h *AuthHandler) Logout(w http.ResponseWriter, r *http.Request) {
	var req LogoutRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		middleware.QueueErrorLog(r.Context(), "failed to decode logout request", err)
		pkg.WriteError(w, http.StatusBadRequest, "VALIDATION", "Некорректное тело запроса")
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
			pkg.WriteError(w, http.StatusUnauthorized, "INVALID_REFRESH_TOKEN", "Refresh-токен недействителен")
			return
		}

		pkg.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", "Не удалось завершить сессию")
		return
	}

	pkg.WriteEmpty(w, http.StatusNoContent)
}

func (h *AuthHandler) ChangePassword(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.UserIDFromContext(r.Context())
	if !ok {
		return
	}
	var request ChangePasswordRequest
	if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
		pkg.WriteError(w, http.StatusBadRequest, "VALIDATION", "Некорректное тело запроса")
		return
	}
	if err := h.authService.ChangePassword(r.Context(), userID, request.CurrentPassword, request.NewPassword); err != nil {
		if errors.Is(err, auth_service.ErrInvalidCredentials) {
			pkg.WriteError(w, http.StatusUnauthorized, "INVALID_CREDENTIALS", "Текущий пароль указан неверно")
			return
		}
		pkg.WriteError(w, http.StatusBadRequest, "VALIDATION", err.Error())
		return
	}
	pkg.WriteEmpty(w, http.StatusNoContent)
}

func newTokenResponse(accessToken, refreshToken string, expiresIn int64) TokenResponse {
	return TokenResponse{
		AccessToken:  accessToken,
		RefreshToken: refreshToken,
		ExpiresIn:    expiresIn,
	}
}
