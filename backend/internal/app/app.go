package app

import (
	"fmt"
	"kanban-board/internal/config"
	"kanban-board/internal/middleware"
	"kanban-board/internal/services"
	"kanban-board/internal/storage"
	transport "kanban-board/internal/transport/http"
	"kanban-board/internal/transport/http/handlers"
	"kanban-board/pkg"
	"net/http"
)

func Run(cfg *config.Config) {
	logger := pkg.InitLogger(cfg.LogLevel)

	logger.Debug("Debug is enabled")

	logger.Info("Info is enabled")

	logger.Warn("Warn is enabled")

	logger.Error("Error is enabled")

	storage, err := storage.Connect(cfg.Postgres)
	if err != nil {
		panic(fmt.Errorf("failed connection to storage: %v", err))
	}

	services := services.Init(storage)

	handlers := handlers.Init(services, logger)

	mux := http.NewServeMux()
	transport := transport.Init(handlers)

	transport.Start(mux)

	err = http.ListenAndServe(cfg.HTTP_ADDRESS, middleware.Logger(logger)(mux))
	if err != nil {
		panic(err)
	}

}
