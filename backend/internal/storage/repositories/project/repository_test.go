package project_repo

import (
	"encoding/json"
	"testing"

	"kanban-board/internal/models"
)

func TestMembersJSON(t *testing.T) {
	t.Parallel()
	members := []models.ProjectMember{{
		UserID: "5e66fa21-acde-4b33-9b60-37cc2b63ac22",
		Role:   "Разработчик",
	}}
	encoded := membersJSON(members)
	var decoded []map[string]string
	if err := json.Unmarshal([]byte(encoded), &decoded); err != nil {
		t.Fatalf("membersJSON() returned invalid JSON: %v", err)
	}
	if len(decoded) != 1 || decoded[0]["user_id"] != members[0].UserID || decoded[0]["role"] != members[0].Role {
		t.Fatalf("membersJSON() = %s", encoded)
	}
}
