package config

import (
	"fmt"
	"os"
	"time"
)

type HTTP struct {
	HTTP_ADDRESS string        `yaml:"http_address" env-required:"true"`
	TIMEOUT      time.Duration `yaml:"timeout" env-required:"true"`
	IDLE_TIMEOUT time.Duration `yaml:"idle_timeout" env-required:"true"`
}

func loadHTTP(cfg *Config) error {
	if v := os.Getenv("http_address"); v != "" {
		cfg.HTTP.HTTP_ADDRESS = v
	}

	if v := os.Getenv("timeout"); v != "" {
		timeout, err := time.ParseDuration(v)
		if err != nil {
			return fmt.Errorf("invalid TIMEOUT: %w", err)
		}
		cfg.HTTP.TIMEOUT = timeout
	}

	if v := os.Getenv("idle_timeout"); v != "" {
		timeout, err := time.ParseDuration(v)
		if err != nil {
			return fmt.Errorf("invalid TIMEOUT: %w", err)
		}
		cfg.HTTP.IDLE_TIMEOUT = timeout
	}

	return nil
}

func validateHTTP(cfg *Config) error {
	if cfg.HTTP.HTTP_ADDRESS == "" {
		return fmt.Errorf("HTTP_ADDRESS is required")
	}

	if cfg.HTTP.TIMEOUT == 0 {
		return fmt.Errorf("TIMEOUT is required")
	}

	if cfg.HTTP.IDLE_TIMEOUT == 0 {
		return fmt.Errorf("IDLE_TIMEOUT is required")
	}

	return nil
}
