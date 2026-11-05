package services

import (
	"context"
	"log/slog"
	"time"
)

func (s *Services) RunNotificationScheduler(
	ctx context.Context,
	interval time.Duration,
	operationTimeout time.Duration,
	logger *slog.Logger,
) {
	run := func() {
		operationContext, cancel := context.WithTimeout(ctx, operationTimeout)
		defer cancel()

		created, err := s.Notification.CreateUpcomingEventNotifications(operationContext)
		if err != nil {
			logger.Error("NOTIFICATION SCHEDULER FAILED", slog.Any("error", err))
			return
		}
		if created > 0 {
			logger.Info("EVENT NOTIFICATIONS CREATED", slog.Int64("notifications", created))
		}
		sent, err := s.Notification.DispatchPush(operationContext)
		if err != nil {
			logger.Error("PUSH DISPATCH FAILED", slog.Any("error", err))
			return
		}
		if sent > 0 {
			logger.Info("PUSH NOTIFICATIONS SENT", slog.Int("notifications", sent))
		}
	}

	run()
	ticker := time.NewTicker(interval)
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
