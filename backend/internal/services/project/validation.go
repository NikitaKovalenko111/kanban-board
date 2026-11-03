package project_service

import servicecommon "kanban-board/internal/services/common"

func validateProject(name, joinPolicy string) error {
	if err := servicecommon.ValidateRequired(name, "name"); err != nil {
		return err
	}

	switch joinPolicy {
	case "OPEN", "INVITE_ONLY", "CLOSED":
		return nil
	default:
		return servicecommon.InvalidInput("joinPolicy must be OPEN, INVITE_ONLY, or CLOSED")
	}
}

func validateMember(role, status string, validateStatus bool) error {
	if role != "LEAD" && role != "MEMBER" {
		return servicecommon.InvalidInput("role must be LEAD or MEMBER")
	}
	if validateStatus && status != "ACTIVE" && status != "BLOCKED" && status != "LEFT" {
		return servicecommon.InvalidInput("status must be ACTIVE, BLOCKED, or LEFT")
	}

	return nil
}
