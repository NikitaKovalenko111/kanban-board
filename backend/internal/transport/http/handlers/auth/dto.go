package auth_handler

import "kanban-board/internal/models"

type RegisterRequest struct {
	Username        string `json:"username"`
	Email           string `json:"email"`
	Password        string `json:"password"`
	GroupName       string `json:"studentGroup"`
	LegacyGroupName string `json:"group_name"`
}

type RegisterResponse struct {
	UserID  string `json:"userId"`
	Message string `json:"message"`
}

type LoginRequest struct {
	Login    string `json:"login"`
	Password string `json:"password"`
}

type TokenResponse struct {
	AccessToken  string          `json:"accessToken"`
	RefreshToken string          `json:"refreshToken"`
	ExpiresIn    int64           `json:"expiresIn"`
	User         *models.Profile `json:"user,omitempty"`
}

type RefreshRequest struct {
	RefreshToken       string `json:"refreshToken"`
	LegacyRefreshToken string `json:"refresh_token"`
}

type LogoutRequest struct {
	RefreshToken       string `json:"refreshToken"`
	LegacyRefreshToken string `json:"refresh_token"`
}

type ChangePasswordRequest struct {
	CurrentPassword string `json:"currentPassword"`
	NewPassword     string `json:"newPassword"`
}
