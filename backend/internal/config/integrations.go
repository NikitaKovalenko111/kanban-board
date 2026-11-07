package config

import (
	"fmt"
	"os"
	"strconv"
	"time"
)

type MinIO struct {
	Enabled   bool   `yaml:"enabled"`
	Endpoint  string `yaml:"endpoint"`
	AccessKey string `yaml:"access_key"`
	SecretKey string `yaml:"secret_key"`
	Bucket    string `yaml:"bucket"`
	UseSSL    bool   `yaml:"use_ssl"`
}

type FCM struct {
	Enabled         bool   `yaml:"enabled"`
	CredentialsFile string `yaml:"credentials_file"`
	ProjectID       string `yaml:"project_id"`
}

type ScheduleSync struct {
	Enabled       bool          `yaml:"enabled"`
	SourceURL     string        `yaml:"source_url"`
	SemesterID    int           `yaml:"semester_id"`
	SemesterStart string        `yaml:"semester_start"`
	Interval      time.Duration `yaml:"interval"`
	HTTPTimeout   time.Duration `yaml:"http_timeout"`
}

func loadIntegrations(cfg *Config) error {
	setBool := func(name string, destination *bool) error {
		value := os.Getenv(name)
		if value == "" {
			return nil
		}
		parsed, err := strconv.ParseBool(value)
		if err != nil {
			return fmt.Errorf("invalid %s: %w", name, err)
		}
		*destination = parsed
		return nil
	}
	if err := setBool("minio_enabled", &cfg.MinIO.Enabled); err != nil {
		return err
	}
	if err := setBool("minio_use_ssl", &cfg.MinIO.UseSSL); err != nil {
		return err
	}
	if err := setBool("fcm_enabled", &cfg.FCM.Enabled); err != nil {
		return err
	}
	if err := setBool("schedule_sync_enabled", &cfg.ScheduleSync.Enabled); err != nil {
		return err
	}
	stringValues := map[string]*string{
		"minio_endpoint":               &cfg.MinIO.Endpoint,
		"minio_access_key":             &cfg.MinIO.AccessKey,
		"minio_secret_key":             &cfg.MinIO.SecretKey,
		"minio_bucket":                 &cfg.MinIO.Bucket,
		"fcm_credentials_file":         &cfg.FCM.CredentialsFile,
		"fcm_project_id":               &cfg.FCM.ProjectID,
		"schedule_sync_source_url":     &cfg.ScheduleSync.SourceURL,
		"schedule_sync_semester_start": &cfg.ScheduleSync.SemesterStart,
	}
	for name, destination := range stringValues {
		if value := os.Getenv(name); value != "" {
			*destination = value
		}
	}
	if value := os.Getenv("schedule_sync_semester_id"); value != "" {
		semesterID, err := strconv.Atoi(value)
		if err != nil {
			return fmt.Errorf("invalid schedule_sync_semester_id: %w", err)
		}
		cfg.ScheduleSync.SemesterID = semesterID
	}
	for name, destination := range map[string]*time.Duration{
		"schedule_sync_interval":     &cfg.ScheduleSync.Interval,
		"schedule_sync_http_timeout": &cfg.ScheduleSync.HTTPTimeout,
	} {
		if value := os.Getenv(name); value != "" {
			parsed, err := time.ParseDuration(value)
			if err != nil {
				return fmt.Errorf("invalid %s: %w", name, err)
			}
			*destination = parsed
		}
	}
	return nil
}

func validateIntegrations(cfg *Config) error {
	if cfg.MinIO.Enabled && (cfg.MinIO.Endpoint == "" || cfg.MinIO.AccessKey == "" || cfg.MinIO.SecretKey == "" || cfg.MinIO.Bucket == "") {
		return fmt.Errorf("minio endpoint, access_key, secret_key, and bucket are required when enabled")
	}
	if cfg.FCM.Enabled && cfg.FCM.CredentialsFile == "" {
		return fmt.Errorf("fcm credentials_file is required when enabled")
	}
	if cfg.ScheduleSync.Enabled && (cfg.ScheduleSync.SourceURL == "" ||
		cfg.ScheduleSync.SemesterID <= 0 ||
		cfg.ScheduleSync.SemesterStart == "" ||
		cfg.ScheduleSync.Interval <= 0 ||
		cfg.ScheduleSync.HTTPTimeout <= 0) {
		return fmt.Errorf("schedule source_url, semester_id, semester_start, and positive intervals are required when enabled")
	}
	if cfg.ScheduleSync.SemesterStart != "" {
		if _, err := time.Parse("2006-01-02", cfg.ScheduleSync.SemesterStart); err != nil {
			return fmt.Errorf("schedule semester_start must use YYYY-MM-DD: %w", err)
		}
	}
	return nil
}
