package auth_service

import (
	"errors"
	"net/mail"
	"regexp"
	"strings"
	"unicode"
	"unicode/utf8"
)

// TODO: to 2 groups: ТОП-203Б, ТОП-204Б (after the sprint)
const (
	minUsernameLength  = 3
	maxUsernameLength  = 32
	minPasswordLength  = 8
	maxPasswordLength  = 72
	maxEmailLength     = 254
	minGroupNameLength = 2
	maxGroupNameLength = 100
)

var usernamePattern = regexp.MustCompile(`^[a-z0-9._-]+$`)

func ValidateRegisterRequest(username, email, password, groupName string) error {
	if err := validateUsername(username); err != nil {
		return err
	}
	if err := validateEmail(email); err != nil {
		return err
	}
	if err := validatePassword(password); err != nil {
		return err
	}
	if err := validateGroupName(groupName); err != nil {
		return err
	}

	return nil
}

func validateUsername(username string) error {
	if username == "" {
		return errors.New("username is required")
	}
	if username != strings.TrimSpace(username) {
		return errors.New("username must not contain leading or trailing spaces")
	}

	length := utf8.RuneCountInString(username)
	if length < minUsernameLength || length > maxUsernameLength {
		return errors.New("username must be between 3 and 32 characters")
	}
	if !usernamePattern.MatchString(username) {
		return errors.New("username may contain only lowercase Latin letters, digits, dots, underscores, and hyphens")
	}

	return nil
}

func validateEmail(email string) error {
	if email == "" {
		return errors.New("email is required")
	}
	if email != strings.TrimSpace(email) {
		return errors.New("email must not contain leading or trailing spaces")
	}
	if len(email) > maxEmailLength {
		return errors.New("email is too long")
	}

	address, err := mail.ParseAddress(email)
	if err != nil || address.Address != email {
		return errors.New("email has an invalid format")
	}

	return nil
}

func validatePassword(password string) error {
	if password == "" {
		return errors.New("password is required")
	}
	if len(password) < minPasswordLength || len(password) > maxPasswordLength {
		return errors.New("password must be between 8 and 72 characters")
	}

	var hasLetter, hasDigit bool
	for _, character := range password {
		if unicode.IsSpace(character) || unicode.IsControl(character) {
			return errors.New("password must not contain spaces or control characters")
		}
		hasLetter = hasLetter || unicode.IsLetter(character)
		hasDigit = hasDigit || unicode.IsDigit(character)
	}
	if !hasLetter || !hasDigit {
		return errors.New("password must contain at least one letter and one digit")
	}

	return nil
}

func validateGroupName(groupName string) error {
	if groupName == "" {
		return errors.New("group name is required")
	}
	if groupName != strings.TrimSpace(groupName) {
		return errors.New("group name must not contain leading or trailing spaces")
	}

	length := utf8.RuneCountInString(groupName)
	if length < minGroupNameLength || length > maxGroupNameLength {
		return errors.New("group name must be between 2 and 100 characters")
	}
	for _, character := range groupName {
		if unicode.IsControl(character) {
			return errors.New("group name must not contain control characters")
		}
	}

	return nil
}
