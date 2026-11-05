package task_repo

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"time"

	"kanban-board/internal/models"
	repositorycommon "kanban-board/internal/storage/repositories/common"
)

type Repository struct {
	db *sql.DB
}

func Init(db *sql.DB) *Repository {
	return &Repository{db: db}
}

func (r *Repository) ListTasks(ctx context.Context, userID, projectID string) ([]models.Task, error) {
	if err := repositorycommon.RequireMember(ctx, r.db, userID, projectID); err != nil {
		return nil, err
	}
	return r.queryTasks(ctx, `b.project_id = $1`, projectID)
}

func (r *Repository) GetTask(ctx context.Context, userID, taskID string) (*models.Task, error) {
	var projectID string
	if err := r.db.QueryRowContext(ctx, `
		SELECT b.project_id
		FROM tasks t
		JOIN board_columns bc ON bc.column_id = t.column_id
		JOIN boards b ON b.board_id = bc.board_id
		WHERE t.task_id = $1
	`, taskID).Scan(&projectID); errors.Is(err, sql.ErrNoRows) {
		return nil, repositorycommon.ErrNotFound
	} else if err != nil {
		return nil, err
	}
	if err := repositorycommon.RequireMember(ctx, r.db, userID, projectID); err != nil {
		return nil, err
	}
	tasks, err := r.queryTasks(ctx, `t.task_id = $1`, taskID)
	if err != nil {
		return nil, err
	}
	if len(tasks) == 0 {
		return nil, repositorycommon.ErrNotFound
	}
	task := &tasks[0]
	if err := r.fillTask(ctx, task); err != nil {
		return nil, err
	}
	return task, nil
}

func (r *Repository) queryTasks(ctx context.Context, condition string, arguments ...any) ([]models.Task, error) {
	query := fmt.Sprintf(`
		SELECT
			t.task_id,
			b.project_id,
			t.column_id,
			t.position,
			t.title,
			COALESCE(t.description, ''),
			t.priority,
			(
				SELECT ta.user_id
				FROM task_assignees ta
				WHERE ta.task_id = t.task_id
				LIMIT 1
			),
			t.created_by_user_id,
			t.created_at,
			t.due_date,
			t.completed_at
		FROM tasks t
		JOIN board_columns bc ON bc.column_id = t.column_id
		JOIN boards b ON b.board_id = bc.board_id
		WHERE %s
		ORDER BY bc.position, t.position
	`, condition)
	rows, err := r.db.QueryContext(ctx, query, arguments...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	tasks := make([]models.Task, 0)
	for rows.Next() {
		var task models.Task
		if err := rows.Scan(
			&task.ID, &task.ProjectID, &task.ColumnID, &task.Position,
			&task.Title, &task.Description, &task.Priority, &task.AssigneeID,
			&task.CreatedBy, &task.CreatedAt, &task.DueAt, &task.CompletedAt,
		); err != nil {
			return nil, err
		}
		tasks = append(tasks, task)
	}
	return tasks, rows.Err()
}

func (r *Repository) fillTask(ctx context.Context, task *models.Task) error {
	var err error
	task.Comments, err = r.comments(ctx, task.ID)
	if err != nil {
		return err
	}
	task.Attachments, err = r.attachments(ctx, task.ID)
	if err != nil {
		return err
	}
	task.History, err = r.history(ctx, task.ID)
	return err
}

func (r *Repository) CreateTask(
	ctx context.Context,
	userID, projectID, title, description, priority string,
	assigneeID *string,
	dueAt *time.Time,
	columnID *string,
) (string, error) {
	if err := repositorycommon.RequireCaptain(ctx, r.db, userID); err != nil {
		return "", err
	}
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return "", err
	}
	defer tx.Rollback()

	selectedColumnID := ""
	if columnID != nil {
		selectedColumnID = *columnID
		var belongs bool
		if err := tx.QueryRowContext(ctx, `
			SELECT EXISTS(
				SELECT 1 FROM board_columns bc
				JOIN boards b ON b.board_id = bc.board_id
				WHERE bc.column_id = $1 AND b.project_id = $2
			)
		`, selectedColumnID, projectID).Scan(&belongs); err != nil || !belongs {
			return "", repositorycommon.ErrConflict
		}
	} else if err := tx.QueryRowContext(ctx, `
		SELECT bc.column_id
		FROM board_columns bc
		JOIN boards b ON b.board_id = bc.board_id
		WHERE b.project_id = $1 AND bc.kind = 'BACKLOG'
		ORDER BY bc.position LIMIT 1
	`, projectID).Scan(&selectedColumnID); err != nil {
		return "", repositorycommon.ErrNotFound
	}

	var position int
	if err := tx.QueryRowContext(ctx, `SELECT COALESCE(MAX(position), -1) + 1 FROM tasks WHERE column_id = $1`, selectedColumnID).Scan(&position); err != nil {
		return "", err
	}
	var taskID string
	if err := tx.QueryRowContext(ctx, `
		INSERT INTO tasks (
			task_id, column_id, created_by_user_id, title,
			description, priority, position, due_date
		)
		VALUES (gen_random_uuid(), $1, $2, $3, NULLIF($4, ''), $5, $6, $7)
		RETURNING task_id
	`, selectedColumnID, userID, title, description, priority, position, dueAt).Scan(&taskID); err != nil {
		return "", err
	}
	if assigneeID != nil {
		if err := assign(ctx, tx, taskID, projectID, *assigneeID); err != nil {
			return "", err
		}
	}
	if err := insertHistory(ctx, tx, taskID, userID, "CREATED", "", "", ""); err != nil {
		return "", err
	}
	if err := tx.Commit(); err != nil {
		return "", err
	}
	return taskID, nil
}

