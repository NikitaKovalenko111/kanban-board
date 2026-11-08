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
	return &Repository{db: db}
}

type scanner interface {
	Scan(...any) error
}

func scanProfile(row scanner) (*models.Profile, error) {
	var profile models.Profile
	var imageURL, contact sql.NullString
	err := row.Scan(
		&profile.ID,
		&profile.FullName,
		&profile.Username,
		&profile.StudentGroup,
		&imageURL,
		&profile.Role,
		&profile.SchedulePrivacy,
		&profile.JoinedAt,
		&profile.Active,
		&contact,
	)
	if err != nil {
		return nil, err
	}
	if imageURL.Valid {
		profile.ImageURL = &imageURL.String
	}
	if contact.Valid {
		profile.Contact = &contact.String
	}

	return &profile, nil
}

func profileSelect() string {
	return `
		SELECT
			u.user_id,
			u.display_name,
			u.username,
			COALESCE(sg.group_code, ''),
			u.image_url,
			u.system_role,
			u.schedule_privacy,
			u.joined_at,
			u.is_active,
			u.contact
		FROM users u
		LEFT JOIN student_groups sg ON sg.student_group_id = u.student_group_id
	`
}

func (r *Repository) GetProfile(ctx context.Context, userID string) (*models.Profile, error) {
	profile, err := scanProfile(r.db.QueryRowContext(ctx, profileSelect()+`WHERE u.user_id = $1`, userID))
	if errors.Is(err, sql.ErrNoRows) {
		return nil, repositorycommon.ErrNotFound
	}
	if err != nil {
		return nil, fmt.Errorf("get profile: %w", err)
	}

	return profile, nil
}

func (r *Repository) ListUsers(ctx context.Context, actorID, search string) ([]models.Profile, error) {
	rows, err := r.db.QueryContext(ctx, profileSelect()+`
		WHERE (
			u.is_active = TRUE
			OR EXISTS(
				SELECT 1
				FROM users actor
				WHERE actor.user_id = $1
					AND actor.system_role = 'CAPTAIN'
					AND actor.is_active = TRUE
			)
		)
		AND (
			$2 = ''
			OR u.display_name ILIKE '%' || $2 || '%'
			OR u.username ILIKE '%' || $2 || '%'
		)
		ORDER BY u.display_name
	`, actorID, search)
	if err != nil {
		return nil, fmt.Errorf("list users: %w", err)
	}
	defer rows.Close()

	users := make([]models.Profile, 0)
	for rows.Next() {
		profile, err := scanProfile(rows)
		if err != nil {
			return nil, fmt.Errorf("scan user: %w", err)
		}
		users = append(users, *profile)
	}

	return users, rows.Err()
}

func (r *Repository) UpdateProfile(ctx context.Context, userID string, username, fullName, studentGroup, schedulePrivacy *string) error {
	return r.updateUserFields(ctx, userID, username, fullName, studentGroup, schedulePrivacy)
}

func (r *Repository) UpdateUser(ctx context.Context, actorID, userID string, username, studentGroup *string) error {
	if err := repositorycommon.RequireCaptain(ctx, r.db, actorID); err != nil {
		return err
	}
	return r.updateUserFields(ctx, userID, username, nil, studentGroup, nil)
}

func (r *Repository) updateUserFields(ctx context.Context, userID string, username, fullName, studentGroup, schedulePrivacy *string) error {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	var groupID *string
	if studentGroup != nil && *studentGroup != "" {
		var id string
		if err := tx.QueryRowContext(ctx, `
			INSERT INTO student_groups (student_group_id, group_code, name)
			VALUES (gen_random_uuid(), $1, $1)
			ON CONFLICT (group_code)
			DO UPDATE SET updated_at = CURRENT_TIMESTAMP
			RETURNING student_group_id
		`, *studentGroup).Scan(&id); err != nil {
			return err
		}
		groupID = &id
	}

	result, err := tx.ExecContext(ctx, `
		UPDATE users
		SET
			username = COALESCE($1, username),
			display_name = COALESCE($2, display_name),
			student_group_id = CASE WHEN $3 THEN $4 ELSE student_group_id END,
			schedule_privacy = COALESCE($5, schedule_privacy),
			updated_at = CURRENT_TIMESTAMP
		WHERE user_id = $6
	`, username, fullName, studentGroup != nil, groupID, schedulePrivacy, userID)
	if err != nil {
		return fmt.Errorf("update profile: %w", err)
	}
	if err := repositorycommon.RequireAffected(result); err != nil {
		return err
	}

	return tx.Commit()
}

func (r *Repository) SetPhoto(ctx context.Context, userID, imageURL string) error {
	result, err := r.db.ExecContext(ctx, `
		UPDATE users
		SET image_url = NULLIF($1, ''), updated_at = CURRENT_TIMESTAMP
		WHERE user_id = $2
	`, imageURL, userID)
	if err != nil {
		return err
	}

	return repositorycommon.RequireAffected(result)
}

