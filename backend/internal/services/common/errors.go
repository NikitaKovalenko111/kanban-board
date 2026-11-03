package servicecommon

import (
	"errors"
	"fmt"

	repositorycommon "kanban-board/internal/storage/repositories/common"
)

var (
	ErrInvalidInput = errors.New("invalid input")
	ErrNotFound     = repositorycommon.ErrNotFound
	ErrForbidden    = repositorycommon.ErrForbidden
	ErrConflict     = repositorycommon.ErrConflict
)

func InvalidInput(format string, arguments ...any) error {
	return fmt.Errorf("%w: %s", ErrInvalidInput, fmt.Sprintf(format, arguments...))
}
