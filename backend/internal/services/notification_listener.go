package services

import (
	"context"
	"log/slog"
	"time"
)

func (s *Services) RunNotificationListener(ctx context.Context, logger *slog.Logger) {
	const retryInterval = 5 * time.Second

	for {
		err := s.Notification.Listen(ctx)
		if ctx.Err() != nil {
			return
		}
		logger.Error("NOTIFICATION LISTENER FAILED", slog.Any("error", err))

		timer := time.NewTimer(retryInterval)
		select {
		case <-timer.C:
		case <-ctx.Done():
			if !timer.Stop() {
				<-timer.C
			}
			return
		}
	}
}
