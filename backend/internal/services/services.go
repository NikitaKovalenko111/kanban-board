package services

import "kanban-board/internal/storage"

type Services struct {
}

func Init(storage *storage.Storage) *Services {
	return &Services{}
}