func (r *Repository) UpdateTask(
	ctx context.Context,
	userID, taskID string,
	title, description, priority *string,
	dueAt *time.Time,
	dueAtProvided bool,
	assigneeID *string,
	assigneeProvided bool,
) error {
	if err := repositorycommon.RequireCaptain(ctx, r.db, userID); err != nil {
		return err
	}
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	var projectID string
	result, err := tx.ExecContext(ctx, `
		UPDATE tasks
		SET
			title = COALESCE($1, title),
			description = CASE WHEN $2 THEN NULLIF($3, '') ELSE description END,
			priority = COALESCE($4, priority),
			due_date = CASE WHEN $5 THEN $6 ELSE due_date END,
			updated_at = CURRENT_TIMESTAMP
		WHERE task_id = $7
	`, title, description != nil, description, priority, dueAtProvided, dueAt, taskID)
	if err != nil {
		return err
	}
	if err := repositorycommon.RequireAffected(result); err != nil {
		return err
	}
	if err := tx.QueryRowContext(ctx, `
		SELECT b.project_id FROM tasks t
		JOIN board_columns bc ON bc.column_id = t.column_id
		JOIN boards b ON b.board_id = bc.board_id
		WHERE t.task_id = $1
	`, taskID).Scan(&projectID); err != nil {
		return err
	}
	if assigneeProvided {
		if _, err := tx.ExecContext(ctx, `DELETE FROM task_assignees WHERE task_id = $1`, taskID); err != nil {
			return err
		}
		if assigneeID != nil {
			if err := assign(ctx, tx, taskID, projectID, *assigneeID); err != nil {
				return err
			}
		}
	}
	if err := insertHistory(ctx, tx, taskID, userID, "EDITED", "", "", ""); err != nil {
		return err
	}
	return tx.Commit()
}

func (r *Repository) DeleteTask(ctx context.Context, userID, taskID string) ([]string, error) {
	if err := repositorycommon.RequireCaptain(ctx, r.db, userID); err != nil {
		return nil, err
	}
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, err
	}
	defer tx.Rollback()

	rows, err := tx.QueryContext(ctx, `SELECT storage_key FROM attachments WHERE task_id = $1`, taskID)
	if err != nil {
		return nil, err
	}
	storageKeys := make([]string, 0)
	for rows.Next() {
		var storageKey string
		if err := rows.Scan(&storageKey); err != nil {
			rows.Close()
			return nil, err
		}
		storageKeys = append(storageKeys, storageKey)
	}
	if err := rows.Close(); err != nil {
		return nil, err
	}

	result, err := tx.ExecContext(ctx, `DELETE FROM tasks WHERE task_id = $1`, taskID)
	if err != nil {
		return nil, err
	}
	if err := repositorycommon.RequireAffected(result); err != nil {
		return nil, err
	}
	if err := tx.Commit(); err != nil {
		return nil, err
	}

	return storageKeys, nil
}

