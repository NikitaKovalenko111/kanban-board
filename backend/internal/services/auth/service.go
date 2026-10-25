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

	"github.com/jackc/pgx/v5/pgconn"
)

var (
	ErrInvalidCredentials  = errors.New("invalid login or password")
	ErrUsernameTaken       = errors.New("username is already in use")
	ErrEmailTaken          = errors.New("email is already in use")
	ErrInvalidRefreshToken = errors.New("invalid refresh token")
)

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
		if conflictErr := registrationConflictError(err); conflictErr != nil {
			return "", conflictErr
		}

		return "", err
	}

	return id, nil
}

func registrationConflictError(err error) error {
	var pgError *pgconn.PgError
	if !errors.As(err, &pgError) || pgError.Code != "23505" {
		return nil
	}

	switch pgError.ConstraintName {
	case "users_username_idx":
		return ErrUsernameTaken
	case "users_email_idx":
		return ErrEmailTaken
	default:
		return nil
	}
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

	accessToken, refreshToken, refreshExpiresAt, err := s.createTokenPair(credentials.UserID)
	if err != nil {
		return "", "", "", err
	}

	refreshTokenHash := pkg.HashToken(refreshToken)
	if err := s.sessionRepo.CreateSession(ctx, credentials.UserID, refreshTokenHash, refreshExpiresAt); err != nil {
		return "", "", "", fmt.Errorf("failed to create session: %w", err)
	}

	return credentials.UserID, accessToken, refreshToken, nil
}

func (s *AuthService) RefreshTokens(ctx context.Context, currentRefreshToken string) (string, string, string, error) {
	currentRefreshToken = strings.TrimSpace(currentRefreshToken)
	claims, err := pkg.ValidateRefreshToken(currentRefreshToken, s.jwtConfig.RefreshSecret)
	if err != nil {
		return "", "", "", ErrInvalidRefreshToken
	}

	currentTokenHash := pkg.HashToken(currentRefreshToken)
	session, err := s.sessionRepo.GetActiveSessionByTokenHash(ctx, currentTokenHash)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return "", "", "", ErrInvalidRefreshToken
		}

		return "", "", "", fmt.Errorf("failed to get active session: %w", err)
	}
	if session.UserID != claims.Subject {
		return "", "", "", ErrInvalidRefreshToken
	}

	accessToken, refreshToken, refreshExpiresAt, err := s.createTokenPair(session.UserID)
	if err != nil {
		return "", "", "", err
	}

	newTokenHash := pkg.HashToken(refreshToken)
	if err := s.sessionRepo.RotateRefreshToken(ctx, currentTokenHash, newTokenHash, refreshExpiresAt); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return "", "", "", ErrInvalidRefreshToken
		}

		return "", "", "", fmt.Errorf("failed to rotate refresh token: %w", err)
	}

	return session.UserID, accessToken, refreshToken, nil
}

func (s *AuthService) Logout(ctx context.Context, refreshToken string) error {
	refreshToken = strings.TrimSpace(refreshToken)
	if _, err := pkg.ValidateRefreshToken(refreshToken, s.jwtConfig.RefreshSecret); err != nil {
		return ErrInvalidRefreshToken
	}

	refreshTokenHash := pkg.HashToken(refreshToken)
	if err := s.sessionRepo.RevokeSessionByTokenHash(ctx, refreshTokenHash); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return ErrInvalidRefreshToken
		}

		return fmt.Errorf("failed to revoke session: %w", err)
	}

	return nil
}

func (s *AuthService) createTokenPair(userID string) (string, string, time.Time, error) {
	accessTokenTTL, err := time.ParseDuration(s.jwtConfig.AccessTokenTTL)
	if err != nil {
		return "", "", time.Time{}, fmt.Errorf("invalid access token ttl: %w", err)
	}
	refreshTokenTTL, err := time.ParseDuration(s.jwtConfig.RefreshTokenTTL)
	if err != nil {
		return "", "", time.Time{}, fmt.Errorf("invalid refresh token ttl: %w", err)
	}

	accessToken, _, err := pkg.CreateAccessToken(userID, s.jwtConfig.AccessSecret, accessTokenTTL)
	if err != nil {
		return "", "", time.Time{}, fmt.Errorf("failed to create access token: %w", err)
	}

	refreshToken, refreshExpiresAt, err := pkg.CreateRefreshToken(userID, s.jwtConfig.RefreshSecret, refreshTokenTTL)
	if err != nil {
		return "", "", time.Time{}, fmt.Errorf("failed to create refresh token: %w", err)
	}

	return accessToken, refreshToken, refreshExpiresAt, nil
}
