package repositorycommon

import (
	"context"
	"database/sql"
	"errors"
)

var (
	ErrNotFound  = errors.New("resource not found")
	ErrForbidden = errors.New("operation is forbidden")
	ErrConflict  = errors.New("resource conflict")
)

type Queryer interface {
	QueryRowContext(context.Context, string, ...any) *sql.Row
}

func RequireMember(ctx context.Context, queryer Queryer, userID, projectID string) error {
	var exists bool
	err := queryer.QueryRowContext(ctx, `
		SELECT
			EXISTS(
				SELECT 1
				FROM project_members
				WHERE project_id = $1
					AND user_id = $2
					AND status = 'ACTIVE'
			)
	`, projectID, userID).Scan(&exists)
	if err != nil {
		return err
	}
	if !exists {
		return ErrForbidden
	}
	return nil
}

func RequireLead(ctx context.Context, queryer Queryer, userID, projectID string) error {
	var exists bool
	err := queryer.QueryRowContext(ctx, `
		SELECT
			EXISTS(
				SELECT 1
				FROM project_members
				WHERE project_id = $1
					AND user_id = $2
					AND role = 'LEAD'
					AND status = 'ACTIVE'
			)
	`, projectID, userID).Scan(&exists)
	if err != nil {
		return err
	}
	if !exists {
		return ErrForbidden
	}
	return nil
}

func RequireTaskMember(ctx context.Context, queryer Queryer, userID, taskID string) (string, error) {
	var projectID string
	err := queryer.QueryRowContext(ctx, `
		SELECT
		    b.project_id
		FROM tasks t
		JOIN board_columns bc ON bc.column_id=t.column_id
		JOIN boards b ON b.board_id=bc.board_id
		WHERE t.task_id=$1
	`, taskID).Scan(&projectID)
	if errors.Is(err, sql.ErrNoRows) {
		return "", ErrNotFound
	}
	if err != nil {
		return "", err
	}
	if err := RequireMember(ctx, queryer, userID, projectID); err != nil {
		return "", err
	}
	return projectID, nil
}

func RequireAffected(result sql.Result) error {
	count, err := result.RowsAffected()
	if err != nil {
		return err
	}
	if count == 0 {
		return ErrNotFound
	}
	return nil
}
