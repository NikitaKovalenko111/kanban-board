package config

import (
	"path/filepath"
	"testing"
	"time"
)

func TestLoadHTTPServerFromYAML(t *testing.T) {
	for _, name := range []string{
		"log_level",
		"http_address",
		"timeout",
		"idle_timeout",
		"session_cleanup_interval",
		"db_host",
		"db_user",
		"db_port",
		"db_pass",
		"db_name",
		"ssl_mode",
	} {
		t.Setenv(name, "")
	}

	cfg, err := Load(filepath.Join("..", "..", "config", "config_test.yaml"))
	if err != nil {
		t.Fatalf("Load() error = %v", err)
	}

	if cfg.HTTP_ADDRESS != ":3005" {
		t.Errorf("HTTP_ADDRESS = %q, want %q", cfg.HTTP_ADDRESS, ":3005")
	}
	if cfg.TIMEOUT != 4*time.Second {
		t.Errorf("TIMEOUT = %s, want %s", cfg.TIMEOUT, 4*time.Second)
	}
	if cfg.IDLE_TIMEOUT != 60*time.Second {
		t.Errorf("IDLE_TIMEOUT = %s, want %s", cfg.IDLE_TIMEOUT, 60*time.Second)
	}
	if cfg.SessionCleanupInterval != 30*time.Minute {
		t.Errorf("SessionCleanupInterval = %s, want %s", cfg.SessionCleanupInterval, 30*time.Minute)
	}
}
