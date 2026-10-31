package servicecommon

import (
	"fmt"

	"github.com/google/uuid"
)

func ValidateUUID(values ...string) error {
	for _, value := range values {
		if _, err := uuid.Parse(value); err != nil {
			return fmt.Errorf("invalid UUID: %w", err)
		}
	}

	return nil
}
