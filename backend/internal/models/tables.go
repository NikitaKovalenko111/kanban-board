package models

const (
	USERS_TABLE = `
		CREATE TABLE IF NOT EXISTS users (
			user_id UUID PRIMARY KEY,
			username VARCHAR NOT NULL,
			email VARCHAR NOT NULL,
			group_name VARCHAR NOT NULL,
			image_url VARCHAR,
			password_hash VARCHAR NOT NULL,
			created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
		);`

	SESSIONS_TABLE = `
		CREATE TABLE IF NOT EXISTS sessions (
			session_id UUID PRIMARY KEY,
			user_id UUID NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
			refresh_token_hash VARCHAR(64) NOT NULL,
			expires_at TIMESTAMPTZ NOT NULL,
			created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
			revoked_at TIMESTAMPTZ
		);`

	TEAMS_TABLE = `
		CREATE TABLE IF NOT EXISTS teams (
			team_id UUID PRIMARY KEY,
			name VARCHAR NOT NULL,
			created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
		);`

	TEAM_MEMBERS_TABLE = `
		CREATE TABLE IF NOT EXISTS team_members (
			team_id UUID NOT NULL REFERENCES teams (team_id) ON DELETE CASCADE,
			user_id UUID NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
			role VARCHAR NOT NULL,
			joined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
			PRIMARY KEY (team_id, user_id)
		);`

	PROJECTS_TABLE = `
		CREATE TABLE IF NOT EXISTS projects (
			project_id UUID PRIMARY KEY,
			team_id UUID NOT NULL REFERENCES teams (team_id) ON DELETE CASCADE,
			name VARCHAR NOT NULL,
			description TEXT,
			created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
		);`

	BOARDS_TABLE = `
		CREATE TABLE IF NOT EXISTS boards (
			board_id UUID PRIMARY KEY,
			project_id UUID NOT NULL REFERENCES projects (project_id) ON DELETE CASCADE,
			name VARCHAR NOT NULL
		);`

	BOARD_COLUMNS_TABLE = `
		CREATE TABLE IF NOT EXISTS board_columns (
			column_id UUID PRIMARY KEY,
			board_id UUID NOT NULL REFERENCES boards (board_id) ON DELETE CASCADE,
			name VARCHAR NOT NULL,
			position INTEGER NOT NULL
		);`

	TASKS_TABLE = `
		CREATE TABLE IF NOT EXISTS tasks (
			task_id UUID PRIMARY KEY,
			column_id UUID NOT NULL REFERENCES board_columns (column_id) ON DELETE CASCADE,
			created_by_user_id UUID NOT NULL REFERENCES users (user_id),
			title VARCHAR NOT NULL,
			description TEXT,
			priority VARCHAR NOT NULL,
			position INTEGER NOT NULL,
			due_date TIMESTAMPTZ,
			created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
			updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
		);`

	TASK_ASSIGNEES_TABLE = `
		CREATE TABLE IF NOT EXISTS task_assignees (
			task_id UUID NOT NULL REFERENCES tasks (task_id) ON DELETE CASCADE,
			user_id UUID NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
			PRIMARY KEY (task_id, user_id)
		);`

	COMMENTS_TABLE = `
		CREATE TABLE IF NOT EXISTS comments (
			comment_id UUID PRIMARY KEY,
			task_id UUID NOT NULL REFERENCES tasks (task_id) ON DELETE CASCADE,
			author_user_id UUID NOT NULL REFERENCES users (user_id),
			content TEXT NOT NULL,
			created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
		);`

	ATTACHMENTS_TABLE = `
		CREATE TABLE IF NOT EXISTS attachments (
			attachment_id UUID PRIMARY KEY,
			task_id UUID NOT NULL REFERENCES tasks (task_id) ON DELETE CASCADE,
			uploaded_by_user_id UUID NOT NULL REFERENCES users (user_id),
			file_name VARCHAR NOT NULL,
			storage_key VARCHAR NOT NULL,
			mime_type VARCHAR NOT NULL,
			file_size BIGINT NOT NULL,
			created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
		);`
)

const (
	USERS_USERNAME_INDEX = `
		CREATE UNIQUE INDEX IF NOT EXISTS users_username_idx
		ON users (username);`

	USERS_EMAIL_INDEX = `
		CREATE UNIQUE INDEX IF NOT EXISTS users_email_idx
		ON users (email);`

	SESSIONS_REFRESH_TOKEN_HASH_INDEX = `
		CREATE UNIQUE INDEX IF NOT EXISTS sessions_refresh_token_hash_idx
		ON sessions (refresh_token_hash);`

	SESSIONS_USER_ID_INDEX = `
		CREATE INDEX IF NOT EXISTS sessions_user_id_idx
		ON sessions (user_id);`

	SESSIONS_EXPIRES_AT_INDEX = `
		CREATE INDEX IF NOT EXISTS sessions_expires_at_idx
		ON sessions (expires_at);`

	TEAM_MEMBERS_USER_ID_INDEX = `
		CREATE INDEX IF NOT EXISTS team_members_user_id_idx
		ON team_members (user_id);`

	PROJECTS_TEAM_ID_INDEX = `
		CREATE INDEX IF NOT EXISTS projects_team_id_idx
		ON projects (team_id);`

	BOARDS_PROJECT_ID_INDEX = `
		CREATE INDEX IF NOT EXISTS boards_project_id_idx
		ON boards (project_id);`

	BOARD_COLUMNS_BOARD_POSITION_INDEX = `
		CREATE INDEX IF NOT EXISTS board_columns_board_position_idx
		ON board_columns (board_id, position);`

	TASKS_COLUMN_POSITION_INDEX = `
		CREATE INDEX IF NOT EXISTS tasks_column_position_idx
		ON tasks (column_id, position);`

	TASKS_CREATED_BY_USER_ID_INDEX = `
		CREATE INDEX IF NOT EXISTS tasks_created_by_user_id_idx
		ON tasks (created_by_user_id);`

	TASK_ASSIGNEES_USER_ID_INDEX = `
		CREATE INDEX IF NOT EXISTS task_assignees_user_id_idx
		ON task_assignees (user_id);`

	COMMENTS_TASK_CREATED_AT_INDEX = `
		CREATE INDEX IF NOT EXISTS comments_task_created_at_idx
		ON comments (task_id, created_at);`

	COMMENTS_AUTHOR_USER_ID_INDEX = `
		CREATE INDEX IF NOT EXISTS comments_author_user_id_idx
		ON comments (author_user_id);`

	ATTACHMENTS_TASK_CREATED_AT_INDEX = `
		CREATE INDEX IF NOT EXISTS attachments_task_created_at_idx
		ON attachments (task_id, created_at);`

	ATTACHMENTS_UPLOADED_BY_USER_ID_INDEX = `
		CREATE INDEX IF NOT EXISTS attachments_uploaded_by_user_id_idx
		ON attachments (uploaded_by_user_id);`

	ATTACHMENTS_STORAGE_KEY_INDEX = `
		CREATE UNIQUE INDEX IF NOT EXISTS attachments_storage_key_idx
		ON attachments (storage_key);`
)
