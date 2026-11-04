package storage

import (
	"database/sql"
	"fmt"
)

type migration struct {
	version int
	name    string
	run     func(*Storage) error
}

func (s *Storage) Migrate() error {
	if _, err := s.Db.Exec(`
		CREATE TABLE IF NOT EXISTS schema_migrations (
			version INTEGER PRIMARY KEY,
			name VARCHAR NOT NULL,
			applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
		)
	`); err != nil {
		return fmt.Errorf("create schema_migrations: %w", err)
	}
	migrations := []migration{
		{version: 1, name: "create tables", run: func(storage *Storage) error { return storage.CreateTables() }},
		{version: 2, name: "mobile api schema", run: func(storage *Storage) error { return storage.ApplySchemaMigration() }},
		{version: 3, name: "create indexes", run: func(storage *Storage) error { return storage.CreateIndexes() }},
	}
	for _, item := range migrations {
		var applied bool
		if err := s.Db.QueryRow(`SELECT EXISTS(SELECT 1 FROM schema_migrations WHERE version = $1)`, item.version).Scan(&applied); err != nil {
			return err
		}
		if applied {
			continue
		}
		if err := item.run(s); err != nil {
			return fmt.Errorf("migration %d %s: %w", item.version, item.name, err)
		}
		if _, err := s.Db.Exec(`INSERT INTO schema_migrations (version, name) VALUES ($1, $2)`, item.version, item.name); err != nil && err != sql.ErrNoRows {
			return err
		}
	}
	return nil
}
