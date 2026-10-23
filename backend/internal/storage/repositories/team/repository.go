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
	var id string
	err := r.db.QueryRowContext(r.ctx, `
		INSERT INTO team (id, name)
		VALUES (gen_random_uuid(), $1)
		RETURNING id
	`, name).Scan(&id)
	if err != nil {
		return "", fmt.Errorf("failed to create team: %v", err)
	}

	return id, nil
}

func (r *TeamRepo) GetTeamByID(id string) (*models.Team, error) {
	var team models.Team
	err := r.db.QueryRowContext(r.ctx, `
		SELECT id, name, created_at
		FROM team
		WHERE id = $1
	`, id).Scan(&team.ID, &team.Name, &team.CreatedAt)
	if err != nil {
		return nil, fmt.Errorf("failed to get team: %v", err)
	}
	return &team, nil
}

// func (r *TeamRepo) GetTeamByName(name string) (*models.Team, error) {
// 	var team models.Team
// 	err := r.db.QueryRowContext(r.ctx, `
// 		SELECT id, name, created_at
// 		FROM team
// 		WHERE name = $1
// 	`, name).Scan(&team.ID, &team.Name, &team.CreatedAt)
// 	if err != nil {
// 		return nil, fmt.Errorf("failed to get team: %v", err)
// 	}
// 	return &team, nil
// }

func (r *TeamRepo) GetAllTeams() ([]models.Team, error) {
	rows, err := r.db.QueryContext(r.ctx, `
		SELECT id, name, created_at
		FROM team
	`)
	if err != nil {
		return nil, fmt.Errorf("failed to get teams: %v", err)
	}

	defer rows.Close()

	var teams []models.Team

	for rows.Next() {
		var team models.Team
		if err := rows.Scan(&team.ID, &team.Name, &team.CreatedAt); err != nil {
			return nil, fmt.Errorf("failed to scan team: %v", err)
		}
		teams = append(teams, team)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("failed to iterate teams: %v", err)
	}

	return teams, nil
}

func (r *TeamRepo) UpdateTeam(id string, name string) error {
	_, err := r.db.ExecContext(r.ctx, `
		UPDATE team
		SET name = $1
		WHERE id = $2
	`, name, id)
	if err != nil {
		return fmt.Errorf("failed to update team: %v", err)
	}

	return nil
}

func (r *TeamRepo) DeleteTeam(id string) error {
	_, err := r.db.ExecContext(r.ctx, `
		DELETE FROM team
		WHERE id = $1
	`, id)
	if err != nil {
		return fmt.Errorf("failed to delete team: %v", err)
	}

	return nil
}
