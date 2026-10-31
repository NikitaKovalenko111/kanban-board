package servicecommon

import workspace_repo "kanban-board/internal/storage/repositories/workspace"

var (
	ErrNotFound  = workspace_repo.ErrNotFound
	ErrForbidden = workspace_repo.ErrForbidden
	ErrConflict  = workspace_repo.ErrConflict
)
