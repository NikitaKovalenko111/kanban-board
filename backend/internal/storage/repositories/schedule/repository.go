package schedule_repo

import (
	"context"
	"database/sql"
	"time"

	"kanban-board/internal/models"
	repositorycommon "kanban-board/internal/storage/repositories/common"
)

type SyncLesson struct {
	ExternalID, Title, Room, Teacher, Kind string
	StartsAt, EndsAt                       time.Time
}

func (r *Repository) ListGroupCodes(ctx context.Context) ([]string, error) {
	rows, err := r.db.QueryContext(ctx, `SELECT group_code FROM student_groups ORDER BY group_code`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := make([]string, 0)
	for rows.Next() {
		var value string
		if err := rows.Scan(&value); err != nil {
			return nil, err
		}
		items = append(items, value)
	}
	return items, rows.Err()
}

func (r *Repository) ReplaceLessons(ctx context.Context, group string, lessons []SyncLesson) error {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	var groupID string
	if err := tx.QueryRowContext(ctx, `SELECT student_group_id FROM student_groups WHERE group_code = $1`, group).Scan(&groupID); err != nil {
		return err
	}
	externalIDs := make([]string, 0, len(lessons))
	for _, lesson := range lessons {
		externalIDs = append(externalIDs, lesson.ExternalID)
		if _, err := tx.ExecContext(ctx, `
			INSERT INTO lessons (
				lesson_id, student_group_id, external_id, title, room, teacher,
				lesson_kind, starts_at, ends_at, source_status, source_updated_at
			)
			VALUES (gen_random_uuid(), $1, $2, $3, NULLIF($4, ''), NULLIF($5, ''), $6, $7, $8, 'ACTUAL', CURRENT_TIMESTAMP)
			ON CONFLICT (student_group_id, external_id) WHERE external_id IS NOT NULL
			DO UPDATE SET
				title = EXCLUDED.title,
				room = EXCLUDED.room,
				teacher = EXCLUDED.teacher,
				lesson_kind = EXCLUDED.lesson_kind,
				starts_at = EXCLUDED.starts_at,
				ends_at = EXCLUDED.ends_at,
				source_status = 'ACTUAL',
				source_updated_at = CURRENT_TIMESTAMP,
				updated_at = CURRENT_TIMESTAMP
		`, groupID, lesson.ExternalID, lesson.Title, lesson.Room, lesson.Teacher, lesson.Kind, lesson.StartsAt, lesson.EndsAt); err != nil {
			return err
		}
	}
	if _, err := tx.ExecContext(ctx, `
		DELETE FROM lessons
		WHERE student_group_id = $1
			AND starts_at >= CURRENT_DATE - INTERVAL '30 days'
			AND NOT (external_id = ANY($2::text[]))
	`, groupID, externalIDs); err != nil {
		return err
	}
	return tx.Commit()
}

func (r *Repository) MarkGroupCached(ctx context.Context, group string) error {
	_, err := r.db.ExecContext(ctx, `UPDATE lessons SET source_status = 'CACHED' WHERE student_group_id = (SELECT student_group_id FROM student_groups WHERE group_code = $1)`, group)
	return err
}

type Repository struct {
	db *sql.DB
}

func Init(db *sql.DB) *Repository {
	return &Repository{
		db: db,
	}
}

func (r *Repository) ListStudentGroups(ctx context.Context) ([]models.StudentGroup, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT
		    student_group_id,
		    group_code,
		    COALESCE(name,''),
		    created_at,
		    updated_at
		FROM student_groups
		ORDER BY group_code
	`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := make([]models.StudentGroup, 0)
	for rows.Next() {
		var g models.StudentGroup
		if err := rows.Scan(&g.StudentGroupID, &g.GroupCode, &g.Name, &g.CreatedAt, &g.UpdatedAt); err != nil {
			return nil, err
		}
		items = append(items, g)
	}
	return items, rows.Err()
}

func (r *Repository) ListLessons(ctx context.Context, groupCode string, from, to time.Time) (*models.Schedule, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT
			l.lesson_id,
			l.external_id,
			sg.group_code,
		    l.title,
		    COALESCE(l.room,''),
		    COALESCE(l.teacher,''),
		    l.starts_at,
		    l.ends_at,
			l.lesson_kind,
		    l.source_status,
			COALESCE(l.source_updated_at, l.updated_at)
		FROM lessons l
		JOIN student_groups sg ON sg.student_group_id=l.student_group_id
		WHERE sg.group_code=$1 AND l.starts_at >= $2 AND l.starts_at < $3
		ORDER BY l.starts_at
	`, groupCode, from, to)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	schedule := &models.Schedule{Lessons: make([]models.Lesson, 0), Source: "ACTUAL"}
	for rows.Next() {
		var l models.Lesson
		var startsAt, endsAt, updatedAt time.Time
		var source string
		if err := rows.Scan(&l.ID, &l.ExternalID, &l.Group, &l.Title, &l.Room, &l.Teacher, &startsAt, &endsAt, &l.Kind, &source, &updatedAt); err != nil {
			return nil, err
		}
		l.Date = startsAt.Format("2006-01-02")
		l.Start = startsAt.Format("15:04")
		l.End = endsAt.Format("15:04")
		if source == "UNAVAILABLE" || (source == "CACHED" && schedule.Source == "ACTUAL") {
			schedule.Source = source
		}
		if schedule.UpdatedAt == nil || updatedAt.After(*schedule.UpdatedAt) {
			schedule.UpdatedAt = &updatedAt
		}
		schedule.Lessons = append(schedule.Lessons, l)
	}
	return schedule, rows.Err()
}

func (r *Repository) GetLesson(ctx context.Context, lessonID string) (*models.Lesson, error) {
	var lesson models.Lesson
	var startsAt, endsAt time.Time
	err := r.db.QueryRowContext(ctx, `
		SELECT
			l.lesson_id,
			l.external_id,
			sg.group_code,
			l.title,
			COALESCE(l.room, ''),
			COALESCE(l.teacher, ''),
			l.starts_at,
			l.ends_at,
			l.lesson_kind
		FROM lessons l
		JOIN student_groups sg ON sg.student_group_id = l.student_group_id
		WHERE l.lesson_id = $1
	`, lessonID).Scan(
		&lesson.ID, &lesson.ExternalID, &lesson.Group, &lesson.Title,
		&lesson.Room, &lesson.Teacher, &startsAt, &endsAt, &lesson.Kind,
	)
	if err == sql.ErrNoRows {
		return nil, repositorycommon.ErrNotFound
	}
	if err != nil {
		return nil, err
	}
	lesson.Date = startsAt.Format("2006-01-02")
	lesson.Start = startsAt.Format("15:04")
	lesson.End = endsAt.Format("15:04")
	return &lesson, nil
}
