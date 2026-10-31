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

const userSelect = `
	SELECT
		u.user_id,
		u.username,
		u.email,
		u.display_name,
		COALESCE(u.position, ''),
		u.student_group_id,
		COALESCE(sg.group_code, ''),
		COALESCE(u.image_url, ''),
		u.is_active,
		u.created_at,
		u.updated_at
	FROM users u
	LEFT JOIN student_groups sg ON sg.student_group_id = u.student_group_id
`

func scanUser(row interface{ Scan(...any) error }) (*models.User, error) {
	var user models.User
	err := row.Scan(&user.UserID, &user.Username, &user.Email, &user.DisplayName, &user.Position,
		&user.StudentGroupID, &user.GroupName, &user.ImageURL, &user.IsActive, &user.CreatedAt, &user.UpdatedAt)
	return &user, err
}

func (r *UsersRepo) GetUserByID(ctx context.Context, userID string) (*models.User, error) {
	user, err := scanUser(r.db.QueryRowContext(ctx, userSelect+`
		WHERE u.user_id = $1
	`, userID))
	if err != nil {
		return nil, fmt.Errorf("failed to select user by id: %w", err)
	}
	return user, nil
}

func (r *UsersRepo) GetUserByEmail(ctx context.Context, email string) (*models.User, error) {
	user, err := scanUser(r.db.QueryRowContext(ctx, userSelect+`
		WHERE u.email = $1
	`, email))
	if err != nil {
		return nil, fmt.Errorf("failed to select user by email: %w", err)
	}
	return user, nil
}

func (r *UsersRepo) GetUserByUsername(ctx context.Context, username string) (*models.User, error) {
	user, err := scanUser(r.db.QueryRowContext(ctx, userSelect+`
		WHERE u.username = $1
	`, username))
	if err != nil {
		return nil, fmt.Errorf("failed to select user by username: %w", err)
	}
	return user, nil
}

// used in auth_service.go
func (r *UsersRepo) GetUserCredentials(ctx context.Context, login string) (*models.UserCredentials, error) {
	var credentials models.UserCredentials
	err := r.db.QueryRowContext(ctx, `
		SELECT
			user_id,
			username,
			email,
			password_hash,
			is_active
		FROM users
		WHERE email = $1
			OR username = $1
		LIMIT 1
	`, login).Scan(&credentials.UserID, &credentials.Username, &credentials.Email, &credentials.PasswordHash, &credentials.IsActive)
	if err != nil {
		return nil, fmt.Errorf("failed to select user credentials: %w", err)
	}
	return &credentials, nil
}

func (r *UsersRepo) GetUsersByGroupName(ctx context.Context, groupName string) ([]models.User, error) {
	rows, err := r.db.QueryContext(ctx, userSelect+`
		WHERE sg.group_code = $1
		ORDER BY u.display_name
	`, groupName)
	if err != nil {
		return nil, fmt.Errorf("failed to select users by group name: %w", err)
	}
	defer rows.Close()
	users := make([]models.User, 0)
	for rows.Next() {
		user, err := scanUser(rows)
		if err != nil {
			return nil, fmt.Errorf("failed to scan user: %w", err)
		}
		users = append(users, *user)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("failed to iterate users: %w", err)
	}
	return users, nil
}

func (r *UsersRepo) UpdateUser(ctx context.Context, userID, username, groupName, imageURL string) error {
	_, err := r.db.ExecContext(ctx, `
		WITH selected_group AS (
			INSERT INTO student_groups (
				student_group_id,
				group_code,
				name
			)
			VALUES (gen_random_uuid(), $2, $2)
			ON CONFLICT (group_code)
			DO UPDATE SET updated_at = CURRENT_TIMESTAMP
			RETURNING student_group_id
		)
		UPDATE users
		SET
			username = $1,
			student_group_id = selected_group.student_group_id,
			image_url = $3,
			updated_at = CURRENT_TIMESTAMP
		FROM selected_group
		WHERE user_id = $4
	`, username, groupName, imageURL, userID)
	if err != nil {
		return fmt.Errorf("failed to update user: %w", err)
	}
	return nil
}

func (r *UsersRepo) UpdateProfile(ctx context.Context, userID, displayName, position, groupCode, imageURL string) error {
	_, err := r.db.ExecContext(ctx, `
		WITH selected_group AS (
			INSERT INTO student_groups (
				student_group_id,
				group_code,
				name
			)
			SELECT
				gen_random_uuid(),
				NULLIF($3, ''),
				NULLIF($3, '')
			WHERE NULLIF($3, '') IS NOT NULL
			ON CONFLICT (group_code)
			DO UPDATE SET updated_at = CURRENT_TIMESTAMP
			RETURNING student_group_id
		)
		UPDATE users
		SET
			display_name = $1,
			position = NULLIF($2, ''),
			student_group_id = (
				SELECT student_group_id
				FROM selected_group
			),
			image_url = NULLIF($4, ''),
			updated_at = CURRENT_TIMESTAMP
		WHERE user_id = $5
	`, displayName, position, groupCode, imageURL, userID)
	if err != nil {
		return fmt.Errorf("failed to update profile: %w", err)
	}
	return nil
}

func (r *UsersRepo) DeleteUser(ctx context.Context, userID string) error {
	if _, err := r.db.ExecContext(ctx, `
		DELETE FROM users
		WHERE user_id = $1
	`, userID); err != nil {
		return fmt.Errorf("failed to delete user: %w", err)
	}
	return nil
}
