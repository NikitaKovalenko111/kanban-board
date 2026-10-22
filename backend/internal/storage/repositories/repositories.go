package repositories

import (
	"context"
	"kanban-board/internal/storage"
	users_repository "kanban-board/internal/storage/repositories/users"
)

type Repositories struct {
	UsersRepo *users_repository.UsersRepo
}

func Init(storage *storage.Storage, ctx context.Context) *Repositories {
	return &Repositories{
		UsersRepo: users_repository.Init(storage, ctx),
	}
}
