package auth_service

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strings"
	"time"

	"kanban-board/internal/config"
	session_repo "kanban-board/internal/storage/repositories/session"
	users_repo "kanban-board/internal/storage/repositories/user"
	"kanban-board/pkg"
)

var ErrInvalidCredentials = errors.New("invalid login or password")

type AuthService struct {
	sessionRepo *session_repo.SessionRepo
	userRepo    *users_repo.UsersRepo
	jwtConfig   config.JWT
}

func Init(sessionRepo *session_repo.SessionRepo, userRepo *users_repo.UsersRepo, jwtConfig config.JWT) *AuthService {
	return &AuthService{
		sessionRepo: sessionRepo,
		userRepo:    userRepo,
		jwtConfig:   jwtConfig,
	}
}

func (s *AuthService) RegisterUser(ctx context.Context, username, email, password, groupName string) (string, error) {
	err := ValidateRegisterRequest(username, email, password, groupName)
	if err != nil {
		return "", err
	}

	passwordHash, err := HashPassword(password)
	if err != nil {
		return "", err
	}

	id, err := s.userRepo.CreateUser(ctx, username, email, groupName, "", passwordHash)
	if err != nil {
		return "", err
	}

	return id, nil
}

func (s *AuthService) LoginUser(ctx context.Context, login, password string) (string, string, string, error) {
	login = strings.TrimSpace(login)
	if login == "" || password == "" {
		return "", "", "", ErrInvalidCredentials
	}

	credentials, err := s.userRepo.GetUserCredentials(ctx, login)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return "", "", "", ErrInvalidCredentials
		}

		return "", "", "", fmt.Errorf("failed to get user credentials: %w", err)
	}

	if !VerifyPassword(password, credentials.PasswordHash) {
		return "", "", "", ErrInvalidCredentials
	}

	accessTokenTTL, err := time.ParseDuration(s.jwtConfig.AccessTokenTTL)
	if err != nil {
		return "", "", "", fmt.Errorf("invalid access token ttl: %w", err)
	}
	refreshTokenTTL, err := time.ParseDuration(s.jwtConfig.RefreshTokenTTL)
	if err != nil {
		return "", "", "", fmt.Errorf("invalid refresh token ttl: %w", err)
	}

	accessToken, _, err := pkg.CreateAccessToken(credentials.UserID, s.jwtConfig.AccessSecret, accessTokenTTL)
	if err != nil {
		return "", "", "", fmt.Errorf("failed to create access token: %w", err)
	}

	refreshToken, refreshExpiresAt, err := pkg.CreateRefreshToken(credentials.UserID, s.jwtConfig.RefreshSecret, refreshTokenTTL)
	if err != nil {
		return "", "", "", fmt.Errorf("failed to create refresh token: %w", err)
	}

	refreshTokenHash := pkg.HashToken(refreshToken)
	if err := s.sessionRepo.CreateSession(ctx, credentials.UserID, refreshTokenHash, refreshExpiresAt); err != nil {
		return "", "", "", fmt.Errorf("failed to create session: %w", err)
	}

	return credentials.UserID, accessToken, refreshToken, nil
}
