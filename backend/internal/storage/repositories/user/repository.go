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
	return &UsersRepo{db: db}
}

// used in auth_service.go

func (r *UsersRepo) CreateUser(ctx context.Context, username string, email string, groupName string, imageURL string, passwordHash string) (string, error) {
	var userID string
	err := r.db.QueryRowContext(ctx, `
		INSERT INTO users (user_id, username, email, group_name, image_url, password_hash)
		VALUES (gen_random_uuid(), $1, $2, $3, $4, $5)
		RETURNING user_id
	 `, username, email, groupName, imageURL, passwordHash).Scan(&userID)

	if err != nil {
		return "", fmt.Errorf("failed to create user: %w", err)
	}

	return userID, nil
}

func (r *UsersRepo) GetUserByID(ctx context.Context, userID string) (*models.User, error) {
	var user models.User
	err := r.db.QueryRowContext(ctx, `
		SELECT user_id, username, email, group_name, COALESCE(image_url, ''), created_at
		FROM users
		WHERE user_id = $1
	 `, userID).Scan(&user.UserID, &user.Username, &user.Email, &user.GroupName, &user.ImageURL, &user.CreatedAt)

	if err != nil {
		return nil, fmt.Errorf("failed to select user by id: %v", err)
	}

	return &user, nil
}

func (r *UsersRepo) GetUserByEmail(ctx context.Context, email string) (*models.User, error) {
	var user models.User
	err := r.db.QueryRowContext(ctx, `
		SELECT user_id, username, email, group_name, COALESCE(image_url, ''), created_at
		FROM users
		WHERE email = $1
	 `, email).Scan(&user.UserID, &user.Username, &user.Email, &user.GroupName, &user.ImageURL, &user.CreatedAt)

	if err != nil {
		return nil, fmt.Errorf("failed to select user by email: %v", err)
	}

	return &user, nil
}

func (r *UsersRepo) GetUserByUsername(ctx context.Context, username string) (*models.User, error) {
	var user models.User
	err := r.db.QueryRowContext(ctx, `
		SELECT user_id, username, email, group_name, COALESCE(image_url, ''), created_at
		FROM users
		WHERE username = $1
	 `, username).Scan(&user.UserID, &user.Username, &user.Email, &user.GroupName, &user.ImageURL, &user.CreatedAt)

	if err != nil {
		return nil, fmt.Errorf("failed to select user by username: %v", err)
	}

	return &user, nil
}

// used in auth_service.go

func (r *UsersRepo) GetUserCredentials(ctx context.Context, login string) (*models.UserCredentials, error) {
	var credentials models.UserCredentials
	err := r.db.QueryRowContext(ctx, `
		SELECT user_id, username, email, password_hash
		FROM users
		WHERE email = $1 OR username = $1
		LIMIT 1
	`, login).Scan(&credentials.UserID, &credentials.Username, &credentials.Email, &credentials.PasswordHash)

	if err != nil {
		return nil, fmt.Errorf("failed to select user credentials: %w", err)
	}

	return &credentials, nil
}

func (r *UsersRepo) GetUsersByGroupName(ctx context.Context, groupName string) ([]models.User, error) {
	var users []models.User
	rows, err := r.db.QueryContext(ctx, `
		SELECT user_id, username, email, group_name, COALESCE(image_url, ''), created_at
		FROM users
		WHERE group_name = $1
	 `, groupName)

	if err != nil {
		return nil, fmt.Errorf("failed to select users by group name: %v", err)
	}
	defer rows.Close()

	for rows.Next() {
		var user models.User
		if err := rows.Scan(&user.UserID, &user.Username, &user.Email, &user.GroupName, &user.ImageURL, &user.CreatedAt); err != nil {
			return nil, fmt.Errorf("failed to scan user by group name: %v", err)
		}
		users = append(users, user)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("failed to iterate users by group name: %v", err)
	}

	return users, nil
}

func (r *UsersRepo) UpdateUser(ctx context.Context, userID string, username string, groupName string, imageURL string) error {
	_, err := r.db.ExecContext(ctx, `
		UPDATE users
		SET username = $1, group_name = $2, image_url = $3
		WHERE user_id = $4
	 `, username, groupName, imageURL, userID)

	if err != nil {
		return fmt.Errorf("failed to update user: %v", err)
	}
	return nil
}

func (r *UsersRepo) DeleteUser(ctx context.Context, userID string) error {
	_, err := r.db.ExecContext(ctx, `
		DELETE FROM users
		WHERE user_id = $1
	 `, userID)

	if err != nil {
		return fmt.Errorf("failed to delete user: %v", err)
	}
	return nil
}
