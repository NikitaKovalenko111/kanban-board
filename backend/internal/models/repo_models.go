package models

import "time"

type StudentGroup struct {
	StudentGroupID string
	GroupCode      string
	Name           string
	CreatedAt      time.Time
	UpdatedAt      time.Time
}

type User struct {
	UserID         string
	Username       string
	Email          string
	DisplayName    string
	Position       string
	StudentGroupID *string
	GroupName      string
	ImageURL       string
	IsActive       bool
	CreatedAt      time.Time
	UpdatedAt      time.Time
}

type UserCredentials struct {
	UserID       string
	Username     string
	Email        string
	PasswordHash string
	IsActive     bool
}

type Session struct {
	SessionID, UserID, RefreshTokenHash string
	ExpiresAt, CreatedAt                time.Time
	RevokedAt                           *time.Time
}

type Project struct {
	ProjectID, CreatedByUserID, Name, Description, JoinPolicy string
	IsArchived                                                bool
	CreatedAt, UpdatedAt                                      time.Time
}

type ProjectMember struct {
	ProjectID, UserID, Role, Status string
	JoinedAt, UpdatedAt             time.Time
}

type Board struct{ BoardID, ProjectID, Name string }

type BoardColumn struct {
	ColumnID, BoardID, Code, Name string
	Position                      int
}

type Task struct {
	TaskID, ColumnID, CreatedByUserID, Title, Description, Priority string
	Position                                                        int
	DueDate, CompletedAt                                            *time.Time
	CreatedAt, UpdatedAt                                            time.Time
}

type TaskAssignee struct{ TaskID, UserID string }

type TaskHistory struct {
	TaskHistoryID, TaskID                 string
	ActorUserID                           *string
	Action, FromStatus, ToStatus, Comment string
	OccurredAt                            time.Time
}

type Comment struct {
	CommentID, TaskID, AuthorUserID, Content string
	CreatedAt                                time.Time
	UpdatedAt                                *time.Time
}

type Attachment struct {
	AttachmentID, TaskID, UploadedByUserID, AttachmentType string
	FileName, StorageKey, MimeType                         string
	FileSize                                               int64
	CreatedAt                                              time.Time
}

type Lesson struct {
	LessonID, StudentGroupID, ExternalID, Title, Room, Teacher string
	LessonKind, SourceStatus                                   string
	StartsAt, EndsAt, CreatedAt, UpdatedAt                     time.Time
	SourceUpdatedAt                                            *time.Time
}

type EventType struct {
	EventTypeID, Code, Title, Color, Scope string
	CreatedByUserID, ProjectID             *string
	DefaultReminderMinutes                 []int
	CreatedAt, UpdatedAt                   time.Time
}

type Event struct {
	EventID, OwnerUserID, EventTypeID, Title, Description, Location, Visibility string
	ProjectID, StudentGroupID                                                   *string
	ReminderMinutes                                                             []int
	StartsAt, EndsAt, CreatedAt, UpdatedAt                                      time.Time
}
