package team_repo

import (
	"context"
	"database/sql"
	"fmt"
	"kanban-board/internal/models"
)

type TeamRepo struct {
	db  *sql.DB
	ctx context.Context
}

func Init(sql *sql.DB, ctx context.Context) *TeamRepo {
	return &TeamRepo{
		db:  sql,
		ctx: ctx,
	}
}

func (r *TeamRepo) CreateTeam(name string) (string, error) {
	var teamID string
	err := r.db.QueryRowContext(r.ctx, `
		INSERT INTO teams (team_id, name)
		VALUES (gen_random_uuid(), $1)
		RETURNING team_id
	`, name).Scan(&teamID)
	if err != nil {
		return "", fmt.Errorf("failed to create team: %v", err)
	}

	return teamID, nil
}

func (r *TeamRepo) GetTeamByID(teamID string) (*models.Team, error) {
	var team models.Team
	err := r.db.QueryRowContext(r.ctx, `
		SELECT team_id, name, created_at
		FROM teams
		WHERE team_id = $1
	`, teamID).Scan(&team.TeamID, &team.Name, &team.CreatedAt)
	if err != nil {
		return nil, fmt.Errorf("failed to get team: %v", err)
	}
	return &team, nil
}

// func (r *TeamRepo) GetTeamByName(name string) (*models.Team, error) {
// 	var team models.Team
// 	err := r.db.QueryRowContext(r.ctx, `
// 		SELECT team_id, name, created_at
// 		FROM teams
// 		WHERE name = $1
// 	`, name).Scan(&team.TeamID, &team.Name, &team.CreatedAt)
// 	if err != nil {
// 		return nil, fmt.Errorf("failed to get team: %v", err)
// 	}
// 	return &team, nil
// }

func (r *TeamRepo) GetAllTeams() ([]models.Team, error) {
	rows, err := r.db.QueryContext(r.ctx, `
		SELECT team_id, name, created_at
		FROM teams
	`)
	if err != nil {
		return nil, fmt.Errorf("failed to get teams: %v", err)
	}

	defer rows.Close()

	var teams []models.Team

	for rows.Next() {
		var team models.Team
		if err := rows.Scan(&team.TeamID, &team.Name, &team.CreatedAt); err != nil {
			return nil, fmt.Errorf("failed to scan team: %v", err)
		}
		teams = append(teams, team)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("failed to iterate teams: %v", err)
	}

	return teams, nil
}

func (r *TeamRepo) UpdateTeam(teamID string, name string) error {
	_, err := r.db.ExecContext(r.ctx, `
		UPDATE teams
		SET name = $1
		WHERE team_id = $2
	`, name, teamID)
	if err != nil {
		return fmt.Errorf("failed to update team: %v", err)
	}

	return nil
}

func (r *TeamRepo) DeleteTeam(teamID string) error {
	_, err := r.db.ExecContext(r.ctx, `
		DELETE FROM teams
		WHERE team_id = $1
	`, teamID)
	if err != nil {
		return fmt.Errorf("failed to delete team: %v", err)
	}

	return nil
}
