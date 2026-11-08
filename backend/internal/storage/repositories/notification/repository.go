package notification_repo

import (
	"context"
	"database/sql"
	"fmt"
	"strings"

	"kanban-board/internal/models"
	repositorycommon "kanban-board/internal/storage/repositories/common"

	"github.com/jackc/pgx/v5/stdlib"
)

type PendingPush struct {
	NotificationID string
	Token          string
	Kind           string
	Title          string
	Text           string
	TaskID         *string
	EventID        *string
	LessonID       *string
}

type Repository struct {
	db *sql.DB
}

func Init(db *sql.DB) *Repository {
	return &Repository{db: db}
}

func (r *Repository) List(ctx context.Context, userID string, limit int) ([]models.Notification, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT notification_id, kind, title, text, created_at, read_at IS NOT NULL, task_id, event_id, lesson_id
		FROM notifications
		WHERE user_id = $1
		ORDER BY created_at DESC
		LIMIT $2
	`, userID, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := make([]models.Notification, 0)
	for rows.Next() {
		var item models.Notification
		if err := rows.Scan(
			&item.ID,
			&item.Kind,
			&item.Title,
			&item.Text,
			&item.At,
			&item.Read,
			&item.TaskID,
			&item.EventID,
			&item.LessonID,
		); err != nil {
			return nil, err
		}
		items = append(items, item)
	}
	return items, rows.Err()
}

func (r *Repository) ListAfter(ctx context.Context, userID, notificationID string, limit int) ([]models.Notification, error) {
	rows, err := r.db.QueryContext(ctx, `
		WITH cursor AS (
			SELECT
				created_at,
				notification_id
			FROM notifications
			WHERE user_id = $1
				AND notification_id = $2
		)
		SELECT
			n.notification_id,
			n.kind,
			n.title,
			n.text,
			n.created_at,
			n.read_at IS NOT NULL,
			n.task_id,
			n.event_id,
			n.lesson_id
		FROM notifications n
		CROSS JOIN cursor c
		WHERE n.user_id = $1
			AND (n.created_at, n.notification_id) > (c.created_at, c.notification_id)
		ORDER BY n.created_at, n.notification_id
		LIMIT $3
	`, userID, notificationID, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	items := make([]models.Notification, 0)
	for rows.Next() {
		var item models.Notification
		if err := rows.Scan(
			&item.ID,
			&item.Kind,
			&item.Title,
			&item.Text,
			&item.At,
			&item.Read,
			&item.TaskID,
			&item.EventID,
			&item.LessonID,
		); err != nil {
			return nil, err
		}
		items = append(items, item)
	}

	return items, rows.Err()
}

func (r *Repository) Listen(ctx context.Context, notify func(userID, eventType string)) error {
	connection, err := r.db.Conn(ctx)
	if err != nil {
		return err
	}
	defer connection.Close()

	return connection.Raw(func(driverConnection any) error {
		stdlibConnection, ok := driverConnection.(*stdlib.Conn)
		if !ok {
			return fmt.Errorf("notification listener requires pgx stdlib connection")
		}
		pgxConnection := stdlibConnection.Conn()
		if _, err := pgxConnection.Exec(ctx, `LISTEN notification_events`); err != nil {
			return err
		}

		for {
			notification, err := pgxConnection.WaitForNotification(ctx)
			if err != nil {
				return err
			}
			separator := strings.LastIndexByte(notification.Payload, ':')
			if separator <= 0 || separator == len(notification.Payload)-1 {
				continue
			}
			notify(notification.Payload[:separator], notification.Payload[separator+1:])
		}
	})
}

func (r *Repository) ReadAll(ctx context.Context, userID string) error {
	_, err := r.db.ExecContext(ctx, `
		UPDATE notifications SET read_at = CURRENT_TIMESTAMP
		WHERE user_id = $1 AND read_at IS NULL
	`, userID)
	return err
}

func (r *Repository) Read(ctx context.Context, userID, notificationID string) error {
	result, err := r.db.ExecContext(ctx, `
		UPDATE notifications
		SET read_at = COALESCE(read_at, CURRENT_TIMESTAMP)
		WHERE notification_id = $1 AND user_id = $2
	`, notificationID, userID)
	if err != nil {
		return err
	}
	rows, err := result.RowsAffected()
	if err != nil {
		return err
	}
	if rows == 0 {
		return repositorycommon.ErrNotFound
	}
	return nil
}

func (r *Repository) AddPushToken(ctx context.Context, userID, token, platform string) error {
	_, err := r.db.ExecContext(ctx, `
		INSERT INTO push_tokens (user_id, token, platform)
		VALUES ($1, $2, $3)
		ON CONFLICT (user_id, token) DO UPDATE SET platform = EXCLUDED.platform
	`, userID, token, platform)
	return err
}

func (r *Repository) DeletePushToken(ctx context.Context, userID, token string) error {
	_, err := r.db.ExecContext(ctx, `DELETE FROM push_tokens WHERE user_id = $1 AND token = $2`, userID, token)
	return err
}

func (r *Repository) CreateUpcomingEventNotifications(ctx context.Context) (int64, error) {
	result, err := r.db.ExecContext(ctx, `
		INSERT INTO notifications (
			notification_id,
			user_id,
			kind,
			title,
			text,
			event_id,
			reminder_key
		)
		SELECT
			gen_random_uuid(),
			er.user_id,
			'EVENT',
			e.title,
			'Событие начнётся через ' || er.minutes_before || ' мин.',
			e.event_id,
			'event:' || er.minutes_before
		FROM events e
		JOIN event_reminders er ON er.event_id = e.event_id
		JOIN users u ON u.user_id = er.user_id AND u.is_active = TRUE
		WHERE e.starts_at > CURRENT_TIMESTAMP
			AND e.starts_at - make_interval(mins => er.minutes_before) <= CURRENT_TIMESTAMP
			AND NOT EXISTS(
				SELECT 1 FROM reminder_overrides overrides
				WHERE overrides.user_id = er.user_id
					AND overrides.item_key = 'event:' || e.event_id::text
			)
		ON CONFLICT DO NOTHING
	`)
	if err != nil {
		return 0, err
	}

	eventCount, err := result.RowsAffected()
	if err != nil {
		return 0, err
	}
	result, err = r.db.ExecContext(ctx, `
		INSERT INTO notifications (
			notification_id, user_id, kind, title, text, event_id, reminder_key
		)
		SELECT
			gen_random_uuid(), ur.user_id, 'EVENT', e.title,
			'Событие начнётся через ' || ur.minutes_before || ' мин.',
			e.event_id, 'event:' || ur.minutes_before
		FROM user_reminders ur
		JOIN reminder_overrides overrides
			ON overrides.user_id = ur.user_id
			AND overrides.item_key = ur.item_key
			AND overrides.enabled = TRUE
		JOIN events e ON ur.item_key = 'event:' || e.event_id::text
		JOIN users u ON u.user_id = ur.user_id AND u.is_active = TRUE
		WHERE ur.item_key LIKE 'event:%'
			AND e.starts_at > CURRENT_TIMESTAMP
			AND e.starts_at - make_interval(mins => ur.minutes_before) <= CURRENT_TIMESTAMP
		ON CONFLICT DO NOTHING
	`)
	if err != nil {
		return 0, err
	}
	customEventCount, err := result.RowsAffected()
	if err != nil {
		return 0, err
	}
	result, err = r.db.ExecContext(ctx, `
		INSERT INTO notifications (
			notification_id, user_id, kind, title, text, lesson_id, reminder_key
		)
		SELECT
			gen_random_uuid(), ur.user_id, 'LESSON', l.title,
			'Занятие начнётся через ' || ur.minutes_before || ' мин.',
			l.lesson_id, 'lesson:' || ur.minutes_before
		FROM user_reminders ur
		JOIN reminder_overrides overrides
			ON overrides.user_id = ur.user_id
			AND overrides.item_key = ur.item_key
			AND overrides.enabled = TRUE
		JOIN lessons l ON ur.item_key IN (
			'lesson:' || l.lesson_id::text,
			'lesson:' || COALESCE(l.external_id, l.lesson_id::text)
		)
		JOIN users u ON u.user_id = ur.user_id AND u.is_active = TRUE
		WHERE ur.item_key LIKE 'lesson:%'
			AND l.starts_at > CURRENT_TIMESTAMP
			AND l.starts_at - make_interval(mins => ur.minutes_before) <= CURRENT_TIMESTAMP
		ON CONFLICT DO NOTHING
	`)
	if err != nil {
		return 0, err
	}
	lessonCount, err := result.RowsAffected()
	return eventCount + customEventCount + lessonCount, err
}

func (r *Repository) ListPendingPush(ctx context.Context, limit int) ([]PendingPush, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT n.notification_id, pt.token, n.kind, n.title, n.text, n.task_id, n.event_id, n.lesson_id
		FROM notifications n
		JOIN push_tokens pt ON pt.user_id = n.user_id
		WHERE n.push_sent_at IS NULL
		ORDER BY n.created_at
		LIMIT $1
	`, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := make([]PendingPush, 0)
	for rows.Next() {
		var item PendingPush
		if err := rows.Scan(
			&item.NotificationID,
			&item.Token,
			&item.Kind,
			&item.Title,
			&item.Text,
			&item.TaskID,
			&item.EventID,
			&item.LessonID,
		); err != nil {
			return nil, err
		}
		items = append(items, item)
	}
	return items, rows.Err()
}

func (r *Repository) MarkPushSent(ctx context.Context, notificationID string) error {
	_, err := r.db.ExecContext(ctx, `UPDATE notifications SET push_sent_at = CURRENT_TIMESTAMP WHERE notification_id = $1`, notificationID)
	return err
}
