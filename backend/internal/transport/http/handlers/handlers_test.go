package handlers

import (
	"net/http"
	"testing"

	"kanban-board/internal/config"
	"kanban-board/internal/services"
	"kanban-board/internal/storage"

	"github.com/DATA-DOG/go-sqlmock"
)

func TestWorkspaceHandlersRegisterAllRoutes(t *testing.T) {
	db, _, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock.New(): %v", err)
	}
	defer db.Close()

	appServices := services.Init(&storage.Storage{Db: db}, config.JWT{})
	handlers := Init(appServices)
	mux := http.NewServeMux()

	handlers.Profile.Start(mux)
	handlers.Project.Start(mux)
	handlers.Board.Start(mux)
	handlers.Task.Start(mux)
	handlers.Comment.Start(mux)
	handlers.Attachment.Start(mux)
	handlers.Schedule.Start(mux)
	handlers.Event.Start(mux)
}
