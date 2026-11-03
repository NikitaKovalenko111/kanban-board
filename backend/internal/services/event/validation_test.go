package event_service

import (
	"testing"
	"time"
)

func TestValidateEventData(t *testing.T) {
	t.Parallel()
	startsAt := time.Date(2026, time.November, 3, 10, 0, 0, 0, time.UTC)
	endsAt := startsAt.Add(time.Hour)
	tests := []struct {
		name       string
		visibility string
		group      string
		wantErr    bool
	}{
		{name: "personal", visibility: "PERSONAL"},
		{name: "team", visibility: "TEAM"},
		{name: "group", visibility: "GROUP", group: "РИ-230912"},
		{name: "group without group", visibility: "GROUP", wantErr: true},
		{name: "unknown visibility", visibility: "PROJECT", wantErr: true},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()
			err := validateEventData("Защита проекта", "Аудитория 101", startsAt, &endsAt, test.visibility, test.group)
			if (err != nil) != test.wantErr {
				t.Fatalf("validateEventData() error = %v, wantErr %v", err, test.wantErr)
			}
		})
	}
}

func TestValidateReminders(t *testing.T) {
	t.Parallel()
	if err := validateReminders([]int{15, 60, 1440}); err != nil {
		t.Fatalf("validateReminders() error = %v", err)
	}
	if err := validateReminders([]int{60, 60}); err == nil {
		t.Fatal("validateReminders() expected duplicate error")
	}
}
