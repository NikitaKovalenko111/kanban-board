package services

import (
	"context"

	"kanban-board/internal/config"
	auth_service "kanban-board/internal/services/auth"
	"kanban-board/internal/storage"
	"kanban-board/internal/storage/repositories"
)

type Services struct {
	Auth *auth_service.AuthService
}

func Init(appStorage *storage.Storage, jwtConfig config.JWT, ctx context.Context) *Services {
	repositories := repositories.Init(appStorage, ctx)

	return &Services{
		Auth: auth_service.Init(repositories.SessionRepo, repositories.UsersRepo, jwtConfig),
	}
}
