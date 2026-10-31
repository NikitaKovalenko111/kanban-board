package task_repo

import (
	"context"
	"database/sql"
	"errors"
	"kanban-board/internal/models"
	repositorycommon "kanban-board/internal/storage/repositories/common"
	"time"
)

type Repository struct {
	db *sql.DB
}

func Init(db *sql.DB) *Repository {
	return &Repository{
		db: db,
	}
}

func (r *Repository) listTasks(ctx context.Context, projectID string) ([]models.Task, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT
			t.task_id,
			b.project_id,
			t.column_id,
			bc.code,
			t.created_by_user_id,
			t.title,
			COALESCE(t.description, ''),
			t.priority,
			t.position,
			t.due_date,
			t.completed_at,
			t.created_at,
			t.updated_at
		FROM tasks t
		JOIN board_columns bc ON bc.column_id = t.column_id
		JOIN boards b ON b.board_id = bc.board_id
		WHERE b.project_id = $1
		ORDER BY bc.position, t.position
	`, projectID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	tasks := make([]models.Task, 0)
	for rows.Next() {
		var t models.Task
		if err := rows.Scan(&t.ID, &t.ProjectID, &t.ColumnID, &t.Status, &t.CreatedBy, &t.Title, &t.Description, &t.Priority, &t.Position, &t.DueDate, &t.CompletedAt, &t.CreatedAt, &t.UpdatedAt); err != nil {
			return nil, err
		}
		ids, err := r.taskAssignees(ctx, t.ID)
		if err != nil {
			return nil, err
		}
		t.AssigneeIDs = ids
		tasks = append(tasks, t)
	}
	return tasks, rows.Err()
}

func (r *Repository) taskAssignees(ctx context.Context, taskID string) ([]string, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT
		    user_id
		FROM task_assignees
		WHERE task_id=$1
		ORDER BY user_id
	`, taskID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	ids := make([]string, 0)
	for rows.Next() {
		var id string
		if err := rows.Scan(&id); err != nil {
			return nil, err
		}
		ids = append(ids, id)
	}
	return ids, rows.Err()
}

func (r *Repository) ListTasks(ctx context.Context, userID, projectID string) ([]models.Task, error) {
	if err := repositorycommon.RequireMember(ctx, r.db, userID, projectID); err != nil {
		return nil, err
	}
	return r.listTasks(ctx, projectID)
}

func (r *Repository) GetTask(ctx context.Context, userID, taskID string) (*models.Task, error) {
	var t models.Task
	err := r.db.QueryRowContext(ctx, `
		SELECT
			t.task_id,
			b.project_id,
			t.column_id,
			bc.code,
			t.created_by_user_id,
			t.title,
			COALESCE(t.description, ''),
			t.priority,
			t.position,
			t.due_date,
			t.completed_at,
			t.created_at,
			t.updated_at
		FROM tasks t
		JOIN board_columns bc ON bc.column_id = t.column_id
		JOIN boards b ON b.board_id = bc.board_id
		WHERE t.task_id = $1
	`, taskID).Scan(&t.ID, &t.ProjectID, &t.ColumnID, &t.Status, &t.CreatedBy, &t.Title, &t.Description, &t.Priority, &t.Position, &t.DueDate, &t.CompletedAt, &t.CreatedAt, &t.UpdatedAt)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, repositorycommon.ErrNotFound
	} else if err != nil {
		return nil, err
	}
	if err := repositorycommon.RequireMember(ctx, r.db, userID, t.ProjectID); err != nil {
		return nil, err
	}
	t.AssigneeIDs, err = r.taskAssignees(ctx, t.ID)
	return &t, err
}

