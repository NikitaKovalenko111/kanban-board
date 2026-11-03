package task_service

import (
	"strings"
	"unicode/utf8"

	servicecommon "kanban-board/internal/services/common"
)

const maxTaskTitleLength = 255

func validateTask(title, priority string, assigneeIDs []string) error {
	if strings.TrimSpace(title) == "" {
		return servicecommon.InvalidInput("title is required")
	}
	if utf8.RuneCountInString(title) > maxTaskTitleLength {
		return servicecommon.InvalidInput("title must not exceed %d characters", maxTaskTitleLength)
	}

	switch priority {
	case "LOW", "MEDIUM", "HIGH", "CRITICAL":
	case "":
		return servicecommon.InvalidInput("priority is required")
	default:
		return servicecommon.InvalidInput("priority must be LOW, MEDIUM, HIGH, or CRITICAL")
	}

	seen := make(map[string]struct{}, len(assigneeIDs))
	for _, assigneeID := range assigneeIDs {
		if err := servicecommon.ValidateUUID(assigneeID); err != nil {
			return err
		}
		if _, exists := seen[assigneeID]; exists {
			return servicecommon.InvalidInput("assigneeIds must not contain duplicates")
		}
		seen[assigneeID] = struct{}{}
	}

	return nil
}

func validateTaskStatus(status string) error {
	switch status {
	case "WAITING", "WORKING", "CHECKING", "DONE":
		return nil
	default:
		return servicecommon.InvalidInput("status must be WAITING, WORKING, CHECKING, or DONE")
	}
}
