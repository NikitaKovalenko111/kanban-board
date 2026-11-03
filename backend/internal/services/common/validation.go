package servicecommon

import (
	"strings"
	"time"

	"github.com/google/uuid"
)

func ValidateUUID(values ...string) error {
	for _, value := range values {
		if _, err := uuid.Parse(value); err != nil {
			return InvalidInput("invalid UUID")
		}
	}

	return nil
}

func ValidateOptionalUUID(value *string) error {
	if value == nil {
		return nil
	}

	return ValidateUUID(*value)
}

func ValidateRequired(value, field string) error {
	if strings.TrimSpace(value) == "" {
		return InvalidInput("%s is required", field)
	}

	return nil
}

func ValidateTimeRange(startsAt, endsAt time.Time) error {
	if startsAt.IsZero() {
		return InvalidInput("startsAt is required")
	}
	if endsAt.IsZero() {
		return InvalidInput("endsAt is required")
	}
	if !endsAt.After(startsAt) {
		return InvalidInput("endsAt must be after startsAt")
	}

	return nil
}

func ValidateReminderMinutes(minutes []int) error {
	seen := make(map[int]struct{}, len(minutes))
	for _, minute := range minutes {
		if minute < 0 {
			return InvalidInput("reminder minutes must not be negative")
		}
		if _, exists := seen[minute]; exists {
			return InvalidInput("reminder minutes must not contain duplicates")
		}
		seen[minute] = struct{}{}
	}

	return nil
}
