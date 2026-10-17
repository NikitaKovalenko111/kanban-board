package config

import (
	"fmt"
	"os"
	"strconv"
)

type Postgres struct {
	DbHost string `yaml:"db_host" env-required:"true"`
	DbUser string `yaml:"db_user" env-required:"true"`
	DbPort int    `yaml:"db_port" env-required:"true"`
	DbPass string `yaml:"db_pass" env-required:"true"`
	DbName string `yaml:"db_name" env-required:"true"`
}

func loadPostgres(cfg *Config) error {
	if v := os.Getenv("db_host"); v != "" {
		cfg.Postgres.DbHost = v
	}

	if v := os.Getenv("db_user"); v != "" {
		cfg.Postgres.DbUser = v
	}

	if v := os.Getenv("db_port"); v != "" {
		port, err := strconv.Atoi(v)
		if err != nil {
			return fmt.Errorf("invalid port: %v", err)
		}
		cfg.Postgres.DbPort = port
	}

	if v := os.Getenv("db_pass"); v != "" {
		cfg.Postgres.DbPass = v
	}

	if v := os.Getenv("db_name"); v != "" {
		cfg.Postgres.DbName = v
	}

	return nil
}

func validatePostgres(cfg *Config) error {
	if cfg.Postgres.DbHost == "" {
		return fmt.Errorf("DbHost is required")
	}

	if cfg.Postgres.DbUser == "" {
		return fmt.Errorf("DbUser is required")
	}

	if cfg.Postgres.DbPort == 0 {
		return fmt.Errorf("DbPort is required")
	}

	if cfg.Postgres.DbPass == "" {
		return fmt.Errorf("DbPass is required")
	}

	if cfg.Postgres.DbName == "" {
		return fmt.Errorf("DbName is required")
	}

	return nil
}
