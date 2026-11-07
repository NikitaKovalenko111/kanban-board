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
		{version: 5, name: "notification targets", run: migrateNotificationTargets},
		{version: 6, name: "fixed board columns", run: migrateFixedBoardColumns},
		{version: 7, name: "frontend integration contracts", run: migrateFrontendIntegrationContracts},
		{version: 8, name: "stable lesson reminder keys", run: migrateStableLessonReminderKeys},
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

func migrateStableLessonReminderKeys(storage *Storage) error {
	_, err := storage.Db.Exec(`
		INSERT INTO user_reminders (user_id, item_key, minutes_before)
		SELECT
			reminders.user_id,
			'lesson:' || lessons.lesson_id::text,
			reminders.minutes_before
		FROM user_reminders reminders
		JOIN lessons
			ON lessons.external_id IS NOT NULL
			AND reminders.item_key = 'lesson:' || lessons.external_id
		ON CONFLICT DO NOTHING;

		DELETE FROM user_reminders reminders
		USING lessons
		WHERE lessons.external_id IS NOT NULL
			AND reminders.item_key = 'lesson:' || lessons.external_id;

		INSERT INTO reminder_overrides (user_id, item_key, enabled)
		SELECT
			overrides.user_id,
			'lesson:' || lessons.lesson_id::text,
			overrides.enabled
		FROM reminder_overrides overrides
		JOIN lessons
			ON lessons.external_id IS NOT NULL
			AND overrides.item_key = 'lesson:' || lessons.external_id
		ON CONFLICT (user_id, item_key) DO NOTHING;

		DELETE FROM reminder_overrides overrides
		USING lessons
		WHERE lessons.external_id IS NOT NULL
			AND overrides.item_key = 'lesson:' || lessons.external_id;

		INSERT INTO reminder_overrides (user_id, item_key, enabled)
		SELECT DISTINCT user_id, item_key, TRUE
		FROM user_reminders
		ON CONFLICT (user_id, item_key) DO NOTHING;
	`)
	return err
}

func migrateFrontendIntegrationContracts(storage *Storage) error {
	_, err := storage.Db.Exec(`
		ALTER TABLE projects
			ADD COLUMN IF NOT EXISTS color VARCHAR NOT NULL DEFAULT '#6576DF';

		CREATE TABLE IF NOT EXISTS reminder_overrides (
			user_id UUID NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
			item_key VARCHAR NOT NULL,
			enabled BOOLEAN NOT NULL,
			updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
			PRIMARY KEY (user_id, item_key)
		);

		INSERT INTO reminder_overrides (user_id, item_key, enabled)
		SELECT DISTINCT user_id, item_key, TRUE
		FROM user_reminders
		ON CONFLICT (user_id, item_key) DO NOTHING;

		CREATE UNIQUE INDEX IF NOT EXISTS lessons_group_external_uidx
			ON lessons (student_group_id, external_id)
			WHERE external_id IS NOT NULL;
	`)
	return err
}

func migrateNotificationTargets(storage *Storage) error {
	_, err := storage.Db.Exec(`
		ALTER TABLE notifications
			ADD COLUMN IF NOT EXISTS lesson_id UUID REFERENCES lessons (lesson_id) ON DELETE CASCADE;
		ALTER TABLE notifications
			ADD COLUMN IF NOT EXISTS lesson_key VARCHAR;
		ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_kind_check;

		UPDATE notifications n
		SET lesson_id = l.lesson_id,
			kind = 'LESSON'
		FROM lessons l
		WHERE n.lesson_id IS NULL
			AND n.lesson_key = 'lesson:' || COALESCE(l.external_id, l.lesson_id::text);

		DELETE FROM notifications
		WHERE num_nonnulls(task_id, event_id, lesson_id) <> 1;

		ALTER TABLE notifications
			ADD CONSTRAINT notifications_kind_check
			CHECK (kind IN ('COMMENT', 'RETURNED', 'STATUS', 'EVENT', 'LESSON'));

		ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_target_check;
		ALTER TABLE notifications
			ADD CONSTRAINT notifications_target_check
			CHECK (num_nonnulls(task_id, event_id, lesson_id) = 1);

		DROP INDEX IF EXISTS notifications_user_lesson_uidx;
		CREATE UNIQUE INDEX notifications_user_lesson_uidx
			ON notifications (user_id, lesson_id, reminder_key)
			WHERE lesson_id IS NOT NULL AND kind = 'LESSON' AND reminder_key IS NOT NULL;

		ALTER TABLE notifications DROP COLUMN IF EXISTS lesson_key;
	`)
	return err
}

