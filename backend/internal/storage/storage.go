package storage

import (
	"database/sql"
	"fmt"
	"kanban-board/internal/config"
	"kanban-board/internal/models"

	_ "github.com/jackc/pgx/v5/stdlib"
)

type Storage struct{ Db *sql.DB }

func Connect(cfg config.Postgres) (*Storage, error) {
	dsn := fmt.Sprintf("host=%s port=%d user=%s password=%s dbname=%s sslmode=%s", cfg.DbHost, cfg.DbPort, cfg.DbUser, cfg.DbPass, cfg.DbName, cfg.SSLMode)
	db, err := sql.Open("pgx", dsn)
	if err != nil {
		return nil, fmt.Errorf("open postgres: %w", err)
	}
	if err := db.Ping(); err != nil {
		db.Close()
		return nil, fmt.Errorf("ping postgres: %w", err)
	}
	appStorage := &Storage{Db: db}
	if err := appStorage.CreateTables(); err != nil {
		db.Close()
		return nil, fmt.Errorf("initialize tables: %w", err)
	}
	if err := appStorage.ApplySchemaMigration(); err != nil {
		db.Close()
		return nil, fmt.Errorf("migrate tables: %w", err)
	}
	if err := appStorage.CreateIndexes(); err != nil {
		db.Close()
		return nil, fmt.Errorf("initialize indexes: %w", err)
	}
	return appStorage, nil
}

func (s *Storage) CreateTables() error {
	tables := []struct{ name, query string }{
		{"student_groups", models.STUDENT_GROUPS_TABLE}, {"users", models.USERS_TABLE}, {"sessions", models.SESSIONS_TABLE},
		{"projects", models.PROJECTS_TABLE}, {"project_members", models.PROJECT_MEMBERS_TABLE}, {"boards", models.BOARDS_TABLE},
		{"board_columns", models.BOARD_COLUMNS_TABLE}, {"tasks", models.TASKS_TABLE}, {"task_assignees", models.TASK_ASSIGNEES_TABLE},
		{"task_history", models.TASK_HISTORY_TABLE}, {"comments", models.COMMENTS_TABLE}, {"attachments", models.ATTACHMENTS_TABLE},
		{"lessons", models.LESSONS_TABLE}, {"event_types", models.EVENT_TYPES_TABLE}, {"event_type_reminders", models.EVENT_TYPE_REMINDERS_TABLE},
		{"events", models.EVENTS_TABLE}, {"event_reminders", models.EVENT_REMINDERS_TABLE},
	}
	return s.runStatements("create", tables)
}

func (s *Storage) ApplySchemaMigration() error {
	if _, err := s.Db.Exec(models.SCHEMA_MIGRATION); err != nil {
		return fmt.Errorf("apply schema migration: %w", err)
	}
	return nil
}

func (s *Storage) CreateIndexes() error {
	indexes := []struct{ name, query string }{
		{"users_username_idx", models.USERS_USERNAME_INDEX}, {"users_email_idx", models.USERS_EMAIL_INDEX},
		{"users_student_group_id_idx", models.USERS_STUDENT_GROUP_INDEX}, {"sessions_refresh_token_hash_idx", models.SESSIONS_REFRESH_TOKEN_HASH_INDEX},
		{"sessions_user_id_idx", models.SESSIONS_USER_ID_INDEX}, {"sessions_expires_at_idx", models.SESSIONS_EXPIRES_AT_INDEX},
		{"sessions_revoked_at_idx", models.SESSIONS_REVOKED_AT_INDEX}, {"projects_created_by_user_id_idx", models.PROJECTS_CREATED_BY_INDEX},
		{"project_members_user_id_idx", models.PROJECT_MEMBERS_USER_INDEX}, {"boards_project_id_idx", models.BOARDS_PROJECT_ID_INDEX},
		{"board_columns_board_position_idx", models.BOARD_COLUMNS_BOARD_POSITION_INDEX},
		{"board_columns_board_code_uidx", models.BOARD_COLUMNS_BOARD_CODE_UNIQUE_INDEX},
		{"board_columns_board_position_uidx", models.BOARD_COLUMNS_BOARD_POSITION_UNIQUE_INDEX},
		{"tasks_column_position_idx", models.TASKS_COLUMN_POSITION_INDEX},
		{"tasks_created_by_user_id_idx", models.TASKS_CREATED_BY_USER_ID_INDEX}, {"tasks_due_date_idx", models.TASKS_DUE_DATE_INDEX},
		{"task_assignees_user_id_idx", models.TASK_ASSIGNEES_USER_ID_INDEX}, {"task_history_task_occurred_at_idx", models.TASK_HISTORY_TASK_TIME_INDEX},
		{"comments_task_created_at_idx", models.COMMENTS_TASK_CREATED_AT_INDEX}, {"comments_author_user_id_idx", models.COMMENTS_AUTHOR_USER_ID_INDEX},
		{"attachments_task_created_at_idx", models.ATTACHMENTS_TASK_CREATED_AT_INDEX}, {"attachments_uploaded_by_user_id_idx", models.ATTACHMENTS_UPLOADED_BY_USER_ID_INDEX},
		{"attachments_storage_key_idx", models.ATTACHMENTS_STORAGE_KEY_INDEX}, {"lessons_group_starts_at_idx", models.LESSONS_GROUP_START_INDEX},
		{"event_types_created_by_user_id_idx", models.EVENT_TYPES_OWNER_INDEX}, {"event_types_project_id_idx", models.EVENT_TYPES_PROJECT_INDEX},
		{"events_owner_starts_at_idx", models.EVENTS_OWNER_START_INDEX}, {"events_project_starts_at_idx", models.EVENTS_PROJECT_START_INDEX},
		{"events_group_starts_at_idx", models.EVENTS_GROUP_START_INDEX},
	}
	return s.runStatements("create index", indexes)
}

func (s *Storage) runStatements(operation string, statements []struct{ name, query string }) error {
	tx, err := s.Db.Begin()
	if err != nil {
		return fmt.Errorf("begin %s transaction: %w", operation, err)
	}
	defer tx.Rollback()
	for _, statement := range statements {
		if _, err := tx.Exec(statement.query); err != nil {
			return fmt.Errorf("%s %s: %w", operation, statement.name, err)
		}
	}
	if err := tx.Commit(); err != nil {
		return fmt.Errorf("commit %s transaction: %w", operation, err)
	}
	return nil
}
