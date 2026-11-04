package services

import (
	"context"
	"log/slog"
	"time"
)

func (s *Services) RunSessionCleanup(
	ctx context.Context,
	interval time.Duration,
	operationTimeout time.Duration,
	logger *slog.Logger,
) {
	runCleanup := func() {
		cleanupCtx, cancel := context.WithTimeout(ctx, operationTimeout)
		defer cancel()

		deletedSessions, err := s.Auth.CleanupInactiveSessions(cleanupCtx)
		if err != nil {
			logger.Error("SESSION CLEANUP FAILED", slog.Any("error", err))
			return
		}

		logger.Info(
			"SESSION CLEANUP COMPLETED",
			slog.Int64("deleted_sessions", deletedSessions),
		)
	}

	if ctx.Err() != nil {
		return
	}

	runCleanup()
	ticker := time.NewTicker(interval)
	defer ticker.Stop()

	for {
		select {
		case <-ticker.C:
			runCleanup()
		case <-ctx.Done():
			return
		}
	}
}
