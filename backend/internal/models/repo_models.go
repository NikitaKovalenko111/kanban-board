package models

import "time"

type User struct {
	UserID    string
	Username  string
	Email     string
	GroupName string
	ImageURL  string
	CreatedAt time.Time
}

type UserCredentials struct {
	UserID       string
	Username     string
	Email        string
	PasswordHash string
}

type Session struct {
	SessionID        string
	UserID           string
	RefreshTokenHash string
	ExpiresAt        time.Time
	CreatedAt        time.Time
	RevokedAt        *time.Time
}

type Team struct {
	TeamID    string
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
	ProjectID   string
	TeamID      string
	Name        string
	Description string
	CreatedAt   time.Time
}

type Board struct {
	BoardID   string
	ProjectID string
	Name      string
}

type BoardColumn struct {
	ColumnID string
	BoardID  string
	Name     string
	Position int
}

type Task struct {
	TaskID          string
	ColumnID        string
	CreatedByUserID string
	Title           string
	Description     string
	Priority        string
	Position        int
	DueDate         *time.Time
	CreatedAt       time.Time
	UpdatedAt       time.Time
}

type TaskAssignee struct {
	TaskID string
	UserID string
}

type Comment struct {
	CommentID    string
	TaskID       string
	AuthorUserID string
	Content      string
	CreatedAt    time.Time
}

type Attachment struct {
	AttachmentID     string
	TaskID           string
	UploadedByUserID string
	FileName         string
	StorageKey       string
	MimeType         string
	FileSize         int64
	CreatedAt        time.Time
}