func migrateFixedBoardColumns(storage *Storage) error {
	_, err := storage.Db.Exec(`
		UPDATE board_columns
		SET
			code = 'MIGRATION_' || REPLACE(column_id::text, '-', ''),
			position = position + 10000;

		INSERT INTO board_columns (column_id, board_id, code, name, kind, color, position)
		SELECT
			gen_random_uuid(),
			b.board_id,
			definition.kind,
			definition.name,
			definition.kind,
			definition.color,
			definition.position
		FROM boards b
		CROSS JOIN (
			VALUES
				('BACKLOG', 'Ожидает', '#8A94A6', 0),
				('IN_PROGRESS', 'В работе', '#6576DF', 1),
				('REVIEW', 'На проверке', '#DA8A37', 2),
				('DONE', 'Выполнено', '#58A779', 3)
		) AS definition(kind, name, color, position)
		WHERE NOT EXISTS (
			SELECT 1
			FROM board_columns bc
			WHERE bc.board_id = b.board_id
				AND bc.kind = definition.kind
		);

		WITH ranked AS (
			SELECT
				column_id,
				board_id,
				CASE
					WHEN kind IN ('BACKLOG', 'REVIEW', 'DONE') THEN kind
					ELSE 'IN_PROGRESS'
				END AS normalized_kind,
				ROW_NUMBER() OVER (
					PARTITION BY board_id, CASE
						WHEN kind IN ('BACKLOG', 'REVIEW', 'DONE') THEN kind
						ELSE 'IN_PROGRESS'
					END
					ORDER BY position, column_id
				) AS row_number
			FROM board_columns
		), moves AS (
			SELECT extra.column_id AS source_id, target.column_id AS target_id
			FROM ranked extra
			JOIN ranked target
				ON target.board_id = extra.board_id
				AND target.normalized_kind = extra.normalized_kind
				AND target.row_number = 1
			WHERE extra.row_number > 1
		)
		UPDATE tasks t
		SET column_id = moves.target_id
		FROM moves
		WHERE t.column_id = moves.source_id;

		WITH ranked AS (
			SELECT
				column_id,
				ROW_NUMBER() OVER (
					PARTITION BY board_id, CASE
						WHEN kind IN ('BACKLOG', 'REVIEW', 'DONE') THEN kind
						ELSE 'IN_PROGRESS'
					END
					ORDER BY position, column_id
				) AS row_number
			FROM board_columns
		)
		DELETE FROM board_columns bc
		USING ranked
		WHERE bc.column_id = ranked.column_id
			AND ranked.row_number > 1;

		UPDATE board_columns
		SET
			code = kind,
			name = CASE kind
				WHEN 'BACKLOG' THEN 'Ожидает'
				WHEN 'IN_PROGRESS' THEN 'В работе'
				WHEN 'REVIEW' THEN 'На проверке'
				WHEN 'DONE' THEN 'Выполнено'
			END,
			color = CASE kind
				WHEN 'BACKLOG' THEN '#8A94A6'
				WHEN 'IN_PROGRESS' THEN '#6576DF'
				WHEN 'REVIEW' THEN '#DA8A37'
				WHEN 'DONE' THEN '#58A779'
			END,
			position = CASE kind
				WHEN 'BACKLOG' THEN 0
				WHEN 'IN_PROGRESS' THEN 1
				WHEN 'REVIEW' THEN 2
				WHEN 'DONE' THEN 3
			END;

		ALTER TABLE board_columns DROP CONSTRAINT IF EXISTS board_columns_kind_check;
		ALTER TABLE board_columns
			ADD CONSTRAINT board_columns_kind_check
			CHECK (kind IN ('BACKLOG', 'IN_PROGRESS', 'REVIEW', 'DONE'));
	`)
	return err
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
