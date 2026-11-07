package event_repo

import (
	"context"
	"database/sql"
	"errors"
	"strings"
	"time"

	"kanban-board/internal/models"
	repositorycommon "kanban-board/internal/storage/repositories/common"
)

type Repository struct {
	db *sql.DB
}

func Init(db *sql.DB) *Repository {
	return &Repository{db: db}
}

func (r *Repository) ListEventTypes(ctx context.Context, userID string) ([]models.EventType, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT event_type_id, title, color, created_by_user_id
		FROM event_types
		WHERE created_by_user_id IS NULL OR created_by_user_id = $1
		ORDER BY title
	`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	types := make([]models.EventType, 0)
	for rows.Next() {
		var eventType models.EventType
		if err := rows.Scan(&eventType.ID, &eventType.Title, &eventType.Color, &eventType.OwnerID); err != nil {
			return nil, err
		}
		eventType.DefaultReminders, err = r.typeReminders(ctx, eventType.ID)
		if err != nil {
			return nil, err
		}
		types = append(types, eventType)
	}
	return types, rows.Err()
}

func (r *Repository) CreateEventType(ctx context.Context, userID, title, color string, reminders []int) (string, error) {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return "", err
	}
	defer tx.Rollback()
	var eventTypeID string
	if err := tx.QueryRowContext(ctx, `
		INSERT INTO event_types (event_type_id, title, color, scope, created_by_user_id)
		VALUES (gen_random_uuid(), $1, $2, 'PERSONAL', $3)
		RETURNING event_type_id
	`, title, color, userID).Scan(&eventTypeID); err != nil {
		return "", err
	}
	if err := replaceTypeReminders(ctx, tx, eventTypeID, reminders); err != nil {
		return "", err
	}
	return eventTypeID, tx.Commit()
}

func (r *Repository) UpdateEventType(ctx context.Context, userID, eventTypeID string, title, color *string, reminders *[]int) error {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	result, err := tx.ExecContext(ctx, `
		UPDATE event_types et
		SET title = COALESCE($1, title), color = COALESCE($2, color), updated_at = CURRENT_TIMESTAMP
		WHERE event_type_id = $3
			AND created_by_user_id IS NOT NULL
			AND (
				created_by_user_id = $4
				OR EXISTS(SELECT 1 FROM users WHERE user_id = $4 AND system_role = 'CAPTAIN' AND is_active = TRUE)
			)
	`, title, color, eventTypeID, userID)
	if err != nil {
		return err
	}
	if err := repositorycommon.RequireAffected(result); err != nil {
		return err
	}
	if reminders != nil {
		if err := replaceTypeReminders(ctx, tx, eventTypeID, *reminders); err != nil {
			return err
		}
	}
	return tx.Commit()
}

func (r *Repository) DeleteEventType(ctx context.Context, userID, eventTypeID string) error {
	result, err := r.db.ExecContext(ctx, `
		DELETE FROM event_types et
		WHERE event_type_id = $1
			AND created_by_user_id IS NOT NULL
			AND (
				created_by_user_id = $2
				OR EXISTS(SELECT 1 FROM users WHERE user_id = $2 AND system_role = 'CAPTAIN' AND is_active = TRUE)
			)
	`, eventTypeID, userID)
	if err != nil {
		return err
	}
	return repositorycommon.RequireAffected(result)
}

func (r *Repository) ListEvents(ctx context.Context, userID, personID string, groups []string, from, to time.Time) ([]models.Event, error) {
	groupList := strings.Join(groups, ",")
	rows, err := r.db.QueryContext(ctx, `
		SELECT
			e.event_id, e.owner_user_id, e.title, COALESCE(e.description, ''),
			COALESCE(e.location, ''), e.starts_at, e.ends_at, e.visibility,
			sg.group_code, e.event_type_id, owner.schedule_privacy
		FROM events e
		JOIN users owner ON owner.user_id = e.owner_user_id
		LEFT JOIN student_groups sg ON sg.student_group_id = e.student_group_id
		WHERE e.starts_at >= $3 AND e.starts_at < $4
			AND (
				e.visibility = 'TEAM'
				OR (e.visibility = 'GROUP' AND sg.group_code = ANY(string_to_array($2, ',')))
				OR (e.visibility = 'PERSONAL' AND e.owner_user_id = $1)
			)
		ORDER BY e.starts_at
	`, personID, groupList, from, to)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	events := make([]models.Event, 0)
	for rows.Next() {
		var event models.Event
		var privacy string
		if err := rows.Scan(
			&event.ID, &event.OwnerID, &event.Title, &event.Description,
			&event.Location, &event.StartsAt, &event.EndsAt, &event.Visibility,
			&event.Group, &event.TypeID, &privacy,
		); err != nil {
			return nil, err
		}
		if !applyEventPrivacy(&event, userID, privacy) {
			continue
		}
		events = append(events, event)
	}
	return events, rows.Err()
}

func (r *Repository) GetEvent(ctx context.Context, userID, eventID string) (*models.Event, error) {
	var event models.Event
	var privacy string
	err := r.db.QueryRowContext(ctx, `
		SELECT
			e.event_id,
			e.owner_user_id,
			e.title,
			COALESCE(e.description, ''),
			COALESCE(e.location, ''),
			e.starts_at,
			e.ends_at,
			e.visibility,
			sg.group_code,
			e.event_type_id,
			owner.schedule_privacy
		FROM events e
		JOIN users owner ON owner.user_id = e.owner_user_id
		LEFT JOIN student_groups sg ON sg.student_group_id = e.student_group_id
		WHERE e.event_id = $2
			AND (
				e.visibility = 'TEAM'
				OR e.owner_user_id = $1
				OR (
					e.visibility = 'GROUP'
					AND (
						e.student_group_id = (
							SELECT student_group_id FROM users WHERE user_id = $1
						)
						OR EXISTS(
							SELECT 1 FROM users
							WHERE user_id = $1 AND system_role = 'CAPTAIN' AND is_active = TRUE
						)
					)
				)
				OR (e.visibility = 'PERSONAL' AND owner.schedule_privacy <> 'NOTHING')
			)
	`, userID, eventID).Scan(
		&event.ID, &event.OwnerID, &event.Title, &event.Description,
		&event.Location, &event.StartsAt, &event.EndsAt, &event.Visibility,
		&event.Group, &event.TypeID, &privacy,
	)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, repositorycommon.ErrNotFound
	}
	if err != nil {
		return nil, err
	}
	if !applyEventPrivacy(&event, userID, privacy) {
		return nil, repositorycommon.ErrNotFound
	}

	return &event, nil
}

func applyEventPrivacy(event *models.Event, viewerID, privacy string) bool {
	if event.Visibility != "PERSONAL" || event.OwnerID == viewerID {
		return true
	}
	switch privacy {
	case "NOTHING":
		return false
	case "BUSY":
		event.Title = "Занят"
		event.Description = ""
		event.Location = ""
		event.TypeID = nil
		event.Masked = true
	case "TITLE":
		event.Description = ""
		event.Location = ""
		event.Partial = true
	case "TITLE_AND_DESCRIPTION":
		event.Location = ""
		event.Partial = true
	}

	return true
}

func (r *Repository) CreateEvent(ctx context.Context, userID, title, description, location string, startsAt time.Time, endsAt *time.Time, visibility, group string, typeID *string, reminders []int) (string, error) {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return "", err
	}
	defer tx.Rollback()
	var groupID *string
	if visibility == "GROUP" {
		var id string
		if err := tx.QueryRowContext(ctx, `
			SELECT sg.student_group_id
			FROM student_groups sg
			WHERE sg.group_code = $1
				AND (
					EXISTS(SELECT 1 FROM users WHERE user_id = $2 AND system_role = 'CAPTAIN')
					OR EXISTS(SELECT 1 FROM users WHERE user_id = $2 AND student_group_id = sg.student_group_id)
				)
		`, group, userID).Scan(&id); err != nil {
			return "", repositorycommon.ErrForbidden
		}
		groupID = &id
	}
	var eventID string
	if err := tx.QueryRowContext(ctx, `
		INSERT INTO events (
			event_id, owner_user_id, event_type_id, student_group_id,
			title, description, location, visibility, starts_at, ends_at
		)
		VALUES (gen_random_uuid(), $1, $2, $3, $4, NULLIF($5, ''), NULLIF($6, ''), $7, $8, $9)
		RETURNING event_id
	`, userID, typeID, groupID, title, description, location, visibility, startsAt, endsAt).Scan(&eventID); err != nil {
		return "", err
	}
	if err := replaceEventReminders(ctx, tx, userID, eventID, reminders); err != nil {
		return "", err
	}
	return eventID, tx.Commit()
}

func (r *Repository) UpdateEvent(ctx context.Context, userID, eventID string, title, description, location *string, startsAt *time.Time, endsAt *time.Time, endsAtProvided bool, visibility, group *string, typeID *string, typeProvided bool) error {
	if err := r.requireEventOwnerOrCaptain(ctx, userID, eventID); err != nil {
		return err
	}
	var groupID *string
	if group != nil && *group != "" {
		var id string
		if err := r.db.QueryRowContext(ctx, `
			SELECT sg.student_group_id
			FROM student_groups sg
			WHERE sg.group_code = $1
				AND (
					EXISTS(SELECT 1 FROM users WHERE user_id = $2 AND system_role = 'CAPTAIN' AND is_active = TRUE)
					OR EXISTS(SELECT 1 FROM users WHERE user_id = $2 AND student_group_id = sg.student_group_id AND is_active = TRUE)
				)
		`, *group, userID).Scan(&id); err != nil {
			return repositorycommon.ErrNotFound
		}
		groupID = &id
	}
	result, err := r.db.ExecContext(ctx, `
		UPDATE events
		SET
			title = COALESCE($1, title),
			description = CASE WHEN $2 THEN NULLIF($3, '') ELSE description END,
			location = CASE WHEN $4 THEN NULLIF($5, '') ELSE location END,
			starts_at = COALESCE($6, starts_at),
			ends_at = CASE WHEN $7 THEN $8 ELSE ends_at END,
			visibility = COALESCE($9, visibility),
			student_group_id = CASE
				WHEN $9 IS NOT NULL AND $9 <> 'GROUP' THEN NULL
				WHEN $10 THEN $11
				ELSE student_group_id
			END,
			event_type_id = CASE WHEN $12 THEN $13 ELSE event_type_id END,
			updated_at = CURRENT_TIMESTAMP
		WHERE event_id = $14
	`, title, description != nil, description, location != nil, location, startsAt, endsAtProvided, endsAt, visibility, group != nil, groupID, typeProvided, typeID, eventID)
	if err != nil {
		return err
	}
	return repositorycommon.RequireAffected(result)
}

func (r *Repository) DeleteEvent(ctx context.Context, userID, eventID string) error {
	if err := r.requireEventOwnerOrCaptain(ctx, userID, eventID); err != nil {
		return err
	}
	result, err := r.db.ExecContext(ctx, `DELETE FROM events WHERE event_id = $1`, eventID)
	if err != nil {
		return err
	}
	return repositorycommon.RequireAffected(result)
}

func (r *Repository) requireEventOwnerOrCaptain(ctx context.Context, userID, eventID string) error {
	var allowed bool
	err := r.db.QueryRowContext(ctx, `
		SELECT EXISTS(
			SELECT 1 FROM events e JOIN users u ON u.user_id = $1
			WHERE e.event_id = $2 AND u.is_active = TRUE
				AND (e.owner_user_id = $1 OR u.system_role = 'CAPTAIN')
		)
	`, userID, eventID).Scan(&allowed)
	if err != nil {
		return err
	}
	if !allowed {
		return repositorycommon.ErrForbidden
	}
	return nil
}

func (r *Repository) GetReminders(ctx context.Context, userID string) (map[string][]int, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT item_key, minutes_before FROM user_reminders WHERE user_id = $1
		UNION ALL
		SELECT 'event:' || event_id::text, minutes_before FROM event_reminders WHERE user_id = $1
		ORDER BY 1, 2 DESC
	`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	result := make(map[string][]int)
	for rows.Next() {
		var key string
		var minute int
		if err := rows.Scan(&key, &minute); err != nil {
			return nil, err
		}
		result[key] = append(result[key], minute)
	}
	return result, rows.Err()
}

