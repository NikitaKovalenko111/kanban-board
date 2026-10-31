package event_repo

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"kanban-board/internal/models"
	repositorycommon "kanban-board/internal/storage/repositories/common"
	"time"
)

type Repository struct {
	db *sql.DB
}

func Init(db *sql.DB) *Repository {
	return &Repository{
		db: db,
	}
}

func (r *Repository) ListEventTypes(ctx context.Context, userID string) ([]models.EventType, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT
		    et.event_type_id,
		    COALESCE(et.code,''),
		    et.title,
		    et.color,
		    et.scope,
		    et.created_by_user_id,
		    et.project_id,
		    et.created_at,
		    et.updated_at
		FROM event_types et
		WHERE et.scope = 'GLOBAL'
			OR et.created_by_user_id = $1
			OR EXISTS(
				SELECT 1
				FROM project_members pm
				WHERE pm.project_id = et.project_id
					AND pm.user_id = $1
					AND pm.status = 'ACTIVE'
			)
		ORDER BY et.title
	`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := make([]models.EventType, 0)
	for rows.Next() {
		var et models.EventType
		if err := rows.Scan(&et.ID, &et.Code, &et.Title, &et.Color, &et.Scope, &et.CreatedByUserID, &et.ProjectID, &et.CreatedAt, &et.UpdatedAt); err != nil {
			return nil, err
		}
		et.DefaultReminderMinutes, err = r.reminders(ctx, "event_type_reminders", "event_type_id", et.ID)
		if err != nil {
			return nil, err
		}
		items = append(items, et)
	}
	return items, rows.Err()
}

func (r *Repository) GetEventType(ctx context.Context, userID, eventTypeID string) (*models.EventType, error) {
	items, err := r.ListEventTypes(ctx, userID)
	if err != nil {
		return nil, err
	}
	for i := range items {
		if items[i].ID == eventTypeID {
			return &items[i], nil
		}
	}
	return nil, repositorycommon.ErrNotFound
}

func (r *Repository) CreateEventType(ctx context.Context, userID, title, color, scope string, projectID *string, minutes []int) (string, error) {
	if projectID != nil {
		if err := repositorycommon.RequireMember(ctx, r.db, userID, *projectID); err != nil {
			return "", err
		}
	}
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return "", err
	}
	defer tx.Rollback()
	var id string
	if err := tx.QueryRowContext(ctx, `
		INSERT INTO event_types (
		    event_type_id,
		    title,
		    color,
		    scope,
		    created_by_user_id,
		    project_id
		)
		VALUES
		    (gen_random_uuid(),$1,$2,$3,$4,$5)
		RETURNING event_type_id
	`, title, color, scope, userID, projectID).Scan(&id); err != nil {
		return "", err
	}
	if err := replaceReminders(ctx, tx, "event_type_reminders", "event_type_id", id, minutes); err != nil {
		return "", err
	}
	if err := tx.Commit(); err != nil {
		return "", err
	}
	return id, nil
}

func (r *Repository) UpdateEventType(ctx context.Context, userID, eventTypeID, title, color string, minutes []int) error {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	result, err := tx.ExecContext(ctx, `
		UPDATE event_types
		SET
		    title=$1,
		    color=$2,
		    updated_at=CURRENT_TIMESTAMP
		WHERE event_type_id=$3 AND created_by_user_id=$4
	`, title, color, eventTypeID, userID)
	if err != nil {
		return err
	}
	if err := repositorycommon.RequireAffected(result); err != nil {
		return err
	}
	if err := replaceReminders(ctx, tx, "event_type_reminders", "event_type_id", eventTypeID, minutes); err != nil {
		return err
	}
	return tx.Commit()
}

func (r *Repository) DeleteEventType(ctx context.Context, userID, eventTypeID string) error {
	result, err := r.db.ExecContext(ctx, `
		DELETE FROM event_types
		WHERE event_type_id=$1 AND created_by_user_id=$2
	`, eventTypeID, userID)
	if err != nil {
		return err
	}
	return repositorycommon.RequireAffected(result)
}

func (r *Repository) ListEvents(ctx context.Context, userID string, projectID *string, groupCode string, from, to time.Time) ([]models.Event, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT
			e.event_id,
			e.owner_user_id,
			e.event_type_id,
			e.project_id,
			e.student_group_id,
			COALESCE(sg.group_code, ''),
			e.title,
			COALESCE(e.description, ''),
			COALESCE(e.location, ''),
			e.visibility,
			e.starts_at,
			e.ends_at,
			e.created_at,
			e.updated_at
		FROM events e
		LEFT JOIN student_groups sg ON sg.student_group_id = e.student_group_id
		WHERE e.starts_at >= $4
			AND e.starts_at < $5
			AND (
				(e.visibility = 'PERSONAL' AND e.owner_user_id = $1)
				OR (
					e.visibility = 'PROJECT'
					AND e.project_id = $2
					AND EXISTS(
						SELECT 1
						FROM project_members pm
						WHERE pm.project_id = e.project_id
							AND pm.user_id = $1
							AND pm.status = 'ACTIVE'
					)
				)
				OR (
					e.visibility = 'GROUP'
					AND sg.group_code = $3
					AND EXISTS(
						SELECT 1
						FROM users u
						WHERE u.user_id = $1
							AND u.student_group_id = e.student_group_id
					)
				)
			)
		ORDER BY e.starts_at
	`, userID, projectID, groupCode, from, to)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := make([]models.Event, 0)
	for rows.Next() {
		var e models.Event
		if err := rows.Scan(&e.ID, &e.OwnerUserID, &e.EventTypeID, &e.ProjectID, &e.StudentGroupID, &e.StudentGroup, &e.Title, &e.Description, &e.Location, &e.Visibility, &e.StartsAt, &e.EndsAt, &e.CreatedAt, &e.UpdatedAt); err != nil {
			return nil, err
		}
		e.ReminderMinutes, err = r.reminders(ctx, "event_reminders", "event_id", e.ID)
		if err != nil {
			return nil, err
		}
		items = append(items, e)
	}
	return items, rows.Err()
}

