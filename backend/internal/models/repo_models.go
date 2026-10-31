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
	ID           string       `json:"id"`
	Username     string       `json:"username"`
	Email        string       `json:"email"`
	DisplayName  string       `json:"displayName"`
	Position     string       `json:"position"`
	StudentGroup string       `json:"studentGroup,omitempty"`
	ImageURL     string       `json:"imageUrl,omitempty"`
	Active       bool         `json:"active"`
	Memberships  []Membership `json:"memberships"`
}

type Project struct {
	ID          string    `json:"id"`
	CreatedBy   string    `json:"createdByUserId"`
	Name        string    `json:"name"`
	Description string    `json:"description"`
	JoinPolicy  string    `json:"joinPolicy"`
	Archived    bool      `json:"archived"`
	IsMember    bool      `json:"isMember"`
	CreatedAt   time.Time `json:"createdAt"`
	UpdatedAt   time.Time `json:"updatedAt"`
}

type ProjectMember struct {
	UserID      string    `json:"userId"`
	DisplayName string    `json:"displayName"`
	Position    string    `json:"position"`
	Email       string    `json:"contact"`
	Role        string    `json:"role"`
	Status      string    `json:"status"`
	JoinedAt    time.Time `json:"joinedAt"`
}

type BoardColumn struct {
	ID       string `json:"id"`
	Code     string `json:"code"`
	Name     string `json:"name"`
	Position int    `json:"position"`
}

type Task struct {
	ID          string     `json:"id"`
	ProjectID   string     `json:"projectId"`
	ColumnID    string     `json:"boardColumnId"`
	Status      string     `json:"status"`
	CreatedBy   string     `json:"createdByUserId"`
	Title       string     `json:"title"`
	Description string     `json:"description"`
	Priority    string     `json:"priority"`
	Position    int        `json:"position"`
	DueDate     *time.Time `json:"dueDate,omitempty"`
	CompletedAt *time.Time `json:"completedAt,omitempty"`
	AssigneeIDs []string   `json:"assigneeIds"`
	CreatedAt   time.Time  `json:"createdAt"`
	UpdatedAt   time.Time  `json:"updatedAt"`
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
	ActorID    *string   `json:"actorId,omitempty"`
	ActorName  string    `json:"actorName"`
	FromStatus string    `json:"fromStatus,omitempty"`
	ToStatus   string    `json:"toStatus,omitempty"`
	Comment    string    `json:"comment,omitempty"`
	OccurredAt time.Time `json:"occurredAt"`
}

type Comment struct {
	ID         string     `json:"id"`
	TaskID     string     `json:"taskId"`
	AuthorID   string     `json:"authorId"`
	AuthorName string     `json:"authorName"`
	Text       string     `json:"text"`
	CreatedAt  time.Time  `json:"createdAt"`
	UpdatedAt  *time.Time `json:"updatedAt,omitempty"`
}

type Attachment struct {
	ID         string    `json:"id"`
	TaskID     string    `json:"taskId"`
	UploadedBy string    `json:"uploadedByUserId"`
	Type       string    `json:"attachmentType"`
	FileName   string    `json:"fileName"`
	StorageKey string    `json:"-"`
	MimeType   string    `json:"mimeType"`
	FileSize   int64     `json:"fileSize"`
	CreatedAt  time.Time `json:"createdAt"`
}

type Lesson struct {
	LessonID        string     `json:"id"`
	StudentGroupID  string     `json:"studentGroupId"`
	ExternalID      string     `json:"externalId"`
	Title           string     `json:"title"`
	Room            string     `json:"room"`
	Teacher         string     `json:"teacher"`
	LessonKind      string     `json:"lessonKind"`
	StartsAt        time.Time  `json:"startsAt"`
	EndsAt          time.Time  `json:"endsAt"`
	SourceStatus    string     `json:"sourceStatus"`
	SourceUpdatedAt *time.Time `json:"sourceUpdatedAt,omitempty"`
	CreatedAt       time.Time  `json:"createdAt"`
	UpdatedAt       time.Time  `json:"updatedAt"`
}

type EventType struct {
	ID                     string    `json:"id"`
	Code                   string    `json:"code,omitempty"`
	Title                  string    `json:"title"`
	Color                  string    `json:"color"`
	Scope                  string    `json:"scope"`
	CreatedByUserID        *string   `json:"createdByUserId,omitempty"`
	ProjectID              *string   `json:"projectId,omitempty"`
	DefaultReminderMinutes []int     `json:"defaultReminderMinutes"`
	CreatedAt              time.Time `json:"createdAt"`
	UpdatedAt              time.Time `json:"updatedAt"`
}

type Event struct {
	ID              string    `json:"id"`
	OwnerUserID     string    `json:"ownerId"`
	EventTypeID     string    `json:"eventTypeId"`
	ProjectID       *string   `json:"projectId,omitempty"`
	StudentGroupID  *string   `json:"studentGroupId,omitempty"`
	StudentGroup    string    `json:"studentGroup,omitempty"`
	Title           string    `json:"title"`
	Description     string    `json:"description"`
	Location        string    `json:"location"`
	Visibility      string    `json:"visibility"`
	StartsAt        time.Time `json:"startsAt"`
	EndsAt          time.Time `json:"endsAt"`
	ReminderMinutes []int     `json:"reminderMinutes"`
	CreatedAt       time.Time `json:"createdAt"`
	UpdatedAt       time.Time `json:"updatedAt"`
}