func (r *Repository) CreateTask(ctx context.Context, userID, projectID, title, description, priority string, dueDate *time.Time, assigneeIDs []string) (string, error) {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return "", err
	}
	defer tx.Rollback()
	if err := repositorycommon.RequireLead(ctx, tx, userID, projectID); err != nil {
		return "", err
	}
	var taskID, columnID string
	var position int
	if err := tx.QueryRowContext(ctx, `
		SELECT
		    bc.column_id
		FROM board_columns bc
		JOIN boards b ON b.board_id=bc.board_id
		WHERE b.project_id=$1 AND bc.code='WAITING'
	`, projectID).Scan(&columnID); err != nil {
		return "", err
	}
	if err := tx.QueryRowContext(ctx, `
		SELECT
		    COALESCE(MAX(position)+1,0)
		FROM tasks
		WHERE column_id=$1
	`, columnID).Scan(&position); err != nil {
		return "", err
	}
	if err := tx.QueryRowContext(ctx, `
		INSERT INTO tasks (
		    task_id,
		    column_id,
		    created_by_user_id,
		    title,
		    description,
		    priority,
		    position,
		    due_date
		)
		VALUES
		    (gen_random_uuid(),$1,$2,$3,NULLIF($4,''),$5,$6,$7)
		RETURNING task_id
	`, columnID, userID, title, description, priority, position, dueDate).Scan(&taskID); err != nil {
		return "", err
	}
	for _, assigneeID := range assigneeIDs {
		if _, err := tx.ExecContext(ctx, `
			INSERT INTO task_assignees (
				task_id,
				user_id
			)
			SELECT
				$1,
				$2
			WHERE EXISTS(
				SELECT 1
				FROM project_members
				WHERE project_id = $3
					AND user_id = $2
					AND status = 'ACTIVE'
			)
	`, taskID, assigneeID, projectID); err != nil {
			return "", err
		}
	}
	if _, err := tx.ExecContext(ctx, `
		INSERT INTO task_history (
		    task_history_id,
		    task_id,
		    actor_user_id,
		    action,
		    to_status
		)
		VALUES
		    (gen_random_uuid(),$1,$2,'CREATED','WAITING')
	`, taskID, userID); err != nil {
		return "", err
	}
	if err := tx.Commit(); err != nil {
		return "", err
	}
	return taskID, nil
}

func (r *Repository) TakeTask(ctx context.Context, userID, taskID string) error {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	var projectID, status string
	if err := tx.QueryRowContext(ctx, `
		SELECT
		    b.project_id,
		    bc.code
		FROM tasks t
		JOIN board_columns bc ON bc.column_id=t.column_id
		JOIN boards b ON b.board_id=bc.board_id
		WHERE t.task_id=$1
	`, taskID).Scan(&projectID, &status); errors.Is(err, sql.ErrNoRows) {
		return repositorycommon.ErrNotFound
	} else if err != nil {
		return err
	}
	if err := repositorycommon.RequireMember(ctx, tx, userID, projectID); err != nil {
		return err
	}
	var assigned int
	if err := tx.QueryRowContext(ctx, `
		SELECT
		    COUNT(*)
		FROM task_assignees
		WHERE task_id=$1
	`, taskID).Scan(&assigned); err != nil {
		return err
	}
	if assigned > 0 {
		return repositorycommon.ErrConflict
	}
	if _, err := tx.ExecContext(ctx, `
		INSERT INTO task_assignees (
		    task_id,
		    user_id
		)
		VALUES
		    ($1,$2)
	`, taskID, userID); err != nil {
		return err
	}
	if _, err := tx.ExecContext(ctx, `
		INSERT INTO task_history (
		    task_history_id,
		    task_id,
		    actor_user_id,
		    action,
		    from_status,
		    to_status
		)
		VALUES
		    (gen_random_uuid(),$1,$2,'TAKEN',$3,$3)
	`, taskID, userID, status); err != nil {
		return err
	}
	return tx.Commit()
}

func (r *Repository) MoveTask(ctx context.Context, userID, taskID, toStatus, comment string) error {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	var projectID, fromStatus, role string
	var assigned bool
	err = tx.QueryRowContext(ctx, `
		SELECT
			b.project_id,
			bc.code,
			pm.role,
			EXISTS(
				SELECT 1
				FROM task_assignees ta
				WHERE ta.task_id = t.task_id
					AND ta.user_id = $2
			)
		FROM tasks t
		JOIN board_columns bc ON bc.column_id = t.column_id
		JOIN boards b ON b.board_id = bc.board_id
		JOIN project_members pm ON pm.project_id = b.project_id
			AND pm.user_id = $2
			AND pm.status = 'ACTIVE'
		WHERE t.task_id = $1
		FOR UPDATE
	`, taskID, userID).Scan(&projectID, &fromStatus, &role, &assigned)
	if errors.Is(err, sql.ErrNoRows) {
		return repositorycommon.ErrForbidden
	} else if err != nil {
		return err
	}
	if !allowedTransition(role, assigned, fromStatus, toStatus, comment) {
		return repositorycommon.ErrForbidden
	}
	var columnID string
	if err := tx.QueryRowContext(ctx, `
		SELECT
		    bc.column_id
		FROM board_columns bc
		JOIN boards b ON b.board_id=bc.board_id
		WHERE b.project_id=$1 AND bc.code=$2
	`, projectID, toStatus).Scan(&columnID); err != nil {
		return repositorycommon.ErrNotFound
	}
	if _, err := tx.ExecContext(ctx, `
		UPDATE tasks
		SET
		    column_id=$1,
		    completed_at=CASE WHEN $2='DONE' THEN CURRENT_TIMESTAMP ELSE NULL END,
		    updated_at=CURRENT_TIMESTAMP
		WHERE task_id=$3
	`, columnID, toStatus, taskID); err != nil {
		return err
	}
	if _, err := tx.ExecContext(ctx, `
		INSERT INTO task_history (
		    task_history_id,
		    task_id,
		    actor_user_id,
		    action,
		    from_status,
		    to_status,
		    comment
		)
		VALUES
		    (gen_random_uuid(),$1,$2,'STATUS_CHANGED',$3,$4,NULLIF($5,''))
	`, taskID, userID, fromStatus, toStatus, comment); err != nil {
		return err
	}
	return tx.Commit()
}

