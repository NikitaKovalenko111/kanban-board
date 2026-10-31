package profile_repo

import (
	"context"
	"database/sql"
	"errors"
	"fmt"

	"kanban-board/internal/models"
	repositorycommon "kanban-board/internal/storage/repositories/common"
)

type Repository struct {
	db *sql.DB
}

func Init(db *sql.DB) *Repository {
	return &Repository{
		db: db,
	}
}

func (r *Repository) GetProfile(ctx context.Context, userID string) (*models.Profile, error) {
	var profile models.Profile
	err := r.db.QueryRowContext(ctx, `
		SELECT
			u.user_id,
			u.username,
			u.email,
			u.display_name,
			COALESCE(u.position, ''),
			COALESCE(sg.group_code, ''),
			COALESCE(u.image_url, ''),
			u.is_active
		FROM users u
		LEFT JOIN student_groups sg ON sg.student_group_id = u.student_group_id
		WHERE u.user_id = $1
	`, userID).Scan(&profile.ID, &profile.Username, &profile.Email, &profile.DisplayName,
		&profile.Position, &profile.StudentGroup, &profile.ImageURL, &profile.Active)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, repositorycommon.ErrNotFound
	}
	if err != nil {
		return nil, fmt.Errorf("get profile: %w", err)
	}
	rows, err := r.db.QueryContext(ctx, `
		SELECT
			project_id,
			user_id,
			role,
			status,
			joined_at
		FROM project_members
		WHERE user_id = $1
			AND status <> 'LEFT'
		ORDER BY joined_at
	`, userID)
	if err != nil {
		return nil, fmt.Errorf("get memberships: %w", err)
	}
	defer rows.Close()
	profile.Memberships = make([]models.Membership, 0)
	for rows.Next() {
		var membership models.Membership
		if err := rows.Scan(&membership.ProjectID, &membership.UserID, &membership.Role, &membership.Status, &membership.JoinedAt); err != nil {
			return nil, fmt.Errorf("scan membership: %w", err)
		}
		profile.Memberships = append(profile.Memberships, membership)
	}
	return &profile, rows.Err()
}

func (r *Repository) UpdateProfile(ctx context.Context, userID, displayName, position, groupCode, imageURL string) error {
	result, err := r.db.ExecContext(ctx, `
		WITH selected_group AS (
			INSERT INTO student_groups (
				student_group_id,
				group_code,
				name
			)
			SELECT
				gen_random_uuid(),
				NULLIF($3, ''),
				NULLIF($3, '')
			WHERE NULLIF($3, '') IS NOT NULL
			ON CONFLICT (group_code)
			DO UPDATE SET updated_at = CURRENT_TIMESTAMP
			RETURNING student_group_id
		)
		UPDATE users
		SET
			display_name = $1,
			position = NULLIF($2, ''),
			student_group_id = (
				SELECT student_group_id
				FROM selected_group
			),
			image_url = NULLIF($4, ''),
			updated_at = CURRENT_TIMESTAMP
		WHERE user_id = $5
	`, displayName, position, groupCode, imageURL, userID)
	if err != nil {
		return fmt.Errorf("update profile: %w", err)
	}
	return repositorycommon.RequireAffected(result)
}
