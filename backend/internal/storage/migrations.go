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
		{version: 4, name: "notification events", run: createNotificationEvents},
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

func createNotificationEvents(storage *Storage) error {
	_, err := storage.Db.Exec(`
		CREATE OR REPLACE FUNCTION publish_notification_event()
		RETURNS TRIGGER AS $$
		DECLARE
			event_type TEXT;
		BEGIN
			event_type := CASE WHEN TG_OP = 'INSERT' THEN 'created' ELSE 'read' END;
			PERFORM pg_notify(
				'notification_events',
				NEW.user_id::text || ':' || event_type
			);
			RETURN NEW;
		END;
		$$ LANGUAGE plpgsql;

		DROP TRIGGER IF EXISTS notifications_created_event ON notifications;
		CREATE TRIGGER notifications_created_event
		AFTER INSERT ON notifications
		FOR EACH ROW
		EXECUTE FUNCTION publish_notification_event();

		DROP TRIGGER IF EXISTS notifications_read_event ON notifications;
		CREATE TRIGGER notifications_read_event
		AFTER UPDATE OF read_at ON notifications
		FOR EACH ROW
		WHEN (OLD.read_at IS DISTINCT FROM NEW.read_at)
		EXECUTE FUNCTION publish_notification_event();
	`)
	return err
}