func allowedTransition(role string, assigned bool, from, to, comment string) bool {
	if assigned && ((from == "WAITING" && to == "WORKING") || (from == "WORKING" && to == "CHECKING")) {
		return true
	}
	if role == "LEAD" && from == "CHECKING" && to == "DONE" {
		return true
	}
	if role == "LEAD" && ((from == "CHECKING" || from == "DONE") && to == "WORKING") {
		return comment != ""
	}
	return false
}

func (r *Repository) UpdateTask(ctx context.Context, userID, taskID, title, description, priority string, dueDate *time.Time) error {
	task, err := r.GetTask(ctx, userID, taskID)
	if err != nil {
		return err
	}
	if err := repositorycommon.RequireLead(ctx, r.db, userID, task.ProjectID); err != nil {
		return err
	}
	result, err := r.db.ExecContext(ctx, `
		UPDATE tasks
		SET
		    title=$1,
		    description=NULLIF($2,''),
		    priority=$3,
		    due_date=$4,
		    updated_at=CURRENT_TIMESTAMP
		WHERE task_id=$5
	`, title, description, priority, dueDate, taskID)
	if err != nil {
		return err
	}
	return repositorycommon.RequireAffected(result)
}

func (r *Repository) DeleteTask(ctx context.Context, userID, taskID string) error {
	task, err := r.GetTask(ctx, userID, taskID)
	if err != nil {
		return err
	}
	if err := repositorycommon.RequireLead(ctx, r.db, userID, task.ProjectID); err != nil {
		return err
	}
	result, err := r.db.ExecContext(ctx, `
		DELETE FROM tasks
		WHERE task_id=$1
	`, taskID)
	if err != nil {
		return err
	}
	return repositorycommon.RequireAffected(result)
}

func (r *Repository) TaskHistory(ctx context.Context, userID, taskID string) ([]models.TaskHistory, error) {
	if _, err := r.GetTask(ctx, userID, taskID); err != nil {
		return nil, err
	}
	rows, err := r.db.QueryContext(ctx, `
		SELECT
		    h.task_history_id,
		    h.action,
		    h.actor_user_id,
		    COALESCE(u.display_name,''),
		    COALESCE(h.from_status,''),
		    COALESCE(h.to_status,''),
		    COALESCE(h.comment,''),
		    h.occurred_at
		FROM task_history h
		LEFT JOIN users u ON u.user_id=h.actor_user_id
		WHERE h.task_id=$1
		ORDER BY h.occurred_at
	`, taskID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := make([]models.TaskHistory, 0)
	for rows.Next() {
		var h models.TaskHistory
		if err := rows.Scan(&h.ID, &h.Action, &h.ActorID, &h.ActorName, &h.FromStatus, &h.ToStatus, &h.Comment, &h.OccurredAt); err != nil {
			return nil, err
		}
		items = append(items, h)
	}
	return items, rows.Err()
}

func (r *Repository) AddTaskAssignee(ctx context.Context, actorID, taskID, userID string) error {
	task, err := r.GetTask(ctx, actorID, taskID)
	if err != nil {
		return err
	}
	if err := repositorycommon.RequireLead(ctx, r.db, actorID, task.ProjectID); err != nil {
		return err
	}
	_, err = r.db.ExecContext(ctx, `
		INSERT INTO task_assignees (
			task_id,
			user_id
		)
		SELECT
			$1,
			$2
		WHERE EXISTS(
			SELECT 1
			FROM project_members
			WHERE project_id = $3
				AND user_id = $2
				AND status = 'ACTIVE'
		)
		ON CONFLICT DO NOTHING
	`, taskID, userID, task.ProjectID)
	return err
}

func (r *Repository) RemoveTaskAssignee(ctx context.Context, actorID, taskID, userID string) error {
	task, err := r.GetTask(ctx, actorID, taskID)
	if err != nil {
		return err
	}
	if err := repositorycommon.RequireLead(ctx, r.db, actorID, task.ProjectID); err != nil {
		return err
	}
	result, err := r.db.ExecContext(ctx, `
		DELETE FROM task_assignees
		WHERE task_id=$1 AND user_id=$2
	`, taskID, userID)
	if err != nil {
		return err
	}
	return repositorycommon.RequireAffected(result)
}
