package task_repo

import "testing"

func TestAllowedMove(t *testing.T) {
	tests := []struct {
		name              string
		role              string
		assigned          bool
		from, to, comment string
		want              bool
	}{
		{"assignee starts", "MEMBER", true, "BACKLOG", "IN_PROGRESS", "", true},
		{"assignee submits", "MEMBER", true, "IN_PROGRESS", "REVIEW", "", true},
		{"captain accepts", "CAPTAIN", false, "REVIEW", "DONE", "", true},
		{"captain returns with comment", "CAPTAIN", false, "REVIEW", "IN_PROGRESS", "исправить", true},
		{"captain cannot return without comment", "CAPTAIN", false, "REVIEW", "IN_PROGRESS", "", false},
		{"unassigned member cannot move", "MEMBER", false, "BACKLOG", "IN_PROGRESS", "", false},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			if got := allowedMove(test.role, test.assigned, test.from, test.to, test.comment); got != test.want {
				t.Fatalf("allowedMove() = %v, want %v", got, test.want)
			}
		})
	}
}
