package app

import (
	"context"
	"fmt"
	"net/http"

	"kanban-board/internal/config"
	"kanban-board/internal/middleware"
	"kanban-board/internal/services"
	"kanban-board/internal/storage"
	transport "kanban-board/internal/transport/http"
	"kanban-board/internal/transport/http/handlers"
	"kanban-board/pkg"
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

	services := services.Init(storage, cfg.JWT)

	cleanupContext, stopSessionCleanup := context.WithCancel(context.Background())
	defer stopSessionCleanup()
	go services.RunSessionCleanup(
		cleanupContext,
		cfg.JWT.SessionCleanupInterval,
		cfg.TIMEOUT,
		logger,
	)

	handlers := handlers.Init(services)

	mux := http.NewServeMux()
	transport := transport.Init(handlers)
	transport.Start(mux)

	requestHandler := middleware.Logger(logger)(middleware.Timeout(cfg.TIMEOUT)(mux))
	server := &http.Server{
		Addr:              cfg.HTTP_ADDRESS,
		Handler:           requestHandler,
		ReadHeaderTimeout: cfg.TIMEOUT,
		ReadTimeout:       cfg.TIMEOUT,
		WriteTimeout:      cfg.TIMEOUT,
		IdleTimeout:       cfg.IDLE_TIMEOUT,
	}

	if err := server.ListenAndServe(); err != nil {
		panic(err)
	}
}