func (r *Repository) CreateUser(ctx context.Context, actorID, fullName, username, passwordHash, studentGroup, role string) (string, error) {
	if err := repositorycommon.RequireCaptain(ctx, r.db, actorID); err != nil {
		return "", err
	}

	var userID string
	err := r.db.QueryRowContext(ctx, `
		WITH selected_group AS (
			INSERT INTO student_groups (student_group_id, group_code, name)
			VALUES (gen_random_uuid(), $4, $4)
			ON CONFLICT (group_code)
			DO UPDATE SET updated_at = CURRENT_TIMESTAMP
			RETURNING student_group_id
		)
		INSERT INTO users (
			user_id,
			username,
			display_name,
			student_group_id,
			system_role,
			password_hash
		)
		SELECT gen_random_uuid(), $1, $2, student_group_id, $5, $3
		FROM selected_group
		RETURNING user_id
	`, username, fullName, passwordHash, studentGroup, role).Scan(&userID)
	if err != nil {
		return "", fmt.Errorf("create user: %w", err)
	}

	return userID, nil
}

func (r *Repository) SetStatus(ctx context.Context, actorID, userID, status string) error {
	if actorID == userID {
		return repositorycommon.ErrForbidden
	}
	if err := repositorycommon.RequireCaptain(ctx, r.db, actorID); err != nil {
		return err
	}

	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	result, err := tx.ExecContext(ctx, `
		UPDATE users
		SET is_active = $1, updated_at = CURRENT_TIMESTAMP
		WHERE user_id = $2
	`, status == "ACTIVE", userID)
	if err != nil {
		return err
	}
	if err := repositorycommon.RequireAffected(result); err != nil {
		return err
	}
	if status == "BLOCKED" {
		if _, err := tx.ExecContext(ctx, `
			UPDATE sessions SET revoked_at = CURRENT_TIMESTAMP
			WHERE user_id = $1 AND revoked_at IS NULL
		`, userID); err != nil {
			return err
		}
	}

	return tx.Commit()
}

func (r *Repository) SetRole(ctx context.Context, actorID, userID, role string) error {
	if actorID == userID {
		return repositorycommon.ErrForbidden
	}
	if err := repositorycommon.RequireCaptain(ctx, r.db, actorID); err != nil {
		return err
	}
	result, err := r.db.ExecContext(ctx, `
		UPDATE users SET system_role = $1, updated_at = CURRENT_TIMESTAMP
		WHERE user_id = $2
	`, role, userID)
	if err != nil {
		return err
	}

	return repositorycommon.RequireAffected(result)
}

func (r *Repository) ResetPassword(ctx context.Context, actorID, userID, passwordHash string) error {
	if err := repositorycommon.RequireCaptain(ctx, r.db, actorID); err != nil {
		return err
	}
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	result, err := tx.ExecContext(ctx, `
		UPDATE users SET password_hash = $1, updated_at = CURRENT_TIMESTAMP
		WHERE user_id = $2
	`, passwordHash, userID)
	if err != nil {
		return err
	}
	if err := repositorycommon.RequireAffected(result); err != nil {
		return err
	}
	if _, err := tx.ExecContext(ctx, `
		UPDATE sessions SET revoked_at = CURRENT_TIMESTAMP
		WHERE user_id = $1 AND revoked_at IS NULL
	`, userID); err != nil {
		return err
	}

	return tx.Commit()
}

func (r *Repository) DeleteUser(ctx context.Context, actorID, userID string) error {
	if actorID == userID {
		return repositorycommon.ErrForbidden
	}
	if err := repositorycommon.RequireCaptain(ctx, r.db, actorID); err != nil {
		return err
	}

	var unfinished int
	if err := r.db.QueryRowContext(ctx, `
		SELECT COUNT(*)
		FROM task_assignees ta
		JOIN tasks t ON t.task_id = ta.task_id
		JOIN board_columns bc ON bc.column_id = t.column_id
		WHERE ta.user_id = $1 AND bc.kind <> 'DONE'
	`, userID).Scan(&unfinished); err != nil {
		return err
	}
	if unfinished > 0 {
		return fmt.Errorf(
			"%w: У участника незакрытых задач: %d — сначала переназначьте их",
			repositorycommon.ErrConflict,
			unfinished,
		)
	}

	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	queries := []string{
		`UPDATE events SET owner_user_id = $1 WHERE owner_user_id = $2 AND visibility <> 'PERSONAL'`,
		`DELETE FROM events WHERE owner_user_id = $2 AND visibility = 'PERSONAL'`,
		`DELETE FROM event_reminders WHERE user_id = $2`,
		`DELETE FROM user_reminders WHERE user_id = $2`,
		`DELETE FROM push_tokens WHERE user_id = $2`,
		`UPDATE event_types SET created_by_user_id = $1 WHERE created_by_user_id = $2`,
		`UPDATE projects SET created_by_user_id = $1 WHERE created_by_user_id = $2`,
		`DELETE FROM project_members WHERE user_id = $2`,
		`UPDATE sessions SET revoked_at = CURRENT_TIMESTAMP WHERE user_id = $2 AND revoked_at IS NULL`,
		`UPDATE users SET is_active = FALSE, username = username || '-deleted-' || user_id::text, display_name = 'Удалённый участник', image_url = NULL, contact = NULL, updated_at = CURRENT_TIMESTAMP WHERE user_id = $2`,
	}
	for _, query := range queries {
		if _, err := tx.ExecContext(ctx, query, actorID, userID); err != nil {
			return err
		}
	}

	return tx.Commit()
}