func (r *Repository) GetEvent(ctx context.Context, userID, eventID string) (*models.Event, error) {
	var event models.Event
	err := r.db.QueryRowContext(ctx, `
		SELECT
			e.event_id,
			e.owner_user_id,
			e.event_type_id,
			e.project_id,
			e.student_group_id,
			COALESCE(sg.group_code, ''),
			e.title,
			COALESCE(e.description, ''),
			COALESCE(e.location, ''),
			e.visibility,
			e.starts_at,
			e.ends_at,
			e.created_at,
			e.updated_at
		FROM events e
		LEFT JOIN student_groups sg ON sg.student_group_id = e.student_group_id
		WHERE e.event_id = $2
			AND (
				(e.visibility = 'PERSONAL' AND e.owner_user_id = $1)
				OR (
					e.visibility = 'PROJECT'
					AND EXISTS(
						SELECT 1
						FROM project_members pm
						WHERE pm.project_id = e.project_id
							AND pm.user_id = $1
							AND pm.status = 'ACTIVE'
					)
				)
				OR (
					e.visibility = 'GROUP'
					AND EXISTS(
						SELECT 1
						FROM users u
						WHERE u.user_id = $1
							AND u.student_group_id = e.student_group_id
					)
				)
		)
	`, userID, eventID).Scan(&event.ID, &event.OwnerUserID, &event.EventTypeID, &event.ProjectID,
		&event.StudentGroupID, &event.StudentGroup, &event.Title, &event.Description, &event.Location,
		&event.Visibility, &event.StartsAt, &event.EndsAt, &event.CreatedAt, &event.UpdatedAt)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, repositorycommon.ErrNotFound
	}
	if err != nil {
		return nil, err
	}
	event.ReminderMinutes, err = r.reminders(ctx, "event_reminders", "event_id", event.ID)
	return &event, err
}

