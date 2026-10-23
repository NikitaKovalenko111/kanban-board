package storage

import (
	"database/sql"
	"fmt"
	"kanban-board/internal/config"
	"kanban-board/internal/models"

	_ "github.com/jackc/pgx/v5/stdlib"
)

type Storage struct {
	Db *sql.DB
}

func Connect(cfg config.Postgres) (*Storage, error) {
	dsn := fmt.Sprintf(
		"host=%s port=%d user=%s password=%s dbname=%s sslmode=%s",
		cfg.DbHost,
		cfg.DbPort,
		cfg.DbUser,
		cfg.DbPass,
		cfg.DbName,
		cfg.SSLMode,
	)

	db, err := sql.Open("pgx", dsn)
	if err != nil {
		return nil, fmt.Errorf("open postgres: %w", err)
	}

	if err := db.Ping(); err != nil {
		db.Close()
		return nil, fmt.Errorf("ping postgres: %w", err)
	}

	storage := &Storage{
		Db: db,
	}

	if err := storage.CreateTables(); err != nil {
		db.Close()
		return nil, fmt.Errorf("initialize tables: %w", err)
	}

	if err := storage.CreateIndexes(); err != nil {
		db.Close()
		return nil, fmt.Errorf("initialize indexes: %w", err)
	}

	return storage, nil
}

func (s *Storage) CreateTables() error {
	tables := []struct {
		name  string
		query string
	}{
		{name: "user", query: models.USER_TABLE},
		{name: "team", query: models.TEAM_TABLE},
		{name: "team_member", query: models.TEAM_MEMBER_TABLE},
		{name: "project", query: models.PROJECT_TABLE},
		{name: "board", query: models.BOARD_TABLE},
		{name: "board_column", query: models.BOARD_COLUMN_TABLE},
		{name: "task", query: models.TASK_TABLE},
		{name: "task_assignee", query: models.TASK_ASSIGNEE_TABLE},
		{name: "comment", query: models.COMMENT_TABLE},
		{name: "attachment", query: models.ATTACHMENT_TABLE},
	}

	tx, err := s.Db.Begin()
	if err != nil {
		return fmt.Errorf("begin create tables transaction: %w", err)
	}
	defer tx.Rollback()

	for _, table := range tables {
		if _, err := tx.Exec(table.query); err != nil {
			return fmt.Errorf("create %s table: %w", table.name, err)
		}
	}

	if err := tx.Commit(); err != nil {
		return fmt.Errorf("commit create tables transaction: %w", err)
	}

	return nil
}

func (s *Storage) CreateIndexes() error {
	indexes := []struct {
		name  string
		query string
	}{
		{name: "user_username_idx", query: models.USER_USERNAME_INDEX},
		{name: "user_email_idx", query: models.USER_EMAIL_INDEX},
		{name: "team_member_user_id_idx", query: models.TEAM_MEMBER_USER_ID_INDEX},
		{name: "project_team_id_idx", query: models.PROJECT_TEAM_ID_INDEX},
		{name: "board_project_id_idx", query: models.BOARD_PROJECT_ID_INDEX},
		{name: "board_column_board_position_idx", query: models.BOARD_COLUMN_BOARD_POSITION_INDEX},
		{name: "task_column_position_idx", query: models.TASK_COLUMN_POSITION_INDEX},
		{name: "task_created_by_idx", query: models.TASK_CREATED_BY_INDEX},
		{name: "task_assignee_user_id_idx", query: models.TASK_ASSIGNEE_USER_ID_INDEX},
		{name: "comment_task_created_at_idx", query: models.COMMENT_TASK_CREATED_AT_INDEX},
		{name: "comment_author_id_idx", query: models.COMMENT_AUTHOR_ID_INDEX},
		{name: "attachment_task_created_at_idx", query: models.ATTACHMENT_TASK_CREATED_AT_INDEX},
		{name: "attachment_uploaded_by_idx", query: models.ATTACHMENT_UPLOADED_BY_INDEX},
		{name: "attachment_storage_key_idx", query: models.ATTACHMENT_STORAGE_KEY_INDEX},
	}

	tx, err := s.Db.Begin()
	if err != nil {
		return fmt.Errorf("begin create indexes transaction: %w", err)
	}
	defer tx.Rollback()

	for _, index := range indexes {
		if _, err := tx.Exec(index.query); err != nil {
			return fmt.Errorf("create %s index: %w", index.name, err)
		}
	}

	if err := tx.Commit(); err != nil {
		return fmt.Errorf("commit create indexes transaction: %w", err)
	}

	return nil
}
