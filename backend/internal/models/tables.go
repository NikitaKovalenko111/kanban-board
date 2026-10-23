package models

const (
	USER_TABLE = `
		CREATE TABLE IF NOT EXISTS "user" (
			id UUID PRIMARY KEY,
			username VARCHAR NOT NULL,
			email VARCHAR NOT NULL,
			"group" VARCHAR NOT NULL,
			image_url VARCHAR,
			password_hash VARCHAR NOT NULL,
			created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
		);`

	TEAM_TABLE = `
		CREATE TABLE IF NOT EXISTS team (
			id UUID PRIMARY KEY,
			name VARCHAR NOT NULL,
			created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
		);`

	TEAM_MEMBER_TABLE = `
		CREATE TABLE IF NOT EXISTS team_member (
			team_id UUID NOT NULL REFERENCES team (id) ON DELETE CASCADE,
			user_id UUID NOT NULL REFERENCES "user" (id) ON DELETE CASCADE,
			role VARCHAR NOT NULL,
			joined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
			PRIMARY KEY (team_id, user_id)
		);`

	PROJECT_TABLE = `
		CREATE TABLE IF NOT EXISTS project (
			id UUID PRIMARY KEY,
			team_id UUID NOT NULL REFERENCES team (id) ON DELETE CASCADE,
			name VARCHAR NOT NULL,
			description TEXT,
			created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
		);`

	BOARD_TABLE = `
		CREATE TABLE IF NOT EXISTS board (
			id UUID PRIMARY KEY,
			project_id UUID NOT NULL REFERENCES project (id) ON DELETE CASCADE,
			name VARCHAR NOT NULL
		);`

	BOARD_COLUMN_TABLE = `
		CREATE TABLE IF NOT EXISTS board_column (
			id UUID PRIMARY KEY,
			board_id UUID NOT NULL REFERENCES board (id) ON DELETE CASCADE,
			name VARCHAR NOT NULL,
			position INTEGER NOT NULL
		);`

	TASK_TABLE = `
		CREATE TABLE IF NOT EXISTS task (
			id UUID PRIMARY KEY,
			column_id UUID NOT NULL REFERENCES board_column (id) ON DELETE CASCADE,
			created_by UUID NOT NULL REFERENCES "user" (id),
			title VARCHAR NOT NULL,
			description TEXT,
			priority VARCHAR NOT NULL,
			position INTEGER NOT NULL,
			due_date TIMESTAMPTZ,
			created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
			updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
		);`

	TASK_ASSIGNEE_TABLE = `
		CREATE TABLE IF NOT EXISTS task_assignee (
			task_id UUID NOT NULL REFERENCES task (id) ON DELETE CASCADE,
			user_id UUID NOT NULL REFERENCES "user" (id) ON DELETE CASCADE,
			PRIMARY KEY (task_id, user_id)
		);`

	COMMENT_TABLE = `
		CREATE TABLE IF NOT EXISTS comment (
			id UUID PRIMARY KEY,
			task_id UUID NOT NULL REFERENCES task (id) ON DELETE CASCADE,
			author_id UUID NOT NULL REFERENCES "user" (id),
			content TEXT NOT NULL,
			created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
		);`

	ATTACHMENT_TABLE = `
		CREATE TABLE IF NOT EXISTS attachment (
			id UUID PRIMARY KEY,
			task_id UUID NOT NULL REFERENCES task (id) ON DELETE CASCADE,
			uploaded_by UUID NOT NULL REFERENCES "user" (id),
			file_name VARCHAR NOT NULL,
			storage_key VARCHAR NOT NULL,
			mime_type VARCHAR NOT NULL,
			file_size BIGINT NOT NULL,
			created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
		);`
)

const (
	USER_USERNAME_INDEX = `
		CREATE UNIQUE INDEX IF NOT EXISTS user_username_idx
		ON "user" (username);`

	USER_EMAIL_INDEX = `
		CREATE UNIQUE INDEX IF NOT EXISTS user_email_idx
		ON "user" (email);`

	TEAM_MEMBER_USER_ID_INDEX = `
		CREATE INDEX IF NOT EXISTS team_member_user_id_idx
		ON team_member (user_id);`

	PROJECT_TEAM_ID_INDEX = `
		CREATE INDEX IF NOT EXISTS project_team_id_idx
		ON project (team_id);`

	BOARD_PROJECT_ID_INDEX = `
		CREATE INDEX IF NOT EXISTS board_project_id_idx
		ON board (project_id);`

	BOARD_COLUMN_BOARD_POSITION_INDEX = `
		CREATE INDEX IF NOT EXISTS board_column_board_position_idx
		ON board_column (board_id, position);`

	TASK_COLUMN_POSITION_INDEX = `
		CREATE INDEX IF NOT EXISTS task_column_position_idx
		ON task (column_id, position);`

	TASK_CREATED_BY_INDEX = `
		CREATE INDEX IF NOT EXISTS task_created_by_idx
		ON task (created_by);`

	TASK_ASSIGNEE_USER_ID_INDEX = `
		CREATE INDEX IF NOT EXISTS task_assignee_user_id_idx
		ON task_assignee (user_id);`

	COMMENT_TASK_CREATED_AT_INDEX = `
		CREATE INDEX IF NOT EXISTS comment_task_created_at_idx
		ON comment (task_id, created_at);`

	COMMENT_AUTHOR_ID_INDEX = `
		CREATE INDEX IF NOT EXISTS comment_author_id_idx
		ON comment (author_id);`

	ATTACHMENT_TASK_CREATED_AT_INDEX = `
		CREATE INDEX IF NOT EXISTS attachment_task_created_at_idx
		ON attachment (task_id, created_at);`

	ATTACHMENT_UPLOADED_BY_INDEX = `
		CREATE INDEX IF NOT EXISTS attachment_uploaded_by_idx
		ON attachment (uploaded_by);`

	ATTACHMENT_STORAGE_KEY_INDEX = `
		CREATE UNIQUE INDEX IF NOT EXISTS attachment_storage_key_idx
		ON attachment (storage_key);`
)
