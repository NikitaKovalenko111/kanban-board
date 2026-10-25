package repositories

import (
	"kanban-board/internal/storage"
	attachment_repo "kanban-board/internal/storage/repositories/attachment"
	board_repo "kanban-board/internal/storage/repositories/board"
	board_column_repo "kanban-board/internal/storage/repositories/board_column"
	comment_repo "kanban-board/internal/storage/repositories/comment"
	project_repo "kanban-board/internal/storage/repositories/project"
	session_repo "kanban-board/internal/storage/repositories/session"
	task_repo "kanban-board/internal/storage/repositories/task"
	task_assignee_repo "kanban-board/internal/storage/repositories/task_assignee"
	team_repo "kanban-board/internal/storage/repositories/team"
	team_member_repo "kanban-board/internal/storage/repositories/team_member"
	users_repository "kanban-board/internal/storage/repositories/user"
)

type Repositories struct {
	UsersRepo        *users_repository.UsersRepo
	TeamRepo         *team_repo.TeamRepo
	TeamMemberRepo   *team_member_repo.TeamMemberRepo
	ProjectRepo      *project_repo.ProjectRepo
	BoardRepo        *board_repo.BoardRepo
	BoardColumnRepo  *board_column_repo.BoardColumnRepo
	TaskRepo         *task_repo.TaskRepo
	SessionRepo      *session_repo.SessionRepo
	TaskAssigneeRepo *task_assignee_repo.TaskAssigneeRepo
	CommentRepo      *comment_repo.CommentRepo
	AttachmentRepo   *attachment_repo.AttachmentRepo
}

func Init(storage *storage.Storage) *Repositories {
	return &Repositories{
		UsersRepo:        users_repository.Init(storage.Db),
		TeamRepo:         team_repo.Init(storage.Db),
		TeamMemberRepo:   team_member_repo.Init(storage.Db),
		ProjectRepo:      project_repo.Init(storage.Db),
		BoardRepo:        board_repo.Init(storage.Db),
		BoardColumnRepo:  board_column_repo.Init(storage.Db),
		TaskRepo:         task_repo.Init(storage.Db),
		SessionRepo:      session_repo.Init(storage.Db),
		TaskAssigneeRepo: task_assignee_repo.Init(storage.Db),
		CommentRepo:      comment_repo.Init(storage.Db),
		AttachmentRepo:   attachment_repo.Init(storage.Db),
	}
}
