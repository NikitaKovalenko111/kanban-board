package session_repo

import (
	"context"
	"database/sql"
	"fmt"
	"kanban-board/internal/models"
	"time"
)

type SessionRepo struct {
	db  *sql.DB
	ctx context.Context
}

func Init(db *sql.DB, ctx context.Context) *SessionRepo {
	return &SessionRepo{
		db:  db,
		ctx: ctx,
	}
}

func (r *SessionRepo) CreateSession(userID string, refreshTokenHash string, expiresAt time.Time) (string, error) {
	var sessionID string
	err := r.db.QueryRowContext(r.ctx, `
		INSERT INTO sessions (session_id, user_id, refresh_token_hash, expires_at)
		VALUES (gen_random_uuid(), $1, $2, $3)
		RETURNING session_id
	`, userID, refreshTokenHash, expiresAt).Scan(&sessionID)
	if err != nil {
		return "", fmt.Errorf("failed to create session: %v", err)
	}

	return sessionID, nil
}

func (r *SessionRepo) GetActiveSessionByTokenHash(refreshTokenHash string) (*models.Session, error) {
	var session models.Session
	err := r.db.QueryRowContext(r.ctx, `
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

func (r *SessionRepo) GetActiveSessionsByUserID(userID string) ([]models.Session, error) {
	rows, err := r.db.QueryContext(r.ctx, `
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

func (r *SessionRepo) RotateRefreshToken(currentTokenHash string, newTokenHash string, expiresAt time.Time) error {
	result, err := r.db.ExecContext(r.ctx, `
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

func (r *SessionRepo) RevokeSessionByTokenHash(refreshTokenHash string) error {
	_, err := r.db.ExecContext(r.ctx, `
		UPDATE sessions
		SET revoked_at = CURRENT_TIMESTAMP
		WHERE refresh_token_hash = $1 AND revoked_at IS NULL
	`, refreshTokenHash)
	if err != nil {
		return fmt.Errorf("failed to revoke session: %v", err)
	}

	return nil
}

func (r *SessionRepo) RevokeAllUserSessions(userID string) error {
	_, err := r.db.ExecContext(r.ctx, `
		UPDATE sessions
		SET revoked_at = CURRENT_TIMESTAMP
		WHERE user_id = $1 AND revoked_at IS NULL
	`, userID)
	if err != nil {
		return fmt.Errorf("failed to revoke all user sessions: %v", err)
	}

	return nil
}

func (r *SessionRepo) DeleteExpiredSessions() error {
	_, err := r.db.ExecContext(r.ctx, `
		DELETE FROM sessions
		WHERE expires_at <= CURRENT_TIMESTAMP
			OR revoked_at IS NOT NULL
	`)
	if err != nil {
		return fmt.Errorf("failed to delete expired sessions: %v", err)
	}

	return nil
}
