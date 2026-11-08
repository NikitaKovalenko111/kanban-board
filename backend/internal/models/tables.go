package models

const (
	STUDENT_GROUPS_TABLE = `
		CREATE TABLE IF NOT EXISTS student_groups (
			student_group_id UUID PRIMARY KEY,
			group_code VARCHAR NOT NULL UNIQUE,
			name VARCHAR,
			created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
			updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
		);`
	USERS_TABLE = `
		CREATE TABLE IF NOT EXISTS users (
			user_id UUID PRIMARY KEY,
			username VARCHAR NOT NULL,
			email VARCHAR,
			display_name VARCHAR NOT NULL,
			position VARCHAR,
			student_group_id UUID REFERENCES student_groups (student_group_id) ON DELETE SET NULL,
			image_url VARCHAR,
			contact VARCHAR,
			system_role VARCHAR NOT NULL DEFAULT 'MEMBER' CHECK (system_role IN ('CAPTAIN', 'MEMBER')),
			schedule_privacy VARCHAR NOT NULL DEFAULT 'BUSY' CHECK (schedule_privacy IN ('NOTHING', 'BUSY', 'TITLE', 'TITLE_AND_DESCRIPTION')),
			password_hash VARCHAR NOT NULL,
			is_active BOOLEAN NOT NULL DEFAULT TRUE,
			joined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
			created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
			updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
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
	PROJECTS_TABLE = `
		CREATE TABLE IF NOT EXISTS projects (
			project_id UUID PRIMARY KEY,
			created_by_user_id UUID NOT NULL REFERENCES users (user_id),
			name VARCHAR NOT NULL,
			description TEXT,
			color VARCHAR NOT NULL DEFAULT '#6576DF',
			join_policy VARCHAR NOT NULL DEFAULT 'INVITE_ONLY' CHECK (join_policy IN ('OPEN', 'INVITE_ONLY', 'CLOSED')),
			is_archived BOOLEAN NOT NULL DEFAULT FALSE,
			created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
			updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
		);`
	PROJECT_MEMBERS_TABLE = `
		CREATE TABLE IF NOT EXISTS project_members (
			project_id UUID NOT NULL REFERENCES projects (project_id) ON DELETE CASCADE,
			user_id UUID NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
			role VARCHAR NOT NULL DEFAULT 'Участник',
			status VARCHAR NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'BLOCKED', 'LEFT')),
			joined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
			updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
			PRIMARY KEY (project_id, user_id)
		);`
	BOARDS_TABLE = `
		CREATE TABLE IF NOT EXISTS boards (
			board_id UUID PRIMARY KEY,
			project_id UUID NOT NULL UNIQUE REFERENCES projects (project_id) ON DELETE CASCADE,
			name VARCHAR NOT NULL
		);`
	BOARD_COLUMNS_TABLE = `
		CREATE TABLE IF NOT EXISTS board_columns (
			column_id UUID PRIMARY KEY,
			board_id UUID NOT NULL REFERENCES boards (board_id) ON DELETE CASCADE,
			code VARCHAR NOT NULL,
			name VARCHAR NOT NULL,
			kind VARCHAR NOT NULL DEFAULT 'IN_PROGRESS' CHECK (kind IN ('BACKLOG', 'IN_PROGRESS', 'REVIEW', 'DONE')),
			color VARCHAR NOT NULL DEFAULT '#6576DF',
			position INTEGER NOT NULL CHECK (position >= 0),
			UNIQUE (board_id, code),
			UNIQUE (board_id, position)
		);`
	TASKS_TABLE = `
		CREATE TABLE IF NOT EXISTS tasks (
			task_id UUID PRIMARY KEY,
			column_id UUID NOT NULL REFERENCES board_columns (column_id) ON DELETE CASCADE,
			created_by_user_id UUID NOT NULL REFERENCES users (user_id),
			title VARCHAR NOT NULL,
			description TEXT,
			priority VARCHAR NOT NULL DEFAULT 'NORMAL' CHECK (priority IN ('LOW', 'NORMAL', 'HIGH')),
			position INTEGER NOT NULL DEFAULT 0 CHECK (position >= 0),
			due_date TIMESTAMPTZ,
			completed_at TIMESTAMPTZ,
			created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
			updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
		);`
	TASK_ASSIGNEES_TABLE = `
		CREATE TABLE IF NOT EXISTS task_assignees (
			task_id UUID NOT NULL REFERENCES tasks (task_id) ON DELETE CASCADE,
			user_id UUID NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
			PRIMARY KEY (task_id, user_id)
		);`
	TASK_HISTORY_TABLE = `
		CREATE TABLE IF NOT EXISTS task_history (
			task_history_id UUID PRIMARY KEY,
			task_id UUID NOT NULL REFERENCES tasks (task_id) ON DELETE CASCADE,
			actor_user_id UUID REFERENCES users (user_id) ON DELETE SET NULL,
			action VARCHAR NOT NULL,
			from_status VARCHAR,
			to_status VARCHAR,
			comment TEXT,
			occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
		);`
	COMMENTS_TABLE = `
		CREATE TABLE IF NOT EXISTS comments (
			comment_id UUID PRIMARY KEY,
			task_id UUID NOT NULL REFERENCES tasks (task_id) ON DELETE CASCADE,
			author_user_id UUID NOT NULL REFERENCES users (user_id),
			content TEXT NOT NULL,
			created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
			updated_at TIMESTAMPTZ
		);`
	ATTACHMENTS_TABLE = `
		CREATE TABLE IF NOT EXISTS attachments (
			attachment_id UUID PRIMARY KEY,
			task_id UUID NOT NULL REFERENCES tasks (task_id) ON DELETE CASCADE,
			uploaded_by_user_id UUID NOT NULL REFERENCES users (user_id),
			attachment_type VARCHAR NOT NULL CHECK (attachment_type IN ('SOURCE', 'RESULT')),
			content_kind VARCHAR NOT NULL DEFAULT 'FILE' CHECK (content_kind IN ('FILE', 'LINK')),
			title VARCHAR NOT NULL,
			link_url VARCHAR,
			file_name VARCHAR,
			storage_key VARCHAR,
			mime_type VARCHAR,
			file_size BIGINT CHECK (file_size IS NULL OR file_size >= 0),
			created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
			CHECK (
				(content_kind = 'FILE' AND file_name IS NOT NULL AND storage_key IS NOT NULL AND mime_type IS NOT NULL AND file_size IS NOT NULL AND link_url IS NULL) OR
				(content_kind = 'LINK' AND link_url IS NOT NULL AND file_name IS NULL AND storage_key IS NULL AND mime_type IS NULL AND file_size IS NULL)
			)
		);`
	LESSONS_TABLE = `
		CREATE TABLE IF NOT EXISTS lessons (
			lesson_id UUID PRIMARY KEY,
			student_group_id UUID NOT NULL REFERENCES student_groups (student_group_id) ON DELETE CASCADE,
			external_id VARCHAR,
			title VARCHAR NOT NULL,
			room VARCHAR,
			teacher VARCHAR,
			lesson_kind VARCHAR NOT NULL CHECK (lesson_kind IN ('LECTURE', 'PRACTICE', 'LAB')),
			starts_at TIMESTAMPTZ NOT NULL,
			ends_at TIMESTAMPTZ NOT NULL CHECK (ends_at > starts_at),
			source_status VARCHAR NOT NULL DEFAULT 'ACTUAL' CHECK (source_status IN ('ACTUAL', 'CACHED', 'UNAVAILABLE')),
			source_updated_at TIMESTAMPTZ,
			created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
			updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
		);`
	EVENT_TYPES_TABLE = `
		CREATE TABLE IF NOT EXISTS event_types (
			event_type_id UUID PRIMARY KEY,
			code VARCHAR UNIQUE,
			title VARCHAR NOT NULL,
			color VARCHAR NOT NULL,
			scope VARCHAR NOT NULL CHECK (scope IN ('GLOBAL', 'PERSONAL', 'PROJECT')),
			created_by_user_id UUID REFERENCES users (user_id) ON DELETE CASCADE,
			project_id UUID REFERENCES projects (project_id) ON DELETE CASCADE,
			created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
			updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
		);`
	EVENT_TYPE_REMINDERS_TABLE = `
		CREATE TABLE IF NOT EXISTS event_type_reminders (
			event_type_id UUID NOT NULL REFERENCES event_types (event_type_id) ON DELETE CASCADE,
			minutes_before INTEGER NOT NULL CHECK (minutes_before >= 0),
			PRIMARY KEY (event_type_id, minutes_before)
		);`
	EVENTS_TABLE = `
		CREATE TABLE IF NOT EXISTS events (
			event_id UUID PRIMARY KEY,
			owner_user_id UUID NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
			event_type_id UUID REFERENCES event_types (event_type_id) ON DELETE SET NULL,
			project_id UUID REFERENCES projects (project_id) ON DELETE CASCADE,
			student_group_id UUID REFERENCES student_groups (student_group_id) ON DELETE CASCADE,
			title VARCHAR NOT NULL,
			description TEXT,
			location VARCHAR,
			visibility VARCHAR NOT NULL CHECK (visibility IN ('PERSONAL', 'TEAM', 'GROUP')),
			starts_at TIMESTAMPTZ NOT NULL,
			ends_at TIMESTAMPTZ CHECK (ends_at IS NULL OR ends_at > starts_at),
			created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
			updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
			CHECK (
				(visibility = 'PERSONAL' AND project_id IS NULL AND student_group_id IS NULL) OR
				(visibility = 'TEAM' AND student_group_id IS NULL) OR
				(visibility = 'GROUP' AND project_id IS NULL AND student_group_id IS NOT NULL)
			)
		);`
	EVENT_REMINDERS_TABLE = `
		CREATE TABLE IF NOT EXISTS event_reminders (
			user_id UUID NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
			event_id UUID NOT NULL REFERENCES events (event_id) ON DELETE CASCADE,
			minutes_before INTEGER NOT NULL CHECK (minutes_before >= 0),
			PRIMARY KEY (user_id, event_id, minutes_before)
		);`
	USER_REMINDERS_TABLE = `
		CREATE TABLE IF NOT EXISTS user_reminders (
			user_id UUID NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
			item_key VARCHAR NOT NULL,
			minutes_before INTEGER NOT NULL CHECK (minutes_before BETWEEN 1 AND 40320),
			PRIMARY KEY (user_id, item_key, minutes_before)
		);`
	REMINDER_OVERRIDES_TABLE = `
		CREATE TABLE IF NOT EXISTS reminder_overrides (
			user_id UUID NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
			item_key VARCHAR NOT NULL,
			enabled BOOLEAN NOT NULL,
			updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
			PRIMARY KEY (user_id, item_key)
		);`
	PROJECT_SOURCES_TABLE = `
		CREATE TABLE IF NOT EXISTS project_sources (
			project_source_id UUID PRIMARY KEY,
			project_id UUID NOT NULL REFERENCES projects (project_id) ON DELETE CASCADE,
			kind VARCHAR NOT NULL CHECK (kind IN ('LINK', 'FILE')),
			title VARCHAR NOT NULL,
			url VARCHAR,
			storage_key VARCHAR,
			file_name VARCHAR,
			mime_type VARCHAR,
			file_size BIGINT CHECK (file_size IS NULL OR file_size >= 0),
			added_by_user_id UUID REFERENCES users (user_id) ON DELETE SET NULL,
			added_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
		);`
	NOTIFICATIONS_TABLE = `
		CREATE TABLE IF NOT EXISTS notifications (
			notification_id UUID PRIMARY KEY,
			user_id UUID NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
			kind VARCHAR NOT NULL CHECK (kind IN ('COMMENT', 'RETURNED', 'STATUS', 'EVENT', 'LESSON')),
			title VARCHAR NOT NULL,
			text VARCHAR NOT NULL,
			task_id UUID REFERENCES tasks (task_id) ON DELETE CASCADE,
			event_id UUID REFERENCES events (event_id) ON DELETE CASCADE,
			lesson_id UUID REFERENCES lessons (lesson_id) ON DELETE CASCADE,
			reminder_key VARCHAR,
			created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
			read_at TIMESTAMPTZ,
			push_sent_at TIMESTAMPTZ,
			CONSTRAINT notifications_target_check CHECK (
				num_nonnulls(task_id, event_id, lesson_id) = 1
			)
		);`
	PUSH_TOKENS_TABLE = `
		CREATE TABLE IF NOT EXISTS push_tokens (
			user_id UUID NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
			token VARCHAR NOT NULL,
			platform VARCHAR NOT NULL CHECK (platform IN ('android')),
			created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
			PRIMARY KEY (user_id, token)
		);`
)

const SCHEMA_MIGRATION = `
	ALTER TABLE users ADD COLUMN IF NOT EXISTS display_name VARCHAR;
	ALTER TABLE users ADD COLUMN IF NOT EXISTS position VARCHAR;
	ALTER TABLE users ADD COLUMN IF NOT EXISTS student_group_id UUID REFERENCES student_groups (student_group_id) ON DELETE SET NULL;
	ALTER TABLE users ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
	ALTER TABLE users ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
	ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
	ALTER TABLE users ADD COLUMN IF NOT EXISTS contact VARCHAR;
	ALTER TABLE users ADD COLUMN IF NOT EXISTS system_role VARCHAR NOT NULL DEFAULT 'MEMBER';
	ALTER TABLE users ADD COLUMN IF NOT EXISTS schedule_privacy VARCHAR NOT NULL DEFAULT 'BUSY';
	ALTER TABLE users ADD COLUMN IF NOT EXISTS joined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
	ALTER TABLE project_members DROP CONSTRAINT IF EXISTS project_members_role_check;
	DO $$
	BEGIN
		IF EXISTS (
			SELECT 1
			FROM information_schema.columns
			WHERE table_name = 'users'
				AND column_name = 'group_name'
		) THEN
			INSERT INTO student_groups (
				student_group_id,
				group_code,
				name
			)
			SELECT
				gen_random_uuid(),
				to_jsonb(u) ->> 'group_name',
				to_jsonb(u) ->> 'group_name'
			FROM users u
			WHERE COALESCE(to_jsonb(u) ->> 'group_name', '') <> ''
			ON CONFLICT (group_code) DO NOTHING;

			UPDATE users u
			SET student_group_id = sg.student_group_id
			FROM student_groups sg
			WHERE sg.group_code = to_jsonb(u) ->> 'group_name'
				AND u.student_group_id IS NULL;
			ALTER TABLE users DROP COLUMN IF EXISTS group_name;
		END IF;
	END $$;
	UPDATE users
	SET display_name = username
	WHERE display_name IS NULL
		OR display_name = '';
	ALTER TABLE users ALTER COLUMN display_name SET NOT NULL;
	ALTER TABLE projects ADD COLUMN IF NOT EXISTS created_by_user_id UUID REFERENCES users (user_id);
	ALTER TABLE projects ADD COLUMN IF NOT EXISTS join_policy VARCHAR NOT NULL DEFAULT 'INVITE_ONLY';
	ALTER TABLE projects ADD COLUMN IF NOT EXISTS is_archived BOOLEAN NOT NULL DEFAULT FALSE;
	ALTER TABLE projects ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
	DO $$
	BEGIN
		IF EXISTS (
			SELECT 1
			FROM information_schema.columns
			WHERE table_name = 'projects'
				AND column_name = 'team_id'
		)
			AND EXISTS (
				SELECT 1
				FROM information_schema.tables
				WHERE table_name = 'team_members'
			) THEN
			INSERT INTO project_members (
				project_id,
				user_id,
				role,
				status,
				joined_at,
				updated_at
			)
			SELECT
				p.project_id,
				tm.user_id,
				CASE WHEN UPPER(tm.role) IN ('LEAD', 'TEAM_LEAD') THEN 'LEAD' ELSE 'MEMBER' END,
				'ACTIVE',
				tm.joined_at,
				CURRENT_TIMESTAMP
			FROM projects p
			JOIN team_members tm ON tm.team_id = p.team_id
			ON CONFLICT (project_id, user_id) DO NOTHING;

			UPDATE projects p
			SET created_by_user_id = (
				SELECT pm.user_id
				FROM project_members pm
				WHERE pm.project_id = p.project_id
				ORDER BY CASE WHEN pm.role = 'LEAD' THEN 0 ELSE 1 END, pm.joined_at
				LIMIT 1
			)
			WHERE p.created_by_user_id IS NULL
				AND EXISTS (
					SELECT 1
					FROM project_members pm
					WHERE pm.project_id = p.project_id
				);
		END IF;
	END $$;
	ALTER TABLE projects DROP CONSTRAINT IF EXISTS projects_team_id_fkey;
	ALTER TABLE projects DROP COLUMN IF EXISTS team_id;
	ALTER TABLE board_columns ADD COLUMN IF NOT EXISTS code VARCHAR;
	UPDATE board_columns
	SET code = CASE position
		WHEN 0 THEN 'WAITING'
		WHEN 1 THEN 'WORKING'
		WHEN 2 THEN 'CHECKING'
		WHEN 3 THEN 'DONE'
		ELSE 'COLUMN_' || position::text
	END
	WHERE code IS NULL
		OR code = '';
	ALTER TABLE board_columns ALTER COLUMN code SET NOT NULL;
	ALTER TABLE board_columns ADD COLUMN IF NOT EXISTS kind VARCHAR NOT NULL DEFAULT 'IN_PROGRESS';
	ALTER TABLE board_columns ADD COLUMN IF NOT EXISTS color VARCHAR NOT NULL DEFAULT '#6576DF';
	UPDATE board_columns SET kind = CASE code
		WHEN 'WAITING' THEN 'BACKLOG'
		WHEN 'CHECKING' THEN 'REVIEW'
		WHEN 'DONE' THEN 'DONE'
		ELSE 'IN_PROGRESS'
	END;
	ALTER TABLE tasks ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;
	ALTER TABLE tasks DROP CONSTRAINT IF EXISTS tasks_column_id_fkey;
	ALTER TABLE tasks ADD CONSTRAINT tasks_column_id_fkey
		FOREIGN KEY (column_id) REFERENCES board_columns (column_id) ON DELETE CASCADE;
	ALTER TABLE tasks DROP CONSTRAINT IF EXISTS tasks_priority_check;
	UPDATE tasks SET priority = 'NORMAL' WHERE priority IN ('MEDIUM', 'CRITICAL');
	ALTER TABLE tasks ALTER COLUMN priority SET DEFAULT 'NORMAL';
	ALTER TABLE tasks ADD CONSTRAINT tasks_priority_check CHECK (priority IN ('LOW', 'NORMAL', 'HIGH'));
	ALTER TABLE comments ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;
	ALTER TABLE notifications ADD COLUMN IF NOT EXISTS push_sent_at TIMESTAMPTZ;
	ALTER TABLE notifications ADD COLUMN IF NOT EXISTS reminder_key VARCHAR;
	DROP INDEX IF EXISTS notifications_user_event_uidx;
	ALTER TABLE attachments ADD COLUMN IF NOT EXISTS attachment_type VARCHAR NOT NULL DEFAULT 'SOURCE';
	ALTER TABLE events DROP CONSTRAINT IF EXISTS events_visibility_check;
	ALTER TABLE events DROP CONSTRAINT IF EXISTS events_check;
	ALTER TABLE events DROP CONSTRAINT IF EXISTS events_ends_at_check;
	ALTER TABLE events DROP CONSTRAINT IF EXISTS events_event_type_id_fkey;
	ALTER TABLE events ALTER COLUMN event_type_id DROP NOT NULL;
	ALTER TABLE events ALTER COLUMN ends_at DROP NOT NULL;
	UPDATE events SET visibility = 'TEAM' WHERE visibility = 'PROJECT';
	ALTER TABLE events ADD CONSTRAINT events_visibility_check CHECK (visibility IN ('PERSONAL', 'TEAM', 'GROUP'));
	ALTER TABLE events ADD CONSTRAINT events_ends_at_check CHECK (ends_at IS NULL OR ends_at > starts_at);
	ALTER TABLE events ADD CONSTRAINT events_event_type_id_fkey FOREIGN KEY (event_type_id) REFERENCES event_types (event_type_id) ON DELETE SET NULL;
	ALTER TABLE event_reminders ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES users (user_id) ON DELETE CASCADE;
	UPDATE event_reminders er SET user_id = e.owner_user_id FROM events e WHERE e.event_id = er.event_id AND er.user_id IS NULL;
	ALTER TABLE event_reminders ALTER COLUMN user_id SET NOT NULL;
	ALTER TABLE event_reminders DROP CONSTRAINT IF EXISTS event_reminders_pkey;
	ALTER TABLE event_reminders ADD PRIMARY KEY (user_id, event_id, minutes_before);
	DROP INDEX IF EXISTS users_username_idx;
	DROP TABLE IF EXISTS team_members CASCADE;
	DROP TABLE IF EXISTS teams CASCADE;
