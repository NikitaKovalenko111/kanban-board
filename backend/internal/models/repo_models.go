package models

import "time"

type StudentGroup struct {
	StudentGroupID string    `json:"id"`
	GroupCode      string    `json:"groupCode"`
	Name           string    `json:"name"`
	CreatedAt      time.Time `json:"createdAt"`
	UpdatedAt      time.Time `json:"updatedAt"`
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

type Membership struct {
	ProjectID string    `json:"projectId"`
	UserID    string    `json:"userId"`
	Role      string    `json:"role"`
	Status    string    `json:"status"`
	JoinedAt  time.Time `json:"joinedAt"`
}

type Profile struct {
	ID              string    `json:"id"`
	FullName        string    `json:"fullName"`
	Username        string    `json:"username"`
	StudentGroup    string    `json:"studentGroup"`
	ImageURL        *string   `json:"imageUrl"`
	Role            string    `json:"role"`
	SchedulePrivacy string    `json:"schedulePrivacy"`
	JoinedAt        time.Time `json:"joinedAt"`
	Active          bool      `json:"active"`
	Contact         *string   `json:"contact,omitempty"`
}

type Project struct {
	ID           string          `json:"id"`
	Name         string          `json:"name"`
	Color        string          `json:"color"`
	Description  string          `json:"description,omitempty"`
	CanViewBoard bool            `json:"canViewBoard"`
	Members      []ProjectMember `json:"members,omitempty"`
	Sources      []ProjectSource `json:"sources,omitempty"`
}

type ProjectMember struct {
	UserID string `json:"userId"`
	Role   string `json:"role"`
}

type FileInfo struct {
	ID          string `json:"id"`
	Name        string `json:"name"`
	SizeBytes   int64  `json:"sizeBytes"`
	MimeType    string `json:"mimeType"`
	DownloadURL string `json:"downloadUrl"`
}

type ProjectSource struct {
	ID      string    `json:"id"`
	Kind    string    `json:"kind"`
	Title   string    `json:"title"`
	URL     *string   `json:"url"`
	File    *FileInfo `json:"file"`
	AddedBy *string   `json:"addedBy"`
	AddedAt time.Time `json:"addedAt"`
}

type BoardColumn struct {
	ID        string `json:"id"`
	ProjectID string `json:"projectId"`
	Title     string `json:"title"`
	Position  int    `json:"position"`
	Kind      string `json:"kind"`
	Color     string `json:"color"`
}

type Task struct {
	ID          string        `json:"id"`
	ProjectID   string        `json:"projectId"`
	ColumnID    string        `json:"columnId"`
	Position    int           `json:"position"`
	Title       string        `json:"title"`
	Description string        `json:"description"`
	Priority    string        `json:"priority"`
	AssigneeID  *string       `json:"assigneeId"`
	CreatedBy   string        `json:"createdBy"`
	CreatedAt   time.Time     `json:"createdAt"`
	DueAt       *time.Time    `json:"dueAt"`
	CompletedAt *time.Time    `json:"completedAt"`
	Attachments []Attachment  `json:"attachments,omitempty"`
	Comments    []Comment     `json:"comments,omitempty"`
	History     []TaskHistory `json:"history,omitempty"`
}

type Board struct {
	ID        string        `json:"boardId"`
	ProjectID string        `json:"projectId"`
	Name      string        `json:"name"`
	Columns   []BoardColumn `json:"columns"`
	Tasks     []Task        `json:"tasks"`
}

type TaskHistory struct {
	ID         string    `json:"id"`
	Action     string    `json:"action"`
	ActorID    *string   `json:"actorId"`
	At         time.Time `json:"at"`
	FromColumn string    `json:"fromColumn,omitempty"`
	ToColumn   string    `json:"toColumn,omitempty"`
	Comment    string    `json:"comment,omitempty"`
}

type Comment struct {
	ID         string     `json:"id"`
	TaskID     string     `json:"-"`
	AuthorID   string     `json:"authorId"`
	AuthorName string     `json:"-"`
	Text       string     `json:"text"`
	CreatedAt  time.Time  `json:"createdAt"`
	UpdatedAt  *time.Time `json:"-"`
}

type Attachment struct {
	ID          string    `json:"id"`
	Kind        string    `json:"kind"`
	ContentKind string    `json:"contentKind"`
	Title       string    `json:"title"`
	URL         *string   `json:"url,omitempty"`
	UploadedBy  string    `json:"uploadedBy"`
	UploadedAt  time.Time `json:"uploadedAt"`
	File        *FileInfo `json:"file,omitempty"`
	TaskID      string    `json:"-"`
	StorageKey  string    `json:"-"`
}

type Lesson struct {
	ID         string  `json:"id"`
	ExternalID *string `json:"externalId,omitempty"`
	Group      string  `json:"group"`
	Date       string  `json:"date"`
	Start      string  `json:"start"`
	End        string  `json:"end"`
	Title      string  `json:"title"`
	Room       string  `json:"room"`
	Teacher    string  `json:"teacher"`
	Kind       string  `json:"kind"`
}

type Schedule struct {
	Lessons   []Lesson   `json:"lessons"`
	UpdatedAt *time.Time `json:"updatedAt"`
	Source    string     `json:"source"`
}

type EventType struct {
	ID               string  `json:"id"`
	Title            string  `json:"title"`
	Color            string  `json:"color"`
	OwnerID          *string `json:"ownerId"`
	DefaultReminders []int   `json:"defaultReminders"`
}

type Event struct {
	ID          string     `json:"id"`
	OwnerID     string     `json:"ownerId"`
	ProjectID   *string    `json:"projectId,omitempty"`
	Title       string     `json:"title"`
	Description string     `json:"description,omitempty"`
	Location    string     `json:"location,omitempty"`
	StartsAt    time.Time  `json:"startsAt"`
	EndsAt      *time.Time `json:"endsAt"`
	Visibility  string     `json:"visibility"`
	Group       *string    `json:"group"`
	TypeID      *string    `json:"typeId,omitempty"`
	Masked      bool       `json:"masked"`
	Partial     bool       `json:"partial"`
}

type Notification struct {
	ID       string    `json:"id"`
	Kind     string    `json:"kind"`
	Title    string    `json:"title"`
	Text     string    `json:"text"`
	At       time.Time `json:"at"`
	Read     bool      `json:"read"`
	TaskID   *string   `json:"taskId,omitempty"`
	EventID  *string   `json:"eventId,omitempty"`
	LessonID *string   `json:"lessonId,omitempty"`
}
