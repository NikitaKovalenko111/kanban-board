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

func (r *Repository) ListLessons(ctx context.Context, groupCode string, from, to time.Time) ([]models.Lesson, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT
		    l.lesson_id,
		    l.student_group_id,
		    COALESCE(l.external_id,''),
		    l.title,
		    COALESCE(l.room,''),
		    COALESCE(l.teacher,''),
		    l.lesson_kind,
		    l.starts_at,
		    l.ends_at,
		    l.source_status,
		    l.source_updated_at,
		    l.created_at,
		    l.updated_at
		FROM lessons l
		JOIN student_groups sg ON sg.student_group_id=l.student_group_id
		WHERE sg.group_code=$1 AND l.starts_at >= $2 AND l.starts_at < $3
		ORDER BY l.starts_at
	`, groupCode, from, to)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := make([]models.Lesson, 0)
	for rows.Next() {
		var l models.Lesson
		if err := rows.Scan(&l.LessonID, &l.StudentGroupID, &l.ExternalID, &l.Title, &l.Room, &l.Teacher, &l.LessonKind, &l.StartsAt, &l.EndsAt, &l.SourceStatus, &l.SourceUpdatedAt, &l.CreatedAt, &l.UpdatedAt); err != nil {
			return nil, err
		}
		items = append(items, l)
	}
	return items, rows.Err()
}
