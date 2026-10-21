package pkg

import (
	"kanban-board/internal/types"
	"log/slog"
	"os"

	"github.com/lmittmann/tint"
)

func InitLogger(env string) *slog.Logger {
	logger := slog.New(
		tint.NewTextHandler(os.Stdout, &tint.Options{
			Level:     types.LogLevels[env],
			AddSource: true,
		}),
	)
	return logger
}
