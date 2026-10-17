package config

import (
	"fmt"
	"os"

	"github.com/goccy/go-yaml"
)

type Config struct {
	HTTP     `yaml:"http" env-required:"true"`
	Postgres `yaml:"postgres" env-required:"true"`
}

func Load(path string) (*Config, error) {
	cfg := Config{}

	if err := loadYAML(path, &cfg); err != nil {
		return nil, fmt.Errorf("err load yaml: %v", err)
	}

	if err := loadENV(&cfg); err != nil {
		return nil, fmt.Errorf("err load env: %v", err)

	}

	if err := validate(&cfg); err != nil {
		return nil, fmt.Errorf("err validate: %v", err)

	}

	return &cfg, nil
}

func loadYAML(path string, cfg *Config) error {
	if path == "" {
		return nil
	}

	data, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			return nil
		}

		return err
	}

	return yaml.Unmarshal(data, cfg)
}

func loadENV(cfg *Config) error {
	err := loadHTTP(cfg)
	if err != nil {
		return err
	}

	err = loadPostgres(cfg)
	if err != nil {
		return err
	}

	return nil
}

func validate(cfg *Config) error {
	err := validateHTTP(cfg)
	if err != nil {
		return err
	}

	err = validatePostgres(cfg)
	if err != nil {
		return err
	}

	return nil
}
