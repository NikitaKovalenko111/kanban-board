package auth_handler

import (
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
}

func (h *AuthHandler) Register(w http.ResponseWriter, r *http.Request) {
	var req RegisterRequest

	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		pkg.WriteError(w, http.StatusBadRequest, "invalid_request", "Invalid request payload")
		return
	}

	userID, err := h.authService.RegisterUser(req.Username, req.Email, req.Password, req.GroupName)
	if err != nil {
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

	userID, accessToken, refreshToken, err := h.authService.LoginUser(req.Login, req.Password)
	if err != nil {
		if errors.Is(err, auth_service.ErrInvalidCredentials) {
			pkg.WriteError(w, http.StatusUnauthorized, "invalid_credentials", "Invalid login or password")
			return
		}

		h.logger.Error("failed to login user", "error", err)
		pkg.WriteError(w, http.StatusInternalServerError, "login_failed", "Failed to login")
		return
	}

	response := LoginResponse{
		UserID:       userID,
		AccessToken:  accessToken,
		RefreshToken: refreshToken,
		TokenType:    "Bearer",
	}

	pkg.WriteJSON(w, http.StatusOK, response)
}
