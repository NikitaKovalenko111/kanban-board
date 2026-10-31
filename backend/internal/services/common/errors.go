package servicecommon

import repositorycommon "kanban-board/internal/storage/repositories/common"

var (
	ErrNotFound  = repositorycommon.ErrNotFound
	ErrForbidden = repositorycommon.ErrForbidden
	ErrConflict  = repositorycommon.ErrConflict
)
