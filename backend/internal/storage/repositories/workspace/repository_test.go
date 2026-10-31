package workspace_repo

import (
	"context"
	"regexp"
	"testing"

	"github.com/DATA-DOG/go-sqlmock"
)

func TestCreateProjectCreatesLeadBoardAndDefaultColumns(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock.New(): %v", err)
	}
	defer db.Close()

	repository := Init(db)
	userID := "5e66fa21-acde-4b33-9b60-37cc2b63ac22"
	projectID := "3c3a113d-15ac-4273-b55f-e5f03ad61d42"
	boardID := "1637fcaa-32d2-436c-ad65-e379314429f1"

	mock.ExpectBegin()
	mock.ExpectQuery(regexp.QuoteMeta(`
		INSERT INTO projects (project_id, created_by_user_id, name, description, join_policy)
		VALUES (gen_random_uuid(), $1, $2, NULLIF($3, ''), $4) RETURNING project_id
	`)).WithArgs(userID, "Mobile", "Android client", "INVITE_ONLY").
		WillReturnRows(sqlmock.NewRows([]string{"project_id"}).AddRow(projectID))
	mock.ExpectExec(regexp.QuoteMeta(`INSERT INTO project_members (project_id, user_id, role) VALUES ($1, $2, 'LEAD')`)).
		WithArgs(projectID, userID).WillReturnResult(sqlmock.NewResult(0, 1))
	mock.ExpectQuery(regexp.QuoteMeta(`INSERT INTO boards (board_id, project_id, name) VALUES (gen_random_uuid(), $1, 'Kanban') RETURNING board_id`)).
		WithArgs(projectID).WillReturnRows(sqlmock.NewRows([]string{"board_id"}).AddRow(boardID))
	mock.ExpectExec(regexp.QuoteMeta(`
		INSERT INTO board_columns (column_id, board_id, code, name, position) VALUES
			(gen_random_uuid(), $1, 'WAITING', 'Ожидает', 0),
			(gen_random_uuid(), $1, 'WORKING', 'В работе', 1),
			(gen_random_uuid(), $1, 'CHECKING', 'На проверке', 2),
			(gen_random_uuid(), $1, 'DONE', 'Готово', 3)
	`)).WithArgs(boardID).WillReturnResult(sqlmock.NewResult(0, 4))
	mock.ExpectCommit()

	actualID, err := repository.CreateProject(context.Background(), userID, "Mobile", "Android client", "INVITE_ONLY")
	if err != nil {
		t.Fatalf("CreateProject(): %v", err)
	}
	if actualID != projectID {
		t.Fatalf("project id = %q, want %q", actualID, projectID)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet SQL expectations: %v", err)
	}
}

func TestAllowedTransition(t *testing.T) {
	tests := []struct {
		name              string
		role              string
		assigned          bool
		from, to, comment string
		want              bool
	}{
		{"assignee starts", "MEMBER", true, "WAITING", "WORKING", "", true},
		{"assignee submits", "MEMBER", true, "WORKING", "CHECKING", "", true},
		{"lead accepts", "LEAD", false, "CHECKING", "DONE", "", true},
		{"lead returns with comment", "LEAD", false, "CHECKING", "WORKING", "fix it", true},
		{"lead cannot return without comment", "LEAD", false, "CHECKING", "WORKING", "", false},
		{"unassigned member cannot move", "MEMBER", false, "WAITING", "WORKING", "", false},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			if got := allowedTransition(test.role, test.assigned, test.from, test.to, test.comment); got != test.want {
				t.Fatalf("allowedTransition() = %v, want %v", got, test.want)
			}
		})
	}
}