func (r *Repository) CreateEvent(ctx context.Context, userID, eventTypeID, title, description, location, visibility string, projectID *string, groupCode string, startsAt, endsAt time.Time, minutes []int) (string, error) {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return "", err
	}
	defer tx.Rollback()
	if visibility == "PROJECT" {
		if projectID == nil {
			return "", repositorycommon.ErrConflict
		}
		if err := repositorycommon.RequireMember(ctx, tx, userID, *projectID); err != nil {
			return "", err
		}
	}
	var groupID *string
	if visibility == "GROUP" {
		var id string
		if err := tx.QueryRowContext(ctx, `
		SELECT
		    student_group_id
		FROM student_groups
		WHERE group_code=$1
	`, groupCode).Scan(&id); err != nil {
			return "", repositorycommon.ErrNotFound
		}
		groupID = &id
		var ownGroup string
		if err := tx.QueryRowContext(ctx, `
		SELECT
		    COALESCE(student_group_id::text,'')
		FROM users
		WHERE user_id=$1
	`, userID).Scan(&ownGroup); err != nil || ownGroup != id {
			return "", repositorycommon.ErrForbidden
		}
	}
	var id string
	if err := tx.QueryRowContext(ctx, `
		INSERT INTO events (
		    event_id,
		    owner_user_id,
		    event_type_id,
		    project_id,
		    student_group_id,
		    title,
		    description,
		    location,
		    visibility,
		    starts_at,
		    ends_at
		)
		VALUES
		    (gen_random_uuid(),$1,$2,$3,$4,$5,NULLIF($6,''),NULLIF($7,''),$8,$9,$10)
		RETURNING event_id
	`, userID, eventTypeID, projectID, groupID, title, description, location, visibility, startsAt, endsAt).Scan(&id); err != nil {
		return "", err
	}
	if len(minutes) == 0 {
		rows, err := tx.QueryContext(ctx, `
		SELECT
		    minutes_before
		FROM event_type_reminders
		WHERE event_type_id=$1
	`, eventTypeID)
		if err != nil {
			return "", err
		}
		for rows.Next() {
			var minute int
			if err := rows.Scan(&minute); err != nil {
				rows.Close()
				return "", err
			}
			minutes = append(minutes, minute)
		}
		rows.Close()
	}
	if err := replaceReminders(ctx, tx, "event_reminders", "event_id", id, minutes); err != nil {
		return "", err
	}
	if err := tx.Commit(); err != nil {
		return "", err
	}
	return id, nil
}

func (r *Repository) UpdateEvent(ctx context.Context, userID, eventID, title, description, location string, startsAt, endsAt time.Time, minutes []int) error {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	result, err := tx.ExecContext(ctx, `
		UPDATE events
		SET
		    title=$1,
		    description=NULLIF($2,''),
		    location=NULLIF($3,''),
		    starts_at=$4,
		    ends_at=$5,
		    updated_at=CURRENT_TIMESTAMP
		WHERE event_id=$6 AND owner_user_id=$7
	`, title, description, location, startsAt, endsAt, eventID, userID)
	if err != nil {
		return err
	}
	if err := repositorycommon.RequireAffected(result); err != nil {
		return err
	}
	if err := replaceReminders(ctx, tx, "event_reminders", "event_id", eventID, minutes); err != nil {
		return err
	}
	return tx.Commit()
}

func (r *Repository) DeleteEvent(ctx context.Context, userID, eventID string) error {
	result, err := r.db.ExecContext(ctx, `
		DELETE FROM events
		WHERE event_id=$1 AND owner_user_id=$2
	`, eventID, userID)
	if err != nil {
		return err
	}
	return repositorycommon.RequireAffected(result)
}

func (r *Repository) reminders(ctx context.Context, table, column, id string) ([]int, error) {
	query := fmt.Sprintf(`
		SELECT
			minutes_before
		FROM %s
		WHERE %s = $1
		ORDER BY minutes_before DESC
	`, table, column)
	rows, err := r.db.QueryContext(ctx, query, id)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := make([]int, 0)
	for rows.Next() {
		var minute int
		if err := rows.Scan(&minute); err != nil {
			return nil, err
		}
		items = append(items, minute)
	}
	return items, rows.Err()
}

func replaceReminders(ctx context.Context, tx *sql.Tx, table, column, id string, minutes []int) error {
	deleteQuery := fmt.Sprintf(`
		DELETE FROM %s
		WHERE %s = $1
	`, table, column)
	if _, err := tx.ExecContext(ctx, deleteQuery, id); err != nil {
		return err
	}
	insertQuery := fmt.Sprintf(`
		INSERT INTO %s (
			%s,
			minutes_before
		)
		VALUES ($1, $2)
	`, table, column)
	for _, minute := range minutes {
		if minute < 0 {
			return repositorycommon.ErrConflict
		}
		if _, err := tx.ExecContext(ctx, insertQuery, id, minute); err != nil {
			return err
		}
	}
	return nil
}
