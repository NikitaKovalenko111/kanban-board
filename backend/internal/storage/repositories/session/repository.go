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
	return &SessionRepo{
		db: db,
	}
}

// used in auth_service.go
func (r *SessionRepo) CreateSession(ctx context.Context, userID string, refreshTokenHash string, expiresAt time.Time) error {
	_, err := r.db.ExecContext(ctx, `
		INSERT INTO sessions (
			session_id,
			user_id,
			refresh_token_hash,
			expires_at
		)
		VALUES (gen_random_uuid(), $1, $2, $3)
	`, userID, refreshTokenHash, expiresAt)
	if err != nil {
		return fmt.Errorf("failed to create session: %w", err)
	}

	return nil
}

// used in auth_service.go
func (r *SessionRepo) GetActiveSessionByTokenHash(ctx context.Context, refreshTokenHash string) (*models.Session, error) {
	var session models.Session
	err := r.db.QueryRowContext(ctx, `
		SELECT
			session_id,
			user_id,
			refresh_token_hash,
			expires_at,
			created_at,
			revoked_at
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
		return nil, fmt.Errorf("failed to get active session: %w", err)
	}

	return &session, nil
}

func (r *SessionRepo) RotateRefreshToken(ctx context.Context, currentTokenHash string, newTokenHash string, expiresAt time.Time) error {
	result, err := r.db.ExecContext(ctx, `
		UPDATE sessions
		SET
			refresh_token_hash = $1,
			expires_at = $2
		WHERE refresh_token_hash = $3
			AND revoked_at IS NULL
			AND expires_at > CURRENT_TIMESTAMP
	`, newTokenHash, expiresAt, currentTokenHash)
	if err != nil {
		return fmt.Errorf("failed to rotate refresh token: %w", err)
	}

	rowsAffected, err := result.RowsAffected()
	if err != nil {
		return fmt.Errorf("failed to get rotated session rows count: %w", err)
	}
	if rowsAffected == 0 {
		return fmt.Errorf("failed to rotate refresh token: %w", sql.ErrNoRows)
	}

	return nil
}

// used in auth_service.go
func (r *SessionRepo) RevokeSessionByTokenHash(ctx context.Context, refreshTokenHash string) error {
	result, err := r.db.ExecContext(ctx, `
		UPDATE sessions
		SET revoked_at = CURRENT_TIMESTAMP
		WHERE refresh_token_hash = $1
			AND revoked_at IS NULL
			AND expires_at > CURRENT_TIMESTAMP
	`, refreshTokenHash)
	if err != nil {
		return fmt.Errorf("failed to revoke session: %w", err)
	}

	rowsAffected, err := result.RowsAffected()
	if err != nil {
		return fmt.Errorf("failed to get revoked session rows count: %w", err)
	}
	if rowsAffected == 0 {
		return fmt.Errorf("failed to revoke session: %w", sql.ErrNoRows)
	}

	return nil
}

func (r *SessionRepo) DeleteInactiveSessions(ctx context.Context) (int64, error) {
	result, err := r.db.ExecContext(ctx, `
		DELETE FROM sessions
		WHERE expires_at <= CURRENT_TIMESTAMP
			OR revoked_at IS NOT NULL
	`)
	if err != nil {
		return 0, fmt.Errorf("failed to delete inactive sessions: %w", err)
	}

	rowsAffected, err := result.RowsAffected()
	if err != nil {
		return 0, fmt.Errorf("failed to get deleted sessions rows count: %w", err)
	}

	return rowsAffected, nil
}
