package team_member_repo

import (
	"context"
	"database/sql"
	"fmt"
	"kanban-board/internal/models"
)

type TeamMemberRepo struct {
	db  *sql.DB
	ctx context.Context
}

func Init(db *sql.DB, ctx context.Context) *TeamMemberRepo {
	return &TeamMemberRepo{
		db:  db,
		ctx: ctx,
	}
}

func (r *TeamMemberRepo) AddMemberToTeam(teamID string, userID string, role string) error {
	_, err := r.db.ExecContext(r.ctx, `
		INSERT INTO team_member (team_id, user_id, role)
		VALUES ($1, $2, $3)
	`, teamID, userID, role)
	if err != nil {
		return fmt.Errorf("failed to add member to team: %v", err)
	}

	return nil
}

func (r *TeamMemberRepo) GetTeamMembersByTeamID(teamID string) ([]models.TeamMember, error) {
	rows, err := r.db.QueryContext(r.ctx, `
		SELECT team_id, user_id, role, joined_at
		FROM team_member
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

func (r *TeamMemberRepo) UpdateMemberRole(teamID string, userID string, newRole string) error {
	_, err := r.db.ExecContext(r.ctx, `
		UPDATE team_member
		SET role = $1
		WHERE team_id = $2 AND user_id = $3
	`, newRole, teamID, userID)
	if err != nil {
		return fmt.Errorf("failed to update member role: %v", err)
	}

	return nil
}

func (r *TeamMemberRepo) RemoveMemberFromTeam(teamID string, userID string) error {
	_, err := r.db.ExecContext(r.ctx, `
		DELETE FROM team_member
		WHERE team_id = $1 AND user_id = $2
	`, teamID, userID)
	if err != nil {
		return fmt.Errorf("failed to remove member from team: %v", err)
	}

	return nil
}
