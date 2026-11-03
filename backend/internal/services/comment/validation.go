package comment_service

import (
	"strings"
	"unicode/utf8"

	servicecommon "kanban-board/internal/services/common"
)

const maxCommentLength = 10_000

func validateComment(text string) error {
	if strings.TrimSpace(text) == "" {
		return servicecommon.InvalidInput("text is required")
	}
	if utf8.RuneCountInString(text) > maxCommentLength {
		return servicecommon.InvalidInput("text must not exceed %d characters", maxCommentLength)
	}

	return nil
}
