package models

import "time"

type User struct {
	ID        string
	UserName  string
	Email     string
	Group     string
	ImageURL  string
	CreatedAt time.Time
}

type Team struct {
	ID        string
	Name      string
	CreatedAt time.Time
}

type TeamMember struct {
	TeamID   string
	UserID   string
	Role     string
	JoinedAt time.Time
}

type Project struct {
	ID          string
	TeamID      string
	Name        string
	Description string
	CreatedAt   time.Time
}

type Board struct {
	ID        string
	ProjectID string
	Name      string
}

type BoardColumn struct {
	ID       string
	BoardID  string
	Name     string
	Position int
}

type Task struct {
	ID          string
	ColumnID    string
	CreatedBy   string
	Title       string
	Description string
	Priority    string
	Position    int
	DueDate     *time.Time
	CreatedAt   time.Time
	UpdatedAt   time.Time
}

type TaskAssignee struct {
	TaskID string
	UserID string
}

type Comment struct {
	ID        string
	TaskID    string
	AuthorID  string
	Content   string
	CreatedAt time.Time
}

type Attachment struct {
	ID         string
	TaskID     string
	UploadedBy string
	FileName   string
	StorageKey string
	MimeType   string
	FileSize   int64
	CreatedAt  time.Time
}
