package services

import (
	"context"
	"log/slog"
	"time"

	"kanban-board/internal/config"
)

func (s *Services) RunScheduleSync(ctx context.Context, cfg config.ScheduleSync, logger *slog.Logger) {
	if !cfg.Enabled {
		return
	}
	run := func() {
		operationContext, cancel := context.WithTimeout(ctx, cfg.HTTPTimeout*time.Duration(2))
		defer cancel()
		count, err := s.Schedule.Sync(operationContext, cfg)
		if err != nil {
			logger.Error("SCHEDULE SYNC FAILED", slog.Any("error", err))
			return
		}
		logger.Info("SCHEDULE SYNC COMPLETED", slog.Int("lessons", count))
	}
	run()
	ticker := time.NewTicker(cfg.Interval)
	defer ticker.Stop()
	for {
		select {
		case <-ticker.C:
			run()
		case <-ctx.Done():
			return
		}
	}
}
