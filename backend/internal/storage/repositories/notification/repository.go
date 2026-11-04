package notification_repo

import (
	"context"
	"database/sql"

	"kanban-board/internal/models"
)

type PendingPush struct {
	NotificationID string
	Token          string
	Kind           string
	Title          string
	Text           string
	TaskID         *string
	EventID        *string
}

type Repository struct {
	db *sql.DB
}

func Init(db *sql.DB) *Repository {
	return &Repository{db: db}
}

func (r *Repository) List(ctx context.Context, userID string, limit int) ([]models.Notification, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT notification_id, kind, title, text, created_at, read_at IS NOT NULL, task_id, event_id
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
		if err := rows.Scan(&item.ID, &item.Kind, &item.Title, &item.Text, &item.At, &item.Read, &item.TaskID, &item.EventID); err != nil {
			return nil, err
		}
		items = append(items, item)
	}
	return items, rows.Err()
}

func (r *Repository) ReadAll(ctx context.Context, userID string) error {
	_, err := r.db.ExecContext(ctx, `
		UPDATE notifications SET read_at = CURRENT_TIMESTAMP
		WHERE user_id = $1 AND read_at IS NULL
	`, userID)
	return err
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
			notification_id, user_id, kind, title, text, lesson_key, reminder_key
		)
		SELECT
			gen_random_uuid(), ur.user_id, 'EVENT', l.title,
			'Занятие начнётся через ' || ur.minutes_before || ' мин.',
			ur.item_key, 'lesson:' || ur.minutes_before
		FROM user_reminders ur
		JOIN lessons l ON ur.item_key = 'lesson:' || COALESCE(l.external_id, l.lesson_id::text)
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
		SELECT n.notification_id, pt.token, n.kind, n.title, n.text, n.task_id, n.event_id
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
		if err := rows.Scan(&item.NotificationID, &item.Token, &item.Kind, &item.Title, &item.Text, &item.TaskID, &item.EventID); err != nil {
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
