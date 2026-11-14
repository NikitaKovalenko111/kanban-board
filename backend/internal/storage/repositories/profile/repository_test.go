package profile_repo

import (
	"context"
	"database/sql/driver"
	"regexp"
	"testing"

	"github.com/DATA-DOG/go-sqlmock"
)

func TestDeleteUserWithoutUnfinishedTasks(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock.New() error: %v", err)
	}
	defer db.Close()

	actorID := "550e8400-e29b-41d4-a716-446655440000"
	userID := "650e8400-e29b-41d4-a716-446655440001"

	mock.ExpectQuery(regexp.QuoteMeta(`
		SELECT
			EXISTS(
				SELECT 1
				FROM users
				WHERE user_id = $1
					AND system_role = 'CAPTAIN'
					AND is_active = TRUE
			)
	`)).
		WithArgs(actorID).
		WillReturnRows(sqlmock.NewRows([]string{"exists"}).AddRow(true))

	mock.ExpectQuery(regexp.QuoteMeta(`
		SELECT COUNT(*)
		FROM task_assignees ta
		JOIN tasks t ON t.task_id = ta.task_id
		JOIN board_columns bc ON bc.column_id = t.column_id
		WHERE ta.user_id = $1 AND bc.kind <> 'DONE'
	`)).
		WithArgs(userID).
		WillReturnRows(sqlmock.NewRows([]string{"count"}).AddRow(0))

	mock.ExpectBegin()
	expectDeleteUserExec(mock,
		`UPDATE events SET owner_user_id = $1 WHERE owner_user_id = $2 AND visibility <> 'PERSONAL'`,
		actorID,
		userID,
	)
	expectDeleteUserExec(mock,
		`DELETE FROM events WHERE owner_user_id = $1 AND visibility = 'PERSONAL'`,
		userID,
	)
	expectDeleteUserExec(mock, `DELETE FROM event_reminders WHERE user_id = $1`, userID)
	expectDeleteUserExec(mock, `DELETE FROM user_reminders WHERE user_id = $1`, userID)
	expectDeleteUserExec(mock, `DELETE FROM push_tokens WHERE user_id = $1`, userID)
	expectDeleteUserExec(mock,
		`UPDATE event_types SET created_by_user_id = $1 WHERE created_by_user_id = $2`,
		actorID,
		userID,
	)
	expectDeleteUserExec(mock,
		`UPDATE projects SET created_by_user_id = $1 WHERE created_by_user_id = $2`,
		actorID,
		userID,
	)
	expectDeleteUserExec(mock, `DELETE FROM project_members WHERE user_id = $1`, userID)
	expectDeleteUserExec(mock,
		`UPDATE sessions SET revoked_at = CURRENT_TIMESTAMP WHERE user_id = $1 AND revoked_at IS NULL`,
		userID,
	)
	expectDeleteUserExec(mock,
		`UPDATE users SET is_active = FALSE, username = username || '-deleted-' || user_id::text, display_name = 'Удалённый участник', image_url = NULL, contact = NULL, updated_at = CURRENT_TIMESTAMP WHERE user_id = $1`,
		userID,
	)
	mock.ExpectCommit()

	repository := Init(db)
	if err := repository.DeleteUser(context.Background(), actorID, userID); err != nil {
		t.Fatalf("DeleteUser() error: %v", err)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet database expectations: %v", err)
	}
}

func expectDeleteUserExec(mock sqlmock.Sqlmock, query string, args ...driver.Value) {
	mock.ExpectExec(regexp.QuoteMeta(query)).
		WithArgs(args...).
		WillReturnResult(sqlmock.NewResult(0, 1))
}