func (r *Repository) TakeTask(ctx context.Context, userID, taskID string) error {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	var projectID, fromTitle, targetID, targetTitle string
	var assignedCount int
	if err := tx.QueryRowContext(ctx, `
		SELECT b.project_id, bc.name, (SELECT COUNT(*) FROM task_assignees WHERE task_id = t.task_id)
		FROM tasks t
		JOIN board_columns bc ON bc.column_id = t.column_id
		JOIN boards b ON b.board_id = bc.board_id
		WHERE t.task_id = $1 AND bc.kind = 'BACKLOG'
		FOR UPDATE OF t
	`, taskID).Scan(&projectID, &fromTitle, &assignedCount); errors.Is(err, sql.ErrNoRows) {
		return repositorycommon.ErrConflict
	} else if err != nil {
		return err
	}
	if err := repositorycommon.RequireMember(ctx, tx, userID, projectID); err != nil {
		return err
	}
	if assignedCount > 0 {
		return repositorycommon.ErrConflict
	}
	if _, err := tx.ExecContext(ctx, `
		INSERT INTO project_members (project_id, user_id, role, status)
		SELECT $1, $2, 'Капитан', 'ACTIVE'
		WHERE EXISTS(
			SELECT 1
			FROM users
			WHERE user_id = $2 AND system_role = 'CAPTAIN' AND is_active = TRUE
		)
		ON CONFLICT (project_id, user_id)
		DO UPDATE SET status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP
	`, projectID, userID); err != nil {
		return err
	}
	if err := tx.QueryRowContext(ctx, `
		SELECT bc.column_id, bc.name
		FROM board_columns bc JOIN boards b ON b.board_id = bc.board_id
		WHERE b.project_id = $1 AND bc.kind = 'IN_PROGRESS'
		ORDER BY bc.position LIMIT 1
	`, projectID).Scan(&targetID, &targetTitle); err != nil {
		return repositorycommon.ErrConflict
	}
	if err := assign(ctx, tx, taskID, projectID, userID); err != nil {
		return err
	}
	if _, err := tx.ExecContext(ctx, `UPDATE tasks SET column_id = $1, updated_at = CURRENT_TIMESTAMP WHERE task_id = $2`, targetID, taskID); err != nil {
		return err
	}
	if err := insertHistory(ctx, tx, taskID, userID, "TAKEN", fromTitle, targetTitle, ""); err != nil {
		return err
	}
	if err := insertTaskNotifications(ctx, tx, taskID, userID, "TAKEN", "Задача взята в работу"); err != nil {
		return err
	}
	return tx.Commit()
}

func (r *Repository) AssignToMe(ctx context.Context, userID, taskID string) error {
	if err := repositorycommon.RequireCaptain(ctx, r.db, userID); err != nil {
		return err
	}
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	var projectID string
	if err := tx.QueryRowContext(ctx, `
		SELECT b.project_id FROM tasks t
		JOIN board_columns bc ON bc.column_id = t.column_id
		JOIN boards b ON b.board_id = bc.board_id
		WHERE t.task_id = $1
	`, taskID).Scan(&projectID); err != nil {
		return repositorycommon.ErrNotFound
	}
	if _, err := tx.ExecContext(ctx, `
		INSERT INTO project_members (project_id, user_id, role, status)
		VALUES ($1, $2, 'Капитан', 'ACTIVE')
		ON CONFLICT (project_id, user_id) DO UPDATE SET status = 'ACTIVE'
	`, projectID, userID); err != nil {
		return err
	}
	if _, err := tx.ExecContext(ctx, `
		INSERT INTO notifications (notification_id, user_id, kind, title, text, task_id)
		SELECT
			gen_random_uuid(),
			ta.user_id,
			'STATUS',
			'Задача переназначена',
			'Капитан назначил задачу себе',
			$1
		FROM task_assignees ta
		WHERE ta.task_id = $1 AND ta.user_id <> $2
	`, taskID, userID); err != nil {
		return err
	}
	if _, err := tx.ExecContext(ctx, `DELETE FROM task_assignees WHERE task_id = $1`, taskID); err != nil {
		return err
	}
	if err := assign(ctx, tx, taskID, projectID, userID); err != nil {
		return err
	}
	if err := insertHistory(ctx, tx, taskID, userID, "ASSIGNED", "", "", ""); err != nil {
		return err
	}
	return tx.Commit()
}

