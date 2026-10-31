package task_repo

import "testing"

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
