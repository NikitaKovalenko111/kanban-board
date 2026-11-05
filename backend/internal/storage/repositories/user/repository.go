package users_repo

import (
	"context"
	"database/sql"
	"fmt"

	"kanban-board/internal/models"
)

type UsersRepo struct {
	db *sql.DB
}

func Init(db *sql.DB) *UsersRepo {
	return &UsersRepo{
		db: db,
	}
}

// used in auth_service.go
func (r *UsersRepo) CreateUser(ctx context.Context, username, email, groupName, imageURL, passwordHash string) (string, error) {
	var userID string
	err := r.db.QueryRowContext(ctx, `
		WITH selected_group AS (
			INSERT INTO student_groups (
				student_group_id,
				group_code,
				name
			)
			VALUES (gen_random_uuid(), $3, $3)
			ON CONFLICT (group_code)
			DO UPDATE SET updated_at = CURRENT_TIMESTAMP
			RETURNING student_group_id
		)
		INSERT INTO users (
			user_id,
			username,
			email,
			display_name,
			student_group_id,
			image_url,
			password_hash
		)
		SELECT
			gen_random_uuid(),
			$1,
			$2,
			$1,
			student_group_id,
			$4,
			$5
		FROM selected_group
		RETURNING user_id
	`, username, email, groupName, imageURL, passwordHash).Scan(&userID)
	if err != nil {
		return "", fmt.Errorf("failed to create user: %w", err)
	}
	return userID, nil
}

func (r *UsersRepo) GetUserCredentials(ctx context.Context, login string) (*models.UserCredentials, error) {
	var credentials models.UserCredentials
	err := r.db.QueryRowContext(ctx, `
		SELECT
			user_id,
			username,
			COALESCE(email, ''),
			password_hash,
			is_active
		FROM users
		WHERE LOWER(COALESCE(email, '')) = LOWER($1)
			OR LOWER(username) = LOWER($1)
		LIMIT 1
	`, login).Scan(&credentials.UserID, &credentials.Username, &credentials.Email, &credentials.PasswordHash, &credentials.IsActive)
	if err != nil {
		return nil, fmt.Errorf("failed to select user credentials: %w", err)
	}
	return &credentials, nil
}

func (r *UsersRepo) GetPasswordHash(ctx context.Context, userID string) (string, error) {
	var passwordHash string
	err := r.db.QueryRowContext(ctx, `
		SELECT password_hash
		FROM users
		WHERE user_id = $1 AND is_active = TRUE
	`, userID).Scan(&passwordHash)
	return passwordHash, err
}

func (r *UsersRepo) ChangePassword(ctx context.Context, userID, passwordHash string) error {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	result, err := tx.ExecContext(ctx, `
		UPDATE users
		SET password_hash = $1, updated_at = CURRENT_TIMESTAMP
		WHERE user_id = $2 AND is_active = TRUE
	`, passwordHash, userID)
	if err != nil {
		return err
	}
	count, err := result.RowsAffected()
	if err != nil {
		return err
	}
	if count == 0 {
		return sql.ErrNoRows
	}
	if _, err := tx.ExecContext(ctx, `
		UPDATE sessions
		SET revoked_at = CURRENT_TIMESTAMP
		WHERE user_id = $1 AND revoked_at IS NULL
	`, userID); err != nil {
		return err
	}

	return tx.Commit()
}

func (r *UsersRepo) IsUserActive(ctx context.Context, userID string) (bool, error) {
	var active bool
	err := r.db.QueryRowContext(ctx, `
		SELECT is_active
		FROM users
		WHERE user_id = $1
	`, userID).Scan(&active)
	if err != nil {
		return false, err
	}

	return active, nil
}
