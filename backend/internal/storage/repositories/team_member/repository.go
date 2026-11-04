package team_member_repo

import (
	"context"
	"database/sql"
	"fmt"
	"kanban-board/internal/models"
)

type TeamMemberRepo struct {
	db *sql.DB
}

func Init(db *sql.DB) *TeamMemberRepo {
	return &TeamMemberRepo{
		db: db,
	}
}

func (r *TeamMemberRepo) AddMemberToTeam(ctx context.Context, teamID string, userID string, role string) error {
	_, err := r.db.ExecContext(ctx, `
		INSERT INTO team_members (team_id, user_id, role)
		VALUES ($1, $2, $3)
	`, teamID, userID, role)
	if err != nil {
		return fmt.Errorf("failed to add member to team: %v", err)
	}

	return nil
}

func (r *TeamMemberRepo) GetTeamMembersByTeamID(ctx context.Context, teamID string) ([]models.TeamMember, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT team_id, user_id, role, joined_at
		FROM team_members
		WHERE team_id = $1
	`, teamID)
	if err != nil {
		return nil, fmt.Errorf("failed to get team members: %v", err)
	}

	defer rows.Close()

	var members []models.TeamMember
	for rows.Next() {
		var member models.TeamMember
		if err := rows.Scan(&member.TeamID, &member.UserID, &member.Role, &member.JoinedAt); err != nil {
			return nil, fmt.Errorf("failed to scan team member: %v", err)
		}
		members = append(members, member)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("failed to iterate team members: %v", err)
	}

	return members, nil
}

func (r *TeamMemberRepo) UpdateMemberRole(ctx context.Context, teamID string, userID string, newRole string) error {
	_, err := r.db.ExecContext(ctx, `
		UPDATE team_members
		SET role = $1
		WHERE team_id = $2 AND user_id = $3
	`, newRole, teamID, userID)
	if err != nil {
		return fmt.Errorf("failed to update member role: %v", err)
	}

	return nil
}

func (r *TeamMemberRepo) RemoveMemberFromTeam(ctx context.Context, teamID string, userID string) error {
	_, err := r.db.ExecContext(ctx, `
		DELETE FROM team_members
		WHERE team_id = $1 AND user_id = $2
	`, teamID, userID)
	if err != nil {
		return fmt.Errorf("failed to remove member from team: %v", err)
	}

	return nil
}
