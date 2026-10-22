package users_repository

import (
	"context"
	"database/sql"
	"fmt"
	"kanban-board/internal/models"
	"kanban-board/internal/storage"
)

type UsersRepo struct {
	db  *sql.DB
	ctx context.Context
}

func Init(storage *storage.Storage, ctx context.Context) *UsersRepo {
	return &UsersRepo{
		db:  storage.Db,
		ctx: ctx,
	}
}

func (r *UsersRepo) CreateUser(userName string, email string, group string, imageUrl string, passwordHash string) (string, error) {
	var id string
	err := r.db.QueryRow(`
		INSERT INTO "user" (username, email, "group", image_url, password_hash)
		VALUES ($1, $2, $3, $4, $5)
		RETURNING id
	 `, userName, email, group, imageUrl, passwordHash).Scan(&id)

	if err != nil {
		return "", fmt.Errorf("failed to create user: %v", err)
	}

	return id, nil
}

func (r *UsersRepo) GetUserByID(id string) (*models.User, error) {
	var user models.User
	err := r.db.QueryRowContext(r.ctx, `
		SELECT id, username, email, "group", image_url
		FROM "user"
		WHERE id = $1
	 `, id).Scan(&user.ID, &user.UserName, &user.Email, &user.Group, &user.ImageURL)

	if err != nil {
		return nil, fmt.Errorf("failed to select user by id: %v", err)
	}

	return &user, nil
}

func (r *UsersRepo) GetUserByEmail(email string) (*models.User, error) {
	var user models.User
	err := r.db.QueryRowContext(r.ctx, `
		SELECT id, username, email, "group", image_url
		FROM "user"
		WHERE email = $1
	 `, email).Scan(&user.ID, &user.UserName, &user.Email, &user.Group, &user.ImageURL)

	if err != nil {
		return nil, fmt.Errorf("failed to select user by email: %v", err)
	}

	return &user, nil
}

func (r *UsersRepo) GetUserByUserName(userName string) (*models.User, error) {
	var user models.User
	err := r.db.QueryRowContext(r.ctx, `
		SELECT id, username, email, "group", image_url
		FROM "user"
		WHERE username = $1
	 `, userName).Scan(&user.ID, &user.UserName, &user.Email, &user.Group, &user.ImageURL)

	if err != nil {
		return nil, fmt.Errorf("failed to select user by userName: %v", err)
	}

	return &user, nil
}

func (r *UsersRepo) GetUsersByGroup(group string) ([]models.User, error) {
	var users []models.User
	rows, err := r.db.QueryContext(r.ctx, `
		SELECT id, username, email, "group", image_url
		FROM "user"
		WHERE "group" = $1
	 `, group)

	if err != nil {
		return nil, fmt.Errorf("failed to select users by group: %v", err)
	}

	for rows.Next() {
		var user models.User
		if err := rows.Scan(&user.ID, &user.UserName, &user.Email, &user.Group, &user.ImageURL); err != nil {
			return nil, fmt.Errorf("failed to scan user(user by group): %v", err)
		}
		users = append(users, user)
	}

	return users, nil
}

func (r *UsersRepo) UpdateUser(userName string, group string, imageUrl string, id string) error {
	err := r.db.QueryRowContext(r.ctx, `
		UPDATE "user"
		SET username = $1, "group" = $2, image_url = $3
		WHERE id = $4
	 `, userName, group, imageUrl, id).Err()

	if err != nil {
		return fmt.Errorf("failed to update user: %v", err)
	}
	return nil
}

func (r *UsersRepo) DeleteUser(id string) error {
	err := r.db.QueryRowContext(r.ctx, `
		DELETE FROM "user"
		WHERE id = $1
	 `, id).Err()

	if err != nil {
		return fmt.Errorf("failed to delete user: %v", err)
	}
	return nil
}
