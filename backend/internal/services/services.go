package services

import (
	"kanban-board/internal/config"
	auth_service "kanban-board/internal/services/auth"
	"kanban-board/internal/storage"
	session_repo "kanban-board/internal/storage/repositories/session"
	users_repo "kanban-board/internal/storage/repositories/user"
)

type Services struct {
	Auth *auth_service.AuthService
}

func Init(appStorage *storage.Storage, jwtConfig config.JWT) *Services {
	userRepo := users_repo.Init(appStorage.Db)
	sessionRepo := session_repo.Init(appStorage.Db)

	return &Services{
		Auth: auth_service.Init(sessionRepo, userRepo, jwtConfig),
	}
}
