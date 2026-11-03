package schedule_repo

import (
	"context"
	"database/sql"
	"kanban-board/internal/models"
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
			COALESCE(l.external_id, l.lesson_id::text),
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
		if err := rows.Scan(&l.ID, &l.Group, &l.Title, &l.Room, &l.Teacher, &startsAt, &endsAt, &l.Kind, &source, &updatedAt); err != nil {
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
