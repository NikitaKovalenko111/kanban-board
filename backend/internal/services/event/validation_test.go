package event_service

import (
	"errors"
	"testing"
	"time"

	servicecommon "kanban-board/internal/services/common"
)

const testUUID = "550e8400-e29b-41d4-a716-446655440000"

func TestValidateEventType(t *testing.T) {
	t.Parallel()

	projectID := testUUID
	tests := []struct {
		name      string
		scope     string
		projectID *string
		wantErr   bool
	}{
		{name: "personal", scope: "PERSONAL"},
		{name: "project", scope: "PROJECT", projectID: &projectID},
		{name: "project without project ID", scope: "PROJECT", wantErr: true},
		{name: "personal with project ID", scope: "PERSONAL", projectID: &projectID, wantErr: true},
		{name: "global", scope: "GLOBAL", wantErr: true},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()

			err := validateEventType(testUUID, nil, "Защита проекта", "#3366FF", test.scope, test.projectID, []int{60})
			if (err != nil) != test.wantErr {
				t.Fatalf("validateEventType() error = %v, wantErr %v", err, test.wantErr)
			}
		})
	}
}

func TestValidateEventVisibility(t *testing.T) {
	t.Parallel()

	startsAt := time.Date(2026, time.November, 3, 10, 0, 0, 0, time.UTC)
	endsAt := startsAt.Add(time.Hour)
	projectID := testUUID
	tests := []struct {
		name         string
		visibility   string
		projectID    *string
		studentGroup string
		wantErr      bool
	}{
		{name: "personal", visibility: "PERSONAL"},
		{name: "project", visibility: "PROJECT", projectID: &projectID},
		{name: "group", visibility: "GROUP", studentGroup: "РИ-230912"},
		{name: "project without project ID", visibility: "PROJECT", wantErr: true},
		{name: "group without group", visibility: "GROUP", wantErr: true},
		{name: "personal with project ID", visibility: "PERSONAL", projectID: &projectID, wantErr: true},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()

			err := validateEvent(
				testUUID,
				nil,
				testUUID,
				"Демонстрация",
				"",
				"Аудитория 101",
				test.visibility,
				test.projectID,
				test.studentGroup,
				startsAt,
				endsAt,
				[]int{60},
			)
			if (err != nil) != test.wantErr {
				t.Fatalf("validateEvent() error = %v, wantErr %v", err, test.wantErr)
			}
			if err != nil && !errors.Is(err, servicecommon.ErrInvalidInput) {
				t.Fatalf("validateEvent() error = %v, want ErrInvalidInput", err)
			}
		})
	}
}
