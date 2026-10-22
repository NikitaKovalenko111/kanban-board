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