func (r *Repository) PutReminders(ctx context.Context, userID, itemKey string, minutes []int) error {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	if _, err := tx.ExecContext(ctx, `DELETE FROM user_reminders WHERE user_id = $1 AND item_key = $2`, userID, itemKey); err != nil {
		return err
	}
	for _, minute := range minutes {
		if _, err := tx.ExecContext(ctx, `
			INSERT INTO user_reminders (user_id, item_key, minutes_before) VALUES ($1, $2, $3)
		`, userID, itemKey, minute); err != nil {
			return err
		}
	}
	return tx.Commit()
}

func (r *Repository) typeReminders(ctx context.Context, eventTypeID string) ([]int, error) {
	rows, err := r.db.QueryContext(ctx, `SELECT minutes_before FROM event_type_reminders WHERE event_type_id = $1 ORDER BY minutes_before DESC`, eventTypeID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	result := make([]int, 0)
	for rows.Next() {
		var minute int
		if err := rows.Scan(&minute); err != nil {
			return nil, err
		}
		result = append(result, minute)
	}
	return result, rows.Err()
}

func replaceTypeReminders(ctx context.Context, tx *sql.Tx, eventTypeID string, reminders []int) error {
	if _, err := tx.ExecContext(ctx, `DELETE FROM event_type_reminders WHERE event_type_id = $1`, eventTypeID); err != nil {
		return err
	}
	for _, minute := range reminders {
		if _, err := tx.ExecContext(ctx, `INSERT INTO event_type_reminders (event_type_id, minutes_before) VALUES ($1, $2)`, eventTypeID, minute); err != nil {
			return err
		}
	}
	return nil
}

func replaceEventReminders(ctx context.Context, tx *sql.Tx, userID, eventID string, reminders []int) error {
	if _, err := tx.ExecContext(ctx, `DELETE FROM event_reminders WHERE user_id = $1 AND event_id = $2`, userID, eventID); err != nil {
		return err
	}
	for _, minute := range reminders {
		if _, err := tx.ExecContext(ctx, `INSERT INTO event_reminders (user_id, event_id, minutes_before) VALUES ($1, $2, $3)`, userID, eventID, minute); err != nil {
			return err
		}
	}
	return nil
}
