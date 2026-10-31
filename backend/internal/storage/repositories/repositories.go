package repositories

import (
	"kanban-board/internal/storage"
	attachment_repo "kanban-board/internal/storage/repositories/attachment"
	board_repo "kanban-board/internal/storage/repositories/board"
	board_column_repo "kanban-board/internal/storage/repositories/board_column"
	comment_repo "kanban-board/internal/storage/repositories/comment"
	session_repo "kanban-board/internal/storage/repositories/session"
	task_repo "kanban-board/internal/storage/repositories/task"
	task_assignee_repo "kanban-board/internal/storage/repositories/task_assignee"
	users_repo "kanban-board/internal/storage/repositories/user"
	workspace_repo "kanban-board/internal/storage/repositories/workspace"
)

type Repositories struct {
	Users         *users_repo.UsersRepo
	Sessions      *session_repo.SessionRepo
	Boards        *board_repo.BoardRepo
	BoardColumns  *board_column_repo.BoardColumnRepo
	Tasks         *task_repo.TaskRepo
	TaskAssignees *task_assignee_repo.TaskAssigneeRepo
	Comments      *comment_repo.CommentRepo
	Attachments   *attachment_repo.AttachmentRepo
	Workspace     *workspace_repo.Repository
}

func Init(appStorage *storage.Storage) *Repositories {
	return &Repositories{
		Users: users_repo.Init(appStorage.Db), Sessions: session_repo.Init(appStorage.Db),
		Boards: board_repo.Init(appStorage.Db), BoardColumns: board_column_repo.Init(appStorage.Db),
		Tasks: task_repo.Init(appStorage.Db), TaskAssignees: task_assignee_repo.Init(appStorage.Db),
		Comments: comment_repo.Init(appStorage.Db), Attachments: attachment_repo.Init(appStorage.Db),
		Workspace: workspace_repo.Init(appStorage.Db),
	}
}
