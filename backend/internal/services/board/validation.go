package board_service

import (
	"errors"
	servicecommon "kanban-board/internal/services/common"
	"strings"
	"unicode/utf8"
)

const (
	minNameLength = 3
	maxNameLength = 32

	minPosition = 0
	maxPosition = 10

	minCodeLength = 3
	maxCodeLength = 32
)

func VallidateBoard(name string, position int, code string, user_id, project_id string) error {
	if err := validateName(name); err != nil {
		return err
	}

	if err := validatePosition(position); err != nil {
		return err
	}

	if err := validateCode(code); err != nil {
		return err
	}

	if err := servicecommon.ValidateUUID(user_id, project_id); err != nil {
		return err
	}

	return nil
}

func validateName(name string) error {
	if name == "" {
		return errors.New("name is required")
	}

	length := utf8.RuneCountInString(name)
	if length < minNameLength || length > maxNameLength {
		return errors.New("name must be between 3 and 32 characters")
	}

	return nil
}

func validatePosition(position int) error {
	if position < minPosition || position > maxPosition {
		return errors.New("position must be between 3 and 32")
	}

	return nil
}

func validateCode(code string) error {
	if code != strings.ToUpper(code) {
		return errors.New("code must be in upper letters")
	}

	length := utf8.RuneCountInString(code)
	if length < minNameLength || length > maxNameLength {
		return errors.New("code must be between 3 and 32 characters")
	}

	return nil
}
