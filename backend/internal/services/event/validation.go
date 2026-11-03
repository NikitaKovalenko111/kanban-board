package event_service

import (
	"strings"
	"time"
	"unicode"

	servicecommon "kanban-board/internal/services/common"
)

const (
	personalScope = "PERSONAL"
	projectScope  = "PROJECT"

	personalVisibility = "PERSONAL"
	projectVisibility  = "PROJECT"
	groupVisibility    = "GROUP"
)

func validateEventType(
	userID string,
	eventTypeID *string,
	title, color, scope string,
	projectID *string,
	reminderMinutes []int,
) error {
	if err := servicecommon.ValidateUUID(userID); err != nil {
		return err
	}
	if eventTypeID != nil {
		if err := servicecommon.ValidateUUID(*eventTypeID); err != nil {
			return err
		}
	}
	if err := validateEventTitle(title); err != nil {
		return err
	}
	if err := servicecommon.ValidateRequired(color, "color"); err != nil {
		return err
	}
	if eventTypeID == nil && scope == "" {
		return servicecommon.InvalidInput("scope is required")
	}
	if scope != "" {
		if scope != personalScope && scope != projectScope {
			return servicecommon.InvalidInput("scope must be PERSONAL or PROJECT")
		}
		if scope == projectScope && projectID == nil {
			return servicecommon.InvalidInput("projectId is required for PROJECT scope")
		}
		if scope == personalScope && projectID != nil {
			return servicecommon.InvalidInput("projectId must be empty for PERSONAL scope")
		}
	}
	if err := servicecommon.ValidateOptionalUUID(projectID); err != nil {
		return err
	}

	return servicecommon.ValidateReminderMinutes(reminderMinutes)
}

func validateEvent(
	userID string,
	eventID *string,
	eventTypeID, title, description, location, visibility string,
	projectID *string,
	studentGroup string,
	startsAt, endsAt time.Time,
	reminderMinutes []int,
) error {
	if err := servicecommon.ValidateUUID(userID); err != nil {
		return err
	}
	if eventID != nil {
		if err := servicecommon.ValidateUUID(*eventID); err != nil {
			return err
		}
	}
	if eventID == nil && eventTypeID == "" {
		return servicecommon.InvalidInput("eventTypeId is required")
	}
	if eventTypeID != "" {
		if err := servicecommon.ValidateUUID(eventTypeID); err != nil {
			return err
		}
	}
	if err := validateEventTitle(title); err != nil {
		return err
	}
	if err := validateText(description, "description"); err != nil {
		return err
	}
	if err := validateText(location, "location"); err != nil {
		return err
	}
	if err := servicecommon.ValidateTimeRange(startsAt, endsAt); err != nil {
		return err
	}
	if err := servicecommon.ValidateReminderMinutes(reminderMinutes); err != nil {
		return err
	}
	if visibility == "" {
		if eventID == nil {
			return servicecommon.InvalidInput("visibility is required")
		}
		return servicecommon.ValidateOptionalUUID(projectID)
	}

	switch visibility {
	case personalVisibility:
		if projectID != nil || strings.TrimSpace(studentGroup) != "" {
			return servicecommon.InvalidInput("projectId and studentGroup must be empty for PERSONAL visibility")
		}
	case projectVisibility:
		if projectID == nil {
			return servicecommon.InvalidInput("projectId is required for PROJECT visibility")
		}
		if strings.TrimSpace(studentGroup) != "" {
			return servicecommon.InvalidInput("studentGroup must be empty for PROJECT visibility")
		}
	case groupVisibility:
		if projectID != nil {
			return servicecommon.InvalidInput("projectId must be empty for GROUP visibility")
		}
		if err := servicecommon.ValidateRequired(studentGroup, "studentGroup"); err != nil {
			return err
		}
	default:
		return servicecommon.InvalidInput("visibility must be PERSONAL, PROJECT, or GROUP")
	}

	return servicecommon.ValidateOptionalUUID(projectID)
}

func validateEventTitle(title string) error {
	if err := servicecommon.ValidateRequired(title, "title"); err != nil {
		return err
	}

	return validateText(title, "title")
}

func validateText(value, field string) error {
	for _, character := range value {
		if unicode.IsControl(character) && character != '\n' && character != '\r' && character != '\t' {
			return servicecommon.InvalidInput("%s must not contain control characters", field)
		}
	}

	return nil
}