func (r *Repository) MoveTask(ctx context.Context, userID, taskID, columnID, comment string) error {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	var projectID, fromID, fromTitle, fromKind, toTitle, toKind, role string
	var assigned bool
	err = tx.QueryRowContext(ctx, `
		SELECT
			b.project_id, t.column_id, source.name, source.kind,
			target.name, target.kind, u.system_role,
			EXISTS(SELECT 1 FROM task_assignees ta WHERE ta.task_id = t.task_id AND ta.user_id = $2)
		FROM tasks t
		JOIN board_columns source ON source.column_id = t.column_id
		JOIN boards b ON b.board_id = source.board_id
		JOIN board_columns target ON target.column_id = $3 AND target.board_id = b.board_id
		JOIN users u ON u.user_id = $2 AND u.is_active = TRUE
		WHERE t.task_id = $1
		FOR UPDATE OF t
	`, taskID, userID, columnID).Scan(&projectID, &fromID, &fromTitle, &fromKind, &toTitle, &toKind, &role, &assigned)
	if errors.Is(err, sql.ErrNoRows) {
		return repositorycommon.ErrNotFound
	}
	if err != nil {
		return err
	}
	if err := repositorycommon.RequireMember(ctx, tx, userID, projectID); err != nil {
		return err
	}
	if !allowedMove(role, assigned, fromKind, toKind, comment) {
		return repositorycommon.ErrForbidden
	}
	if _, err := tx.ExecContext(ctx, `
		UPDATE tasks
		SET column_id = $1,
			completed_at = CASE WHEN $2 = 'DONE' THEN COALESCE(completed_at, CURRENT_TIMESTAMP) ELSE NULL END,
			updated_at = CURRENT_TIMESTAMP
		WHERE task_id = $3
	`, columnID, toKind, taskID); err != nil {
		return err
	}
	action := "MOVED"
	if toKind == "REVIEW" {
		action = "SUBMITTED"
	} else if toKind == "DONE" {
		action = "ACCEPTED"
	} else if (fromKind == "REVIEW" || fromKind == "DONE") && (toKind == "BACKLOG" || toKind == "IN_PROGRESS") {
		action = "RETURNED"
	}
	if err := insertHistory(ctx, tx, taskID, userID, action, fromTitle, toTitle, comment); err != nil {
		return err
	}
	if action == "RETURNED" {
		if _, err := tx.ExecContext(ctx, `
			INSERT INTO comments (comment_id, task_id, author_user_id, content)
			VALUES (gen_random_uuid(), $1, $2, 'Замечания: ' || $3)
		`, taskID, userID, comment); err != nil {
			return err
		}
	}
	if err := insertTaskNotifications(ctx, tx, taskID, userID, action, "Статус задачи изменён"); err != nil {
		return err
	}
	return tx.Commit()
}

func allowedMove(role string, assigned bool, fromKind, toKind, comment string) bool {
	if toKind == "REVIEW" {
		return assigned
	}
	if fromKind == "BACKLOG" && toKind == "IN_PROGRESS" {
		return assigned
	}
	if fromKind == "IN_PROGRESS" && toKind == "IN_PROGRESS" {
		return assigned || role == "CAPTAIN"
	}
	if fromKind == "REVIEW" && toKind == "DONE" {
		return role == "CAPTAIN"
	}
	if (fromKind == "REVIEW" || fromKind == "DONE") && (toKind == "BACKLOG" || toKind == "IN_PROGRESS") {
		return role == "CAPTAIN" && comment != ""
	}
	return role == "CAPTAIN"
}

func (r *Repository) ListCompleted(ctx context.Context, userID string, from, to *time.Time, projectID, assigneeID, search string) ([]models.Task, error) {
	if _, err := r.GetUserActive(ctx, userID); err != nil {
		return nil, err
	}
	return r.queryTasks(ctx, `
		bc.kind = 'DONE'
		AND ($1::timestamptz IS NULL OR t.completed_at >= $1)
		AND ($2::timestamptz IS NULL OR t.completed_at < $2)
		AND ($3 = '' OR b.project_id::text = $3)
		AND ($4 = '' OR EXISTS(SELECT 1 FROM task_assignees ta WHERE ta.task_id = t.task_id AND ta.user_id::text = $4))
		AND ($5 = '' OR t.title ILIKE '%' || $5 || '%' OR COALESCE(t.description, '') ILIKE '%' || $5 || '%')
	`, from, to, projectID, assigneeID, search)
}

func (r *Repository) ListAssigned(ctx context.Context, userID, requestedUserID string) ([]models.Task, error) {
	if _, err := r.GetUserActive(ctx, userID); err != nil {
		return nil, err
	}
	return r.queryTasks(ctx, `
		bc.kind <> 'DONE'
		AND EXISTS(SELECT 1 FROM task_assignees ta WHERE ta.task_id = t.task_id AND ta.user_id = $1)
	`, requestedUserID)
}

func (r *Repository) GetUserActive(ctx context.Context, userID string) (bool, error) {
	var active bool
	err := r.db.QueryRowContext(ctx, `SELECT is_active FROM users WHERE user_id = $1`, userID).Scan(&active)
	return active, err
}

