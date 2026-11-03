package notification_repo

import (
	"context"
	"database/sql"

	"kanban-board/internal/models"
)

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
			event_id
		)
		SELECT
			gen_random_uuid(),
			u.user_id,
			'EVENT',
			e.title,
			'Событие начнётся в ближайшие 24 часа',
			e.event_id
		FROM events e
		JOIN users u
			ON u.is_active = TRUE
			AND (
				e.visibility = 'TEAM'
				OR (e.visibility = 'PERSONAL' AND u.user_id = e.owner_user_id)
				OR (
					e.visibility = 'GROUP'
					AND u.student_group_id = e.student_group_id
				)
			)
		WHERE e.starts_at > CURRENT_TIMESTAMP
			AND e.starts_at <= CURRENT_TIMESTAMP + INTERVAL '24 hours'
		ON CONFLICT DO NOTHING
	`)
	if err != nil {
		return 0, err
	}

	return result.RowsAffected()
}