`

const (
	USERS_USERNAME_INDEX                      = `CREATE UNIQUE INDEX IF NOT EXISTS users_username_idx ON users (LOWER(username));`
	USERS_EMAIL_INDEX                         = `CREATE UNIQUE INDEX IF NOT EXISTS users_email_idx ON users (email);`
	USERS_STUDENT_GROUP_INDEX                 = `CREATE INDEX IF NOT EXISTS users_student_group_id_idx ON users (student_group_id);`
	SESSIONS_REFRESH_TOKEN_HASH_INDEX         = `CREATE UNIQUE INDEX IF NOT EXISTS sessions_refresh_token_hash_idx ON sessions (refresh_token_hash);`
	SESSIONS_USER_ID_INDEX                    = `CREATE INDEX IF NOT EXISTS sessions_user_id_idx ON sessions (user_id);`
	SESSIONS_EXPIRES_AT_INDEX                 = `CREATE INDEX IF NOT EXISTS sessions_expires_at_idx ON sessions (expires_at);`
	SESSIONS_REVOKED_AT_INDEX                 = `CREATE INDEX IF NOT EXISTS sessions_revoked_at_idx ON sessions (revoked_at) WHERE revoked_at IS NOT NULL;`
	PROJECTS_CREATED_BY_INDEX                 = `CREATE INDEX IF NOT EXISTS projects_created_by_user_id_idx ON projects (created_by_user_id);`
	PROJECTS_NAME_UNIQUE_INDEX                = `CREATE UNIQUE INDEX IF NOT EXISTS projects_name_lower_uidx ON projects (LOWER(name)) WHERE is_archived = FALSE;`
	PROJECT_MEMBERS_USER_INDEX                = `CREATE INDEX IF NOT EXISTS project_members_user_id_idx ON project_members (user_id, status);`
	BOARDS_PROJECT_ID_INDEX                   = `CREATE UNIQUE INDEX IF NOT EXISTS boards_project_id_idx ON boards (project_id);`
	BOARD_COLUMNS_BOARD_POSITION_INDEX        = `CREATE INDEX IF NOT EXISTS board_columns_board_position_idx ON board_columns (board_id, position);`
	BOARD_COLUMNS_BOARD_CODE_UNIQUE_INDEX     = `CREATE UNIQUE INDEX IF NOT EXISTS board_columns_board_code_uidx ON board_columns (board_id, code);`
	BOARD_COLUMNS_BOARD_POSITION_UNIQUE_INDEX = `CREATE UNIQUE INDEX IF NOT EXISTS board_columns_board_position_uidx ON board_columns (board_id, position);`
	TASKS_COLUMN_POSITION_INDEX               = `CREATE INDEX IF NOT EXISTS tasks_column_position_idx ON tasks (column_id, position);`
	TASKS_CREATED_BY_USER_ID_INDEX            = `CREATE INDEX IF NOT EXISTS tasks_created_by_user_id_idx ON tasks (created_by_user_id);`
	TASKS_DUE_DATE_INDEX                      = `CREATE INDEX IF NOT EXISTS tasks_due_date_idx ON tasks (due_date) WHERE due_date IS NOT NULL;`
	TASK_ASSIGNEES_USER_ID_INDEX              = `CREATE INDEX IF NOT EXISTS task_assignees_user_id_idx ON task_assignees (user_id);`
	TASK_HISTORY_TASK_TIME_INDEX              = `CREATE INDEX IF NOT EXISTS task_history_task_occurred_at_idx ON task_history (task_id, occurred_at);`
	COMMENTS_TASK_CREATED_AT_INDEX            = `CREATE INDEX IF NOT EXISTS comments_task_created_at_idx ON comments (task_id, created_at);`
	COMMENTS_AUTHOR_USER_ID_INDEX             = `CREATE INDEX IF NOT EXISTS comments_author_user_id_idx ON comments (author_user_id);`
	ATTACHMENTS_TASK_CREATED_AT_INDEX         = `CREATE INDEX IF NOT EXISTS attachments_task_created_at_idx ON attachments (task_id, created_at);`
	ATTACHMENTS_UPLOADED_BY_USER_ID_INDEX     = `CREATE INDEX IF NOT EXISTS attachments_uploaded_by_user_id_idx ON attachments (uploaded_by_user_id);`
	ATTACHMENTS_STORAGE_KEY_INDEX             = `CREATE UNIQUE INDEX IF NOT EXISTS attachments_storage_key_idx ON attachments (storage_key);`
	LESSONS_GROUP_START_INDEX                 = `CREATE INDEX IF NOT EXISTS lessons_group_starts_at_idx ON lessons (student_group_id, starts_at);`
	LESSONS_GROUP_EXTERNAL_UNIQUE_INDEX       = `CREATE UNIQUE INDEX IF NOT EXISTS lessons_group_external_uidx ON lessons (student_group_id, external_id) WHERE external_id IS NOT NULL;`
	EVENT_TYPES_OWNER_INDEX                   = `CREATE INDEX IF NOT EXISTS event_types_created_by_user_id_idx ON event_types (created_by_user_id);`
	EVENT_TYPES_PROJECT_INDEX                 = `CREATE INDEX IF NOT EXISTS event_types_project_id_idx ON event_types (project_id);`
	EVENTS_OWNER_START_INDEX                  = `CREATE INDEX IF NOT EXISTS events_owner_starts_at_idx ON events (owner_user_id, starts_at);`
	EVENTS_PROJECT_START_INDEX                = `CREATE INDEX IF NOT EXISTS events_project_starts_at_idx ON events (project_id, starts_at);`
	EVENTS_GROUP_START_INDEX                  = `CREATE INDEX IF NOT EXISTS events_group_starts_at_idx ON events (student_group_id, starts_at);`
	PROJECT_SOURCES_PROJECT_INDEX             = `CREATE INDEX IF NOT EXISTS project_sources_project_id_idx ON project_sources (project_id, added_at);`
	NOTIFICATIONS_USER_INDEX                  = `CREATE INDEX IF NOT EXISTS notifications_user_created_at_idx ON notifications (user_id, created_at DESC);`
	NOTIFICATIONS_UNREAD_INDEX                = `CREATE INDEX IF NOT EXISTS notifications_user_unread_idx ON notifications (user_id) WHERE read_at IS NULL;`
	NOTIFICATIONS_EVENT_UNIQUE_INDEX          = `CREATE UNIQUE INDEX IF NOT EXISTS notifications_user_event_uidx ON notifications (user_id, event_id, reminder_key) WHERE event_id IS NOT NULL AND kind = 'EVENT' AND reminder_key IS NOT NULL;`
	NOTIFICATIONS_LESSON_UNIQUE_INDEX         = `CREATE UNIQUE INDEX IF NOT EXISTS notifications_user_lesson_uidx ON notifications (user_id, lesson_id, reminder_key) WHERE lesson_id IS NOT NULL AND kind = 'LESSON' AND reminder_key IS NOT NULL;`
	USER_REMINDERS_USER_INDEX                 = `CREATE INDEX IF NOT EXISTS user_reminders_user_id_idx ON user_reminders (user_id);`
)