func assign(ctx context.Context, tx *sql.Tx, taskID, projectID, userID string) error {
	result, err := tx.ExecContext(ctx, `
		INSERT INTO task_assignees (task_id, user_id)
		SELECT $1, $2
		WHERE EXISTS(
			SELECT 1 FROM project_members
			WHERE project_id = $3 AND user_id = $2 AND status = 'ACTIVE'
		)
		ON CONFLICT DO NOTHING
	`, taskID, userID, projectID)
	if err != nil {
		return err
	}
	count, err := result.RowsAffected()
	if err != nil {
		return err
	}
	if count == 0 {
		return repositorycommon.ErrConflict
	}
	return nil
}

func insertHistory(ctx context.Context, tx *sql.Tx, taskID, actorID, action, fromColumn, toColumn, comment string) error {
	_, err := tx.ExecContext(ctx, `
		INSERT INTO task_history (
			task_history_id, task_id, actor_user_id, action,
			from_status, to_status, comment
		)
		VALUES (gen_random_uuid(), $1, $2, $3, NULLIF($4, ''), NULLIF($5, ''), NULLIF($6, ''))
	`, taskID, actorID, action, fromColumn, toColumn, comment)
	return err
}

func insertTaskNotifications(ctx context.Context, tx *sql.Tx, taskID, actorID, action, text string) error {
	kind := "STATUS"
	title := "Статус задачи изменён"
	if action == "RETURNED" {
		kind = "RETURNED"
		title = "Задача возвращена на доработку"
		text = "Задача возвращена на доработку"
	}
	_, err := tx.ExecContext(ctx, `
		INSERT INTO notifications (notification_id, user_id, kind, title, text, task_id)
		SELECT
			gen_random_uuid(),
			recipient.user_id,
			$4,
			$5,
			$6,
			$1
		FROM (
			SELECT created_by_user_id AS user_id
			FROM tasks
			WHERE task_id = $1 AND $3 <> 'RETURNED'
			UNION
			SELECT user_id
			FROM task_assignees
			WHERE task_id = $1 AND $3 <> 'SUBMITTED'
			UNION
			SELECT user_id
			FROM users
			WHERE system_role = 'CAPTAIN' AND is_active = TRUE AND $3 = 'SUBMITTED'
		) recipient
		WHERE recipient.user_id <> $2
	`, taskID, actorID, action, kind, title, text)

	return err
}

func (r *Repository) comments(ctx context.Context, taskID string) ([]models.Comment, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT c.comment_id, c.task_id, c.author_user_id, COALESCE(u.display_name, 'Удалённый участник'), c.content, c.created_at, c.updated_at
		FROM comments c LEFT JOIN users u ON u.user_id = c.author_user_id
		WHERE c.task_id = $1 ORDER BY c.created_at
	`, taskID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := make([]models.Comment, 0)
	for rows.Next() {
		var item models.Comment
		if err := rows.Scan(&item.ID, &item.TaskID, &item.AuthorID, &item.AuthorName, &item.Text, &item.CreatedAt, &item.UpdatedAt); err != nil {
			return nil, err
		}
		items = append(items, item)
	}
	return items, rows.Err()
}

func (r *Repository) attachments(ctx context.Context, taskID string) ([]models.Attachment, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT attachment_id, task_id, uploaded_by_user_id, attachment_type, file_name, storage_key, mime_type, file_size, created_at
		FROM attachments WHERE task_id = $1 ORDER BY created_at
	`, taskID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := make([]models.Attachment, 0)
	for rows.Next() {
		var item models.Attachment
		if err := rows.Scan(&item.ID, &item.TaskID, &item.UploadedBy, &item.Kind, &item.File.Name, &item.StorageKey, &item.File.MimeType, &item.File.SizeBytes, &item.UploadedAt); err != nil {
			return nil, err
		}
		item.File.ID = item.ID
		item.File.DownloadURL = "/api/v1/files/" + item.ID
		items = append(items, item)
	}
	return items, rows.Err()
}

func (r *Repository) history(ctx context.Context, taskID string) ([]models.TaskHistory, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT task_history_id, action, actor_user_id, occurred_at, COALESCE(from_status, ''), COALESCE(to_status, ''), COALESCE(comment, '')
		FROM task_history WHERE task_id = $1 ORDER BY occurred_at
	`, taskID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := make([]models.TaskHistory, 0)
	for rows.Next() {
		var item models.TaskHistory
		if err := rows.Scan(&item.ID, &item.Action, &item.ActorID, &item.At, &item.FromColumn, &item.ToColumn, &item.Comment); err != nil {
			return nil, err
		}
		items = append(items, item)
	}
	return items, rows.Err()
}
