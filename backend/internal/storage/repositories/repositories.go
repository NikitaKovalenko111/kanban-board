package repositories

import (
	"kanban-board/internal/storage"
	attachment_repo "kanban-board/internal/storage/repositories/attachment"
	board_repo "kanban-board/internal/storage/repositories/board"
	comment_repo "kanban-board/internal/storage/repositories/comment"
	event_repo "kanban-board/internal/storage/repositories/event"
	profile_repo "kanban-board/internal/storage/repositories/profile"
	project_repo "kanban-board/internal/storage/repositories/project"
	schedule_repo "kanban-board/internal/storage/repositories/schedule"
	session_repo "kanban-board/internal/storage/repositories/session"
	task_repo "kanban-board/internal/storage/repositories/task"
	users_repo "kanban-board/internal/storage/repositories/user"
)

type Repositories struct {
	Users       *users_repo.UsersRepo
	Sessions    *session_repo.SessionRepo
	Profile     *profile_repo.Repository
	Projects    *project_repo.Repository
	Boards      *board_repo.Repository
	Tasks       *task_repo.Repository
	Comments    *comment_repo.Repository
	Attachments *attachment_repo.Repository
	Schedule    *schedule_repo.Repository
	Events      *event_repo.Repository
}

func Init(appStorage *storage.Storage) *Repositories {
	db := appStorage.Db
	return &Repositories{
		Users:       users_repo.Init(db),
		Sessions:    session_repo.Init(db),
		Profile:     profile_repo.Init(db),
		Projects:    project_repo.Init(db),
		Boards:      board_repo.Init(db),
		Tasks:       task_repo.Init(db),
		Comments:    comment_repo.Init(db),
		Attachments: attachment_repo.Init(db),
		Schedule:    schedule_repo.Init(db),
		Events:      event_repo.Init(db),
	}
}
