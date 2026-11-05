package app

import (
	"context"
	"fmt"
	"net/http"
	"time"

	"kanban-board/internal/config"
	"kanban-board/internal/middleware"
	"kanban-board/internal/objectstorage"
	"kanban-board/internal/push"
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

	pushSender, err := push.New(context.Background(), cfg.FCM)
	if err != nil {
		panic(fmt.Errorf("initialize push sender: %v", err))
	}
	services := services.Init(storage, cfg.JWT, pushSender)
	objects, err := objectstorage.New(context.Background(), cfg.MinIO)
	if err != nil {
		panic(fmt.Errorf("initialize object storage: %v", err))
	}

	cleanupContext, stopSessionCleanup := context.WithCancel(context.Background())
	defer stopSessionCleanup()
	go services.RunSessionCleanup(
		cleanupContext,
		cfg.JWT.SessionCleanupInterval,
		cfg.TIMEOUT,
		logger,
	)
	go services.RunNotificationScheduler(
		cleanupContext,
		time.Minute,
		cfg.TIMEOUT,
		logger,
	)
	go services.RunNotificationListener(cleanupContext, logger)
	go services.RunScheduleSync(cleanupContext, cfg.ScheduleSync, logger)

	handlers := handlers.Init(services, objects)

	mux := http.NewServeMux()
	transport := transport.Init(handlers, cfg.JWT.AccessSecret, logger, services.Auth.IsUserActive)
	transport.Start(mux)

	requestHandler := middleware.Logger(logger)(middleware.TimeoutExcept(
		cfg.TIMEOUT,
		"/api/v1/notifications/stream",
	)(mux))
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
