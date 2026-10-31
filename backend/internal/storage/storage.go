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

type statement struct {
	name  string
	query string
}

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
	appStorage := &Storage{
		Db: db,
	}
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
	tables := []statement{
		{name: "student_groups", query: models.STUDENT_GROUPS_TABLE},
		{name: "users", query: models.USERS_TABLE},
		{name: "sessions", query: models.SESSIONS_TABLE},
		{name: "projects", query: models.PROJECTS_TABLE},
		{name: "project_members", query: models.PROJECT_MEMBERS_TABLE},
		{name: "boards", query: models.BOARDS_TABLE},
		{name: "board_columns", query: models.BOARD_COLUMNS_TABLE},
		{name: "tasks", query: models.TASKS_TABLE},
		{name: "task_assignees", query: models.TASK_ASSIGNEES_TABLE},
		{name: "task_history", query: models.TASK_HISTORY_TABLE},
		{name: "comments", query: models.COMMENTS_TABLE},
		{name: "attachments", query: models.ATTACHMENTS_TABLE},
		{name: "lessons", query: models.LESSONS_TABLE},
		{name: "event_types", query: models.EVENT_TYPES_TABLE},
		{name: "event_type_reminders", query: models.EVENT_TYPE_REMINDERS_TABLE},
		{name: "events", query: models.EVENTS_TABLE},
		{name: "event_reminders", query: models.EVENT_REMINDERS_TABLE},
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
	indexes := []statement{
		{name: "users_username_idx", query: models.USERS_USERNAME_INDEX},
		{name: "users_email_idx", query: models.USERS_EMAIL_INDEX},
		{name: "users_student_group_id_idx", query: models.USERS_STUDENT_GROUP_INDEX},
		{name: "sessions_refresh_token_hash_idx", query: models.SESSIONS_REFRESH_TOKEN_HASH_INDEX},
		{name: "sessions_user_id_idx", query: models.SESSIONS_USER_ID_INDEX},
		{name: "sessions_expires_at_idx", query: models.SESSIONS_EXPIRES_AT_INDEX},
		{name: "sessions_revoked_at_idx", query: models.SESSIONS_REVOKED_AT_INDEX},
		{name: "projects_created_by_user_id_idx", query: models.PROJECTS_CREATED_BY_INDEX},
		{name: "project_members_user_id_idx", query: models.PROJECT_MEMBERS_USER_INDEX},
		{name: "boards_project_id_idx", query: models.BOARDS_PROJECT_ID_INDEX},
		{name: "board_columns_board_position_idx", query: models.BOARD_COLUMNS_BOARD_POSITION_INDEX},
		{name: "board_columns_board_code_uidx", query: models.BOARD_COLUMNS_BOARD_CODE_UNIQUE_INDEX},
		{name: "board_columns_board_position_uidx", query: models.BOARD_COLUMNS_BOARD_POSITION_UNIQUE_INDEX},
		{name: "tasks_column_position_idx", query: models.TASKS_COLUMN_POSITION_INDEX},
		{name: "tasks_created_by_user_id_idx", query: models.TASKS_CREATED_BY_USER_ID_INDEX},
		{name: "tasks_due_date_idx", query: models.TASKS_DUE_DATE_INDEX},
		{name: "task_assignees_user_id_idx", query: models.TASK_ASSIGNEES_USER_ID_INDEX},
		{name: "task_history_task_occurred_at_idx", query: models.TASK_HISTORY_TASK_TIME_INDEX},
		{name: "comments_task_created_at_idx", query: models.COMMENTS_TASK_CREATED_AT_INDEX},
		{name: "comments_author_user_id_idx", query: models.COMMENTS_AUTHOR_USER_ID_INDEX},
		{name: "attachments_task_created_at_idx", query: models.ATTACHMENTS_TASK_CREATED_AT_INDEX},
		{name: "attachments_uploaded_by_user_id_idx", query: models.ATTACHMENTS_UPLOADED_BY_USER_ID_INDEX},
		{name: "attachments_storage_key_idx", query: models.ATTACHMENTS_STORAGE_KEY_INDEX},
		{name: "lessons_group_starts_at_idx", query: models.LESSONS_GROUP_START_INDEX},
		{name: "event_types_created_by_user_id_idx", query: models.EVENT_TYPES_OWNER_INDEX},
		{name: "event_types_project_id_idx", query: models.EVENT_TYPES_PROJECT_INDEX},
		{name: "events_owner_starts_at_idx", query: models.EVENTS_OWNER_START_INDEX},
		{name: "events_project_starts_at_idx", query: models.EVENTS_PROJECT_START_INDEX},
		{name: "events_group_starts_at_idx", query: models.EVENTS_GROUP_START_INDEX},
	}
	return s.runStatements("create index", indexes)
}

func (s *Storage) runStatements(operation string, statements []statement) error {
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
