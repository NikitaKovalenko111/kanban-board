package schedule_service

import (
	"time"

	servicecommon "kanban-board/internal/services/common"
)

func validateLessonQuery(group string, from, to time.Time) error {
	if err := servicecommon.ValidateRequired(group, "group"); err != nil {
		return err
	}

	return servicecommon.ValidateTimeRange(from, to)
}
