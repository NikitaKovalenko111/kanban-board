package board_service

import (
	"regexp"
	"strings"
	"unicode/utf8"

	servicecommon "kanban-board/internal/services/common"
)

var columnCodePattern = regexp.MustCompile(`^[A-Z0-9_]+$`)

const (
	minNameLength = 3
	maxNameLength = 32

	minPosition = 0
	maxPosition = 10

	minCodeLength = 3
	maxCodeLength = 32
)

func ValidateColumn(name string, position int, code, userID, resourceID string) error {
	if err := validateName(name); err != nil {
		return err
	}

	if err := validatePosition(position); err != nil {
		return err
	}

	if err := validateCode(code); err != nil {
		return err
	}

	if err := servicecommon.ValidateUUID(userID, resourceID); err != nil {
		return err
	}

	return nil
}

func validateName(name string) error {
	if strings.TrimSpace(name) == "" {
		return servicecommon.InvalidInput("name is required")
	}

	length := utf8.RuneCountInString(name)
	if length < minNameLength || length > maxNameLength {
		return servicecommon.InvalidInput("name must be between 3 and 32 characters")
	}

	return nil
}

func validatePosition(position int) error {
	if position < minPosition || position > maxPosition {
		return servicecommon.InvalidInput("position must be between 0 and 10")
	}

	return nil
}

func validateCode(code string) error {
	if code == "" {
		return servicecommon.InvalidInput("code is required")
	}
	if !columnCodePattern.MatchString(code) {
		return servicecommon.InvalidInput("code may contain only uppercase Latin letters, digits, and underscores")
	}

	length := utf8.RuneCountInString(code)
	if length < minCodeLength || length > maxCodeLength {
		return servicecommon.InvalidInput("code must be between 3 and 32 characters")
	}

	return nil
}
