package servicecommon

import (
	"errors"
	"testing"
	"time"
)

func TestValidateUUID(t *testing.T) {
	t.Parallel()

	if err := ValidateUUID("550e8400-e29b-41d4-a716-446655440000"); err != nil {
		t.Fatalf("ValidateUUID() error = %v", err)
	}

	err := ValidateUUID("not-a-uuid")
	if !errors.Is(err, ErrInvalidInput) {
		t.Fatalf("ValidateUUID() error = %v, want ErrInvalidInput", err)
	}
}

func TestValidateReminderMinutes(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name    string
		minutes []int
		wantErr bool
	}{
		{name: "valid", minutes: []int{1_440, 60}},
		{name: "negative", minutes: []int{-1}, wantErr: true},
		{name: "duplicate", minutes: []int{60, 60}, wantErr: true},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			t.Parallel()

			err := ValidateReminderMinutes(test.minutes)
			if (err != nil) != test.wantErr {
				t.Fatalf("ValidateReminderMinutes() error = %v, wantErr %v", err, test.wantErr)
			}
		})
	}
}

func TestValidateTimeRange(t *testing.T) {
	t.Parallel()

	startsAt := time.Date(2026, time.November, 3, 10, 0, 0, 0, time.UTC)
	if err := ValidateTimeRange(startsAt, startsAt.Add(time.Hour)); err != nil {
		t.Fatalf("ValidateTimeRange() error = %v", err)
	}
	if err := ValidateTimeRange(startsAt, startsAt); !errors.Is(err, ErrInvalidInput) {
		t.Fatalf("ValidateTimeRange() error = %v, want ErrInvalidInput", err)
	}
}
