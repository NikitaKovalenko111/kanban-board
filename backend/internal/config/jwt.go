package config

import (
	"fmt"
	"os"
)

type JWT struct {
	AccessSecret    string `yaml:"jwt_access_secret" env-required:"true"`
	RefreshSecret   string `yaml:"jwt_refresh_secret" env-required:"true"`
	AccessTokenTTL  string `yaml:"access_token_ttl" env-required:"true"`
	RefreshTokenTTL string `yaml:"refresh_token_ttl" env-required:"true"`
}

func loadJWT(cfg *Config) error {
	if v := os.Getenv("jwt_access_secret"); v != "" {
		cfg.JWT.AccessSecret = v
	}

	if v := os.Getenv("jwt_refresh_secret"); v != "" {
		cfg.JWT.RefreshSecret = v
	}

	if v := os.Getenv("access_token_ttl"); v != "" {
		cfg.JWT.AccessTokenTTL = v
	}

	if v := os.Getenv("refresh_token_ttl"); v != "" {
		cfg.JWT.RefreshTokenTTL = v
	}

	return nil
}

func validateJWT(cfg *Config) error {
	if cfg.JWT.AccessSecret == "" {
		return fmt.Errorf("jwt_access_secret is required")
	}

	if cfg.JWT.RefreshSecret == "" {
		return fmt.Errorf("jwt_refresh_secret is required")
	}

	if cfg.JWT.AccessTokenTTL == "" {
		return fmt.Errorf("access_token_ttl is required")
	}

	if cfg.JWT.RefreshTokenTTL == "" {
		return fmt.Errorf("refresh_token_ttl is required")
	}

	return nil
}
