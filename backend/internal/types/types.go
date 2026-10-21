package types

import "log/slog"

const (
	EnvLocal = "local"
	EnvDev   = "dev"
	EnvWarn  = "warn"
	EnvProd  = "prod"
)

var LogLevels = map[string]slog.Level{
	EnvLocal: slog.LevelDebug,
	EnvDev:   slog.LevelInfo,
	EnvWarn:  slog.LevelWarn,
	EnvProd:  slog.LevelError,
}
