package session_repo

import (
	"context"
	"database/sql"
	"fmt"
	"kanban-board/internal/models"
	"time"
)

type SessionRepo struct {
	db *sql.DB
}

func Init(db *sql.DB) *SessionRepo {
	return &SessionRepo{db: db}
}

// used in auth_service.go

func (r *SessionRepo) CreateSession(ctx context.Context, userID string, refreshTokenHash string, expiresAt time.Time) error {
	_, err := r.db.ExecContext(ctx, `
		INSERT INTO sessions (session_id, user_id, refresh_token_hash, expires_at)
		VALUES (gen_random_uuid(), $1, $2, $3)
	`, userID, refreshTokenHash, expiresAt)
	if err != nil {
		return fmt.Errorf("failed to create session: %w", err)
	}

	return nil
}

func (r *SessionRepo) GetActiveSessionByTokenHash(ctx context.Context, refreshTokenHash string) (*models.Session, error) {
	var session models.Session
	err := r.db.QueryRowContext(ctx, `
		SELECT session_id, user_id, refresh_token_hash, expires_at, created_at, revoked_at
		FROM sessions
		WHERE refresh_token_hash = $1
			AND revoked_at IS NULL
			AND expires_at > CURRENT_TIMESTAMP
	`, refreshTokenHash).Scan(
		&session.SessionID,
		&session.UserID,
		&session.RefreshTokenHash,
		&session.ExpiresAt,
		&session.CreatedAt,
		&session.RevokedAt,
	)
	if err != nil {
		return nil, fmt.Errorf("failed to get active session: %v", err)
	}

	return &session, nil
}

func (r *SessionRepo) GetActiveSessionsByUserID(ctx context.Context, userID string) ([]models.Session, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT session_id, user_id, refresh_token_hash, expires_at, created_at, revoked_at
		FROM sessions
		WHERE user_id = $1
			AND revoked_at IS NULL
			AND expires_at > CURRENT_TIMESTAMP
		ORDER BY created_at DESC
	`, userID)
	if err != nil {
		return nil, fmt.Errorf("failed to get active user sessions: %v", err)
	}
	defer rows.Close()

	var sessions []models.Session
	for rows.Next() {
		var session models.Session
		if err := rows.Scan(
			&session.SessionID,
			&session.UserID,
			&session.RefreshTokenHash,
			&session.ExpiresAt,
			&session.CreatedAt,
			&session.RevokedAt,
		); err != nil {
			return nil, fmt.Errorf("failed to scan session: %v", err)
		}
		sessions = append(sessions, session)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("failed to iterate sessions: %v", err)
	}

	return sessions, nil
}

func (r *SessionRepo) RotateRefreshToken(ctx context.Context, currentTokenHash string, newTokenHash string, expiresAt time.Time) error {
	result, err := r.db.ExecContext(ctx, `
		UPDATE sessions
		SET refresh_token_hash = $1, expires_at = $2
		WHERE refresh_token_hash = $3
			AND revoked_at IS NULL
			AND expires_at > CURRENT_TIMESTAMP
	`, newTokenHash, expiresAt, currentTokenHash)
	if err != nil {
		return fmt.Errorf("failed to rotate refresh token: %v", err)
	}

	rowsAffected, err := result.RowsAffected()
	if err != nil {
		return fmt.Errorf("failed to get rotated session rows count: %v", err)
	}
	if rowsAffected == 0 {
		return fmt.Errorf("failed to rotate refresh token: %w", sql.ErrNoRows)
	}

	return nil
}

func (r *SessionRepo) RevokeSessionByTokenHash(ctx context.Context, refreshTokenHash string) error {
	_, err := r.db.ExecContext(ctx, `
		UPDATE sessions
		SET revoked_at = CURRENT_TIMESTAMP
		WHERE refresh_token_hash = $1 AND revoked_at IS NULL
	`, refreshTokenHash)
	if err != nil {
		return fmt.Errorf("failed to revoke session: %v", err)
	}

	return nil
}

func (r *SessionRepo) RevokeAllUserSessions(ctx context.Context, userID string) error {
	_, err := r.db.ExecContext(ctx, `
		UPDATE sessions
		SET revoked_at = CURRENT_TIMESTAMP
		WHERE user_id = $1 AND revoked_at IS NULL
	`, userID)
	if err != nil {
		return fmt.Errorf("failed to revoke all user sessions: %v", err)
	}

	return nil
}

func (r *SessionRepo) DeleteExpiredSessions(ctx context.Context) error {
	_, err := r.db.ExecContext(ctx, `
		DELETE FROM sessions
		WHERE expires_at <= CURRENT_TIMESTAMP
			OR revoked_at IS NOT NULL
	`)
	if err != nil {
		return fmt.Errorf("failed to delete expired sessions: %v", err)
	}

	return nil
}
