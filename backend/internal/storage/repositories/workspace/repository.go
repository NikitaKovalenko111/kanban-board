package workspace_repo

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"time"

	"kanban-board/internal/models"
)

var (
	ErrNotFound  = errors.New("resource not found")
	ErrForbidden = errors.New("operation is forbidden")
	ErrConflict  = errors.New("resource conflict")
)

type Repository struct{ db *sql.DB }

func Init(db *sql.DB) *Repository { return &Repository{db: db} }

type Membership struct {
	ProjectID string    `json:"projectId"`
	UserID    string    `json:"userId"`
	Role      string    `json:"role"`
	Status    string    `json:"status"`
	JoinedAt  time.Time `json:"joinedAt"`
}

type Profile struct {
	ID           string       `json:"id"`
	Username     string       `json:"username"`
	Email        string       `json:"email"`
	DisplayName  string       `json:"displayName"`
	Position     string       `json:"position"`
	StudentGroup string       `json:"studentGroup,omitempty"`
	ImageURL     string       `json:"imageUrl,omitempty"`
	Active       bool         `json:"active"`
	Memberships  []Membership `json:"memberships"`
}

type Project struct {
	ID          string    `json:"id"`
	CreatedBy   string    `json:"createdByUserId"`
	Name        string    `json:"name"`
	Description string    `json:"description"`
	JoinPolicy  string    `json:"joinPolicy"`
	Archived    bool      `json:"archived"`
	IsMember    bool      `json:"isMember"`
	CreatedAt   time.Time `json:"createdAt"`
	UpdatedAt   time.Time `json:"updatedAt"`
}

type ProjectMember struct {
	UserID      string    `json:"userId"`
	DisplayName string    `json:"displayName"`
	Position    string    `json:"position"`
	Email       string    `json:"contact"`
	Role        string    `json:"role"`
	Status      string    `json:"status"`
	JoinedAt    time.Time `json:"joinedAt"`
}

type Column struct {
	ID       string `json:"id"`
	Code     string `json:"code"`
	Name     string `json:"name"`
	Position int    `json:"position"`
}

type Task struct {
	ID          string     `json:"id"`
	ProjectID   string     `json:"projectId"`
	ColumnID    string     `json:"boardColumnId"`
	Status      string     `json:"status"`
	CreatedBy   string     `json:"createdByUserId"`
	Title       string     `json:"title"`
	Description string     `json:"description"`
	Priority    string     `json:"priority"`
	Position    int        `json:"position"`
	DueDate     *time.Time `json:"dueDate,omitempty"`
	CompletedAt *time.Time `json:"completedAt,omitempty"`
	AssigneeIDs []string   `json:"assigneeIds"`
	CreatedAt   time.Time  `json:"createdAt"`
	UpdatedAt   time.Time  `json:"updatedAt"`
}

type Board struct {
	ID        string   `json:"boardId"`
	ProjectID string   `json:"projectId"`
	Name      string   `json:"name"`
	Columns   []Column `json:"columns"`
	Tasks     []Task   `json:"tasks"`
}

type History struct {
	ID         string    `json:"id"`
	Action     string    `json:"action"`
	ActorID    *string   `json:"actorId,omitempty"`
	ActorName  string    `json:"actorName"`
	FromStatus string    `json:"fromStatus,omitempty"`
	ToStatus   string    `json:"toStatus,omitempty"`
	Comment    string    `json:"comment,omitempty"`
	OccurredAt time.Time `json:"occurredAt"`
}

type Comment struct {
	ID         string     `json:"id"`
	TaskID     string     `json:"taskId"`
	AuthorID   string     `json:"authorId"`
	AuthorName string     `json:"authorName"`
	Text       string     `json:"text"`
	CreatedAt  time.Time  `json:"createdAt"`
	UpdatedAt  *time.Time `json:"updatedAt,omitempty"`
}

type Attachment struct {
	ID         string    `json:"id"`
	TaskID     string    `json:"taskId"`
	UploadedBy string    `json:"uploadedByUserId"`
	Type       string    `json:"attachmentType"`
	FileName   string    `json:"fileName"`
	StorageKey string    `json:"-"`
	MimeType   string    `json:"mimeType"`
	FileSize   int64     `json:"fileSize"`
	CreatedAt  time.Time `json:"createdAt"`
}

type EventType struct {
	ID                     string    `json:"id"`
	Code                   string    `json:"code,omitempty"`
	Title                  string    `json:"title"`
	Color                  string    `json:"color"`
	Scope                  string    `json:"scope"`
	CreatedByUserID        *string   `json:"createdByUserId,omitempty"`
	ProjectID              *string   `json:"projectId,omitempty"`
	DefaultReminderMinutes []int     `json:"defaultReminderMinutes"`
	CreatedAt              time.Time `json:"createdAt"`
	UpdatedAt              time.Time `json:"updatedAt"`
}

type Event struct {
	ID              string    `json:"id"`
	OwnerUserID     string    `json:"ownerId"`
	EventTypeID     string    `json:"eventTypeId"`
	ProjectID       *string   `json:"projectId,omitempty"`
	StudentGroupID  *string   `json:"studentGroupId,omitempty"`
	StudentGroup    string    `json:"studentGroup,omitempty"`
	Title           string    `json:"title"`
	Description     string    `json:"description"`
	Location        string    `json:"location"`
	Visibility      string    `json:"visibility"`
	StartsAt        time.Time `json:"startsAt"`
	EndsAt          time.Time `json:"endsAt"`
	ReminderMinutes []int     `json:"reminderMinutes"`
	CreatedAt       time.Time `json:"createdAt"`
	UpdatedAt       time.Time `json:"updatedAt"`
}

func (r *Repository) GetProfile(ctx context.Context, userID string) (*Profile, error) {
	var profile Profile
	err := r.db.QueryRowContext(ctx, `
		SELECT u.user_id, u.username, u.email, u.display_name, COALESCE(u.position, ''),
			COALESCE(sg.group_code, ''), COALESCE(u.image_url, ''), u.is_active
		FROM users u LEFT JOIN student_groups sg ON sg.student_group_id = u.student_group_id
		WHERE u.user_id = $1
	`, userID).Scan(&profile.ID, &profile.Username, &profile.Email, &profile.DisplayName,
		&profile.Position, &profile.StudentGroup, &profile.ImageURL, &profile.Active)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrNotFound
	}
	if err != nil {
		return nil, fmt.Errorf("get profile: %w", err)
	}
	rows, err := r.db.QueryContext(ctx, `
		SELECT project_id, user_id, role, status, joined_at
		FROM project_members WHERE user_id = $1 AND status <> 'LEFT' ORDER BY joined_at
	`, userID)
	if err != nil {
		return nil, fmt.Errorf("get memberships: %w", err)
	}
	defer rows.Close()
	profile.Memberships = make([]Membership, 0)
	for rows.Next() {
		var membership Membership
		if err := rows.Scan(&membership.ProjectID, &membership.UserID, &membership.Role, &membership.Status, &membership.JoinedAt); err != nil {
			return nil, fmt.Errorf("scan membership: %w", err)
		}
		profile.Memberships = append(profile.Memberships, membership)
	}
	return &profile, rows.Err()
}

func (r *Repository) UpdateProfile(ctx context.Context, userID, displayName, position, groupCode, imageURL string) error {
	result, err := r.db.ExecContext(ctx, `
		WITH selected_group AS (
			INSERT INTO student_groups (student_group_id, group_code, name)
			SELECT gen_random_uuid(), NULLIF($3, ''), NULLIF($3, '') WHERE NULLIF($3, '') IS NOT NULL
			ON CONFLICT (group_code) DO UPDATE SET updated_at = CURRENT_TIMESTAMP
			RETURNING student_group_id
		)
		UPDATE users SET display_name = $1, position = NULLIF($2, ''),
			student_group_id = (SELECT student_group_id FROM selected_group), image_url = NULLIF($4, ''),
			updated_at = CURRENT_TIMESTAMP WHERE user_id = $5
	`, displayName, position, groupCode, imageURL, userID)
	if err != nil {
		return fmt.Errorf("update profile: %w", err)
	}
	return requireAffected(result)
}

func (r *Repository) ListProjects(ctx context.Context, userID string) ([]Project, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT p.project_id, COALESCE(p.created_by_user_id::text, ''), p.name, COALESCE(p.description, ''), p.join_policy,
			p.is_archived, EXISTS(SELECT 1 FROM project_members pm WHERE pm.project_id = p.project_id AND pm.user_id = $1 AND pm.status = 'ACTIVE'),
			p.created_at, p.updated_at
		FROM projects p
		WHERE p.is_archived = FALSE AND (p.join_policy = 'OPEN' OR EXISTS(
			SELECT 1 FROM project_members pm WHERE pm.project_id = p.project_id AND pm.user_id = $1 AND pm.status <> 'LEFT'
		)) ORDER BY p.created_at
	`, userID)
	if err != nil {
		return nil, fmt.Errorf("list projects: %w", err)
	}
	defer rows.Close()
	projects := make([]Project, 0)
	for rows.Next() {
		var project Project
		if err := rows.Scan(&project.ID, &project.CreatedBy, &project.Name, &project.Description, &project.JoinPolicy,
			&project.Archived, &project.IsMember, &project.CreatedAt, &project.UpdatedAt); err != nil {
			return nil, err
		}
		projects = append(projects, project)
	}
	return projects, rows.Err()
}

func (r *Repository) GetProject(ctx context.Context, userID, projectID string) (*Project, error) {
	var project Project
	err := r.db.QueryRowContext(ctx, `
		SELECT p.project_id, COALESCE(p.created_by_user_id::text, ''), p.name, COALESCE(p.description, ''), p.join_policy,
			p.is_archived, EXISTS(SELECT 1 FROM project_members pm WHERE pm.project_id = p.project_id AND pm.user_id = $1 AND pm.status = 'ACTIVE'),
			p.created_at, p.updated_at
		FROM projects p WHERE p.project_id = $2 AND (p.join_policy = 'OPEN' OR EXISTS(
			SELECT 1 FROM project_members pm WHERE pm.project_id = p.project_id AND pm.user_id = $1 AND pm.status <> 'LEFT'))
	`, userID, projectID).Scan(&project.ID, &project.CreatedBy, &project.Name, &project.Description, &project.JoinPolicy,
		&project.Archived, &project.IsMember, &project.CreatedAt, &project.UpdatedAt)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrNotFound
	}
	if err != nil {
		return nil, fmt.Errorf("get project: %w", err)
	}
	return &project, nil
}

func (r *Repository) CreateProject(ctx context.Context, userID, name, description, joinPolicy string) (string, error) {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return "", err
	}
	defer tx.Rollback()
	var projectID, boardID string
	if err := tx.QueryRowContext(ctx, `
		INSERT INTO projects (project_id, created_by_user_id, name, description, join_policy)
		VALUES (gen_random_uuid(), $1, $2, NULLIF($3, ''), $4) RETURNING project_id
	`, userID, name, description, joinPolicy).Scan(&projectID); err != nil {
		return "", fmt.Errorf("create project: %w", err)
	}
	if _, err := tx.ExecContext(ctx, `INSERT INTO project_members (project_id, user_id, role) VALUES ($1, $2, 'LEAD')`, projectID, userID); err != nil {
		return "", err
	}
	if err := tx.QueryRowContext(ctx, `INSERT INTO boards (board_id, project_id, name) VALUES (gen_random_uuid(), $1, 'Kanban') RETURNING board_id`, projectID).Scan(&boardID); err != nil {
		return "", err
	}
	if _, err := tx.ExecContext(ctx, `
		INSERT INTO board_columns (column_id, board_id, code, name, position) VALUES
			(gen_random_uuid(), $1, 'WAITING', 'Ожидает', 0),
			(gen_random_uuid(), $1, 'WORKING', 'В работе', 1),
			(gen_random_uuid(), $1, 'CHECKING', 'На проверке', 2),
			(gen_random_uuid(), $1, 'DONE', 'Готово', 3)
	`, boardID); err != nil {
		return "", err
	}
	if err := tx.Commit(); err != nil {
		return "", err
	}
	return projectID, nil
}

func (r *Repository) UpdateProject(ctx context.Context, userID, projectID, name, description, joinPolicy string, archived bool) error {
	if err := r.requireLead(ctx, r.db, userID, projectID); err != nil {
		return err
	}
	result, err := r.db.ExecContext(ctx, `UPDATE projects SET name=$1, description=NULLIF($2,''), join_policy=$3, is_archived=$4, updated_at=CURRENT_TIMESTAMP WHERE project_id=$5`, name, description, joinPolicy, archived, projectID)
	if err != nil {
		return fmt.Errorf("update project: %w", err)
	}
	return requireAffected(result)
}

func (r *Repository) DeleteProject(ctx context.Context, userID, projectID string) error {
	if err := r.requireLead(ctx, r.db, userID, projectID); err != nil {
		return err
	}
	result, err := r.db.ExecContext(ctx, `DELETE FROM projects WHERE project_id=$1`, projectID)
	if err != nil {
		return fmt.Errorf("delete project: %w", err)
	}
	return requireAffected(result)
}

func (r *Repository) ListProjectMembers(ctx context.Context, userID, projectID string) ([]ProjectMember, error) {
	if err := r.requireMember(ctx, r.db, userID, projectID); err != nil {
		return nil, err
	}
	rows, err := r.db.QueryContext(ctx, `
		SELECT u.user_id, u.display_name, COALESCE(u.position,''), u.email, pm.role, pm.status, pm.joined_at
		FROM project_members pm JOIN users u ON u.user_id=pm.user_id
		WHERE pm.project_id=$1 AND pm.status <> 'LEFT' ORDER BY pm.joined_at
	`, projectID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	members := make([]ProjectMember, 0)
	for rows.Next() {
		var m ProjectMember
		if err := rows.Scan(&m.UserID, &m.DisplayName, &m.Position, &m.Email, &m.Role, &m.Status, &m.JoinedAt); err != nil {
			return nil, err
		}
		members = append(members, m)
	}
	return members, rows.Err()
}

func (r *Repository) AddProjectMember(ctx context.Context, actorID, projectID, userID, role string) error {
	if err := r.requireLead(ctx, r.db, actorID, projectID); err != nil {
		return err
	}
	_, err := r.db.ExecContext(ctx, `
		INSERT INTO project_members (project_id,user_id,role,status) VALUES ($1,$2,$3,'ACTIVE')
		ON CONFLICT (project_id,user_id) DO UPDATE SET role=EXCLUDED.role,status='ACTIVE',updated_at=CURRENT_TIMESTAMP
	`, projectID, userID, role)
	return err
}

func (r *Repository) UpdateProjectMember(ctx context.Context, actorID, projectID, userID, role, status string) error {
	if err := r.requireLead(ctx, r.db, actorID, projectID); err != nil {
		return err
	}
	result, err := r.db.ExecContext(ctx, `UPDATE project_members SET role=$1,status=$2,updated_at=CURRENT_TIMESTAMP WHERE project_id=$3 AND user_id=$4`, role, status, projectID, userID)
	if err != nil {
		return err
	}
	return requireAffected(result)
}

func (r *Repository) RemoveProjectMember(ctx context.Context, actorID, projectID, userID string) error {
	if actorID != userID {
		if err := r.requireLead(ctx, r.db, actorID, projectID); err != nil {
			return err
		}
	}
	var role string
	if err := r.db.QueryRowContext(ctx, `SELECT role FROM project_members WHERE project_id=$1 AND user_id=$2`, projectID, userID).Scan(&role); err != nil {
		return ErrNotFound
	}
	if role == "LEAD" {
		var count int
		if err := r.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM project_members WHERE project_id=$1 AND role='LEAD' AND status='ACTIVE'`, projectID).Scan(&count); err != nil {
			return err
		}
		if count <= 1 {
			return ErrConflict
		}
	}
	result, err := r.db.ExecContext(ctx, `UPDATE project_members SET status='LEFT',updated_at=CURRENT_TIMESTAMP WHERE project_id=$1 AND user_id=$2`, projectID, userID)
	if err != nil {
		return err
	}
	return requireAffected(result)
}

func (r *Repository) JoinProject(ctx context.Context, userID, projectID string) error {
	var policy string
	if err := r.db.QueryRowContext(ctx, `SELECT join_policy FROM projects WHERE project_id=$1 AND is_archived=FALSE`, projectID).Scan(&policy); errors.Is(err, sql.ErrNoRows) {
		return ErrNotFound
	} else if err != nil {
		return err
	}
	if policy != "OPEN" {
		return ErrForbidden
	}
	_, err := r.db.ExecContext(ctx, `INSERT INTO project_members(project_id,user_id,role,status) VALUES($1,$2,'MEMBER','ACTIVE') ON CONFLICT(project_id,user_id) DO UPDATE SET status='ACTIVE',updated_at=CURRENT_TIMESTAMP`, projectID, userID)
	return err
}

func (r *Repository) GetBoard(ctx context.Context, userID, projectID string) (*Board, error) {
	if err := r.requireMember(ctx, r.db, userID, projectID); err != nil {
		return nil, err
	}
	var board Board
	if err := r.db.QueryRowContext(ctx, `SELECT board_id,project_id,name FROM boards WHERE project_id=$1`, projectID).Scan(&board.ID, &board.ProjectID, &board.Name); errors.Is(err, sql.ErrNoRows) {
		return nil, ErrNotFound
	} else if err != nil {
		return nil, err
	}
	rows, err := r.db.QueryContext(ctx, `SELECT column_id,code,name,position FROM board_columns WHERE board_id=$1 ORDER BY position`, board.ID)
	if err != nil {
		return nil, err
	}
	board.Columns = make([]Column, 0)
	for rows.Next() {
		var c Column
		if err := rows.Scan(&c.ID, &c.Code, &c.Name, &c.Position); err != nil {
			rows.Close()
			return nil, err
		}
		board.Columns = append(board.Columns, c)
	}
	rows.Close()
	board.Tasks, err = r.listTasks(ctx, projectID)
	if err != nil {
		return nil, err
	}
	return &board, nil
}

func (r *Repository) listTasks(ctx context.Context, projectID string) ([]Task, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT t.task_id,b.project_id,t.column_id,bc.code,t.created_by_user_id,t.title,COALESCE(t.description,''),t.priority,t.position,t.due_date,t.completed_at,t.created_at,t.updated_at
		FROM tasks t JOIN board_columns bc ON bc.column_id=t.column_id JOIN boards b ON b.board_id=bc.board_id
		WHERE b.project_id=$1 ORDER BY bc.position,t.position
	`, projectID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	tasks := make([]Task, 0)
	for rows.Next() {
		var t Task
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

func (r *Repository) ListTasks(ctx context.Context, userID, projectID string) ([]Task, error) {
	if err := r.requireMember(ctx, r.db, userID, projectID); err != nil {
		return nil, err
	}
	return r.listTasks(ctx, projectID)
}

func (r *Repository) GetTask(ctx context.Context, userID, taskID string) (*Task, error) {
	var t Task
	err := r.db.QueryRowContext(ctx, `
		SELECT t.task_id,b.project_id,t.column_id,bc.code,t.created_by_user_id,t.title,COALESCE(t.description,''),t.priority,t.position,t.due_date,t.completed_at,t.created_at,t.updated_at
		FROM tasks t JOIN board_columns bc ON bc.column_id=t.column_id JOIN boards b ON b.board_id=bc.board_id
		WHERE t.task_id=$1
	`, taskID).Scan(&t.ID, &t.ProjectID, &t.ColumnID, &t.Status, &t.CreatedBy, &t.Title, &t.Description, &t.Priority, &t.Position, &t.DueDate, &t.CompletedAt, &t.CreatedAt, &t.UpdatedAt)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrNotFound
	} else if err != nil {
		return nil, err
	}
	if err := r.requireMember(ctx, r.db, userID, t.ProjectID); err != nil {
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
	if err := r.requireLead(ctx, tx, userID, projectID); err != nil {
		return "", err
	}
	var taskID, columnID string
	var position int
	if err := tx.QueryRowContext(ctx, `SELECT bc.column_id FROM board_columns bc JOIN boards b ON b.board_id=bc.board_id WHERE b.project_id=$1 AND bc.code='WAITING'`, projectID).Scan(&columnID); err != nil {
		return "", err
	}
	if err := tx.QueryRowContext(ctx, `SELECT COALESCE(MAX(position)+1,0) FROM tasks WHERE column_id=$1`, columnID).Scan(&position); err != nil {
		return "", err
	}
	if err := tx.QueryRowContext(ctx, `INSERT INTO tasks(task_id,column_id,created_by_user_id,title,description,priority,position,due_date) VALUES(gen_random_uuid(),$1,$2,$3,NULLIF($4,''),$5,$6,$7) RETURNING task_id`, columnID, userID, title, description, priority, position, dueDate).Scan(&taskID); err != nil {
		return "", err
	}
	for _, assigneeID := range assigneeIDs {
		if _, err := tx.ExecContext(ctx, `INSERT INTO task_assignees(task_id,user_id) SELECT $1,$2 WHERE EXISTS(SELECT 1 FROM project_members WHERE project_id=$3 AND user_id=$2 AND status='ACTIVE')`, taskID, assigneeID, projectID); err != nil {
			return "", err
		}
	}
	if _, err := tx.ExecContext(ctx, `INSERT INTO task_history(task_history_id,task_id,actor_user_id,action,to_status) VALUES(gen_random_uuid(),$1,$2,'CREATED','WAITING')`, taskID, userID); err != nil {
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
	if err := tx.QueryRowContext(ctx, `SELECT b.project_id,bc.code FROM tasks t JOIN board_columns bc ON bc.column_id=t.column_id JOIN boards b ON b.board_id=bc.board_id WHERE t.task_id=$1 FOR UPDATE`, taskID).Scan(&projectID, &status); errors.Is(err, sql.ErrNoRows) {
		return ErrNotFound
	} else if err != nil {
		return err
	}
	if err := r.requireMember(ctx, tx, userID, projectID); err != nil {
		return err
	}
	var assigned int
	if err := tx.QueryRowContext(ctx, `SELECT COUNT(*) FROM task_assignees WHERE task_id=$1`, taskID).Scan(&assigned); err != nil {
		return err
	}
	if assigned > 0 {
		return ErrConflict
	}
	if _, err := tx.ExecContext(ctx, `INSERT INTO task_assignees(task_id,user_id) VALUES($1,$2)`, taskID, userID); err != nil {
		return err
	}
	if _, err := tx.ExecContext(ctx, `INSERT INTO task_history(task_history_id,task_id,actor_user_id,action,from_status,to_status) VALUES(gen_random_uuid(),$1,$2,'TAKEN',$3,$3)`, taskID, userID, status); err != nil {
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
		SELECT b.project_id,bc.code,pm.role,EXISTS(SELECT 1 FROM task_assignees ta WHERE ta.task_id=t.task_id AND ta.user_id=$2)
		FROM tasks t JOIN board_columns bc ON bc.column_id=t.column_id JOIN boards b ON b.board_id=bc.board_id
		JOIN project_members pm ON pm.project_id=b.project_id AND pm.user_id=$2 AND pm.status='ACTIVE'
		WHERE t.task_id=$1 FOR UPDATE
	`, taskID, userID).Scan(&projectID, &fromStatus, &role, &assigned)
	if errors.Is(err, sql.ErrNoRows) {
		return ErrForbidden
	} else if err != nil {
		return err
	}
	if !allowedTransition(role, assigned, fromStatus, toStatus, comment) {
		return ErrForbidden
	}
	var columnID string
	if err := tx.QueryRowContext(ctx, `SELECT bc.column_id FROM board_columns bc JOIN boards b ON b.board_id=bc.board_id WHERE b.project_id=$1 AND bc.code=$2`, projectID, toStatus).Scan(&columnID); err != nil {
		return ErrNotFound
	}
	if _, err := tx.ExecContext(ctx, `UPDATE tasks SET column_id=$1,completed_at=CASE WHEN $2='DONE' THEN CURRENT_TIMESTAMP ELSE NULL END,updated_at=CURRENT_TIMESTAMP WHERE task_id=$3`, columnID, toStatus, taskID); err != nil {
		return err
	}
	if _, err := tx.ExecContext(ctx, `INSERT INTO task_history(task_history_id,task_id,actor_user_id,action,from_status,to_status,comment) VALUES(gen_random_uuid(),$1,$2,'STATUS_CHANGED',$3,$4,NULLIF($5,''))`, taskID, userID, fromStatus, toStatus, comment); err != nil {
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
	if err := r.requireLead(ctx, r.db, userID, task.ProjectID); err != nil {
		return err
	}
	result, err := r.db.ExecContext(ctx, `UPDATE tasks SET title=$1,description=NULLIF($2,''),priority=$3,due_date=$4,updated_at=CURRENT_TIMESTAMP WHERE task_id=$5`, title, description, priority, dueDate, taskID)
	if err != nil {
		return err
	}
	return requireAffected(result)
}
func (r *Repository) DeleteTask(ctx context.Context, userID, taskID string) error {
	task, err := r.GetTask(ctx, userID, taskID)
	if err != nil {
		return err
	}
	if err := r.requireLead(ctx, r.db, userID, task.ProjectID); err != nil {
		return err
	}
	result, err := r.db.ExecContext(ctx, `DELETE FROM tasks WHERE task_id=$1`, taskID)
	if err != nil {
		return err
	}
	return requireAffected(result)
}

func (r *Repository) TaskHistory(ctx context.Context, userID, taskID string) ([]History, error) {
	if _, err := r.GetTask(ctx, userID, taskID); err != nil {
		return nil, err
	}
	rows, err := r.db.QueryContext(ctx, `SELECT h.task_history_id,h.action,h.actor_user_id,COALESCE(u.display_name,''),COALESCE(h.from_status,''),COALESCE(h.to_status,''),COALESCE(h.comment,''),h.occurred_at FROM task_history h LEFT JOIN users u ON u.user_id=h.actor_user_id WHERE h.task_id=$1 ORDER BY h.occurred_at`, taskID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := make([]History, 0)
	for rows.Next() {
		var h History
		if err := rows.Scan(&h.ID, &h.Action, &h.ActorID, &h.ActorName, &h.FromStatus, &h.ToStatus, &h.Comment, &h.OccurredAt); err != nil {
			return nil, err
		}
		items = append(items, h)
	}
	return items, rows.Err()
}

func (r *Repository) ListComments(ctx context.Context, userID, taskID string) ([]Comment, error) {
	if _, err := r.GetTask(ctx, userID, taskID); err != nil {
		return nil, err
	}
	rows, err := r.db.QueryContext(ctx, `SELECT c.comment_id,c.task_id,c.author_user_id,u.display_name,c.content,c.created_at,c.updated_at FROM comments c JOIN users u ON u.user_id=c.author_user_id WHERE c.task_id=$1 ORDER BY c.created_at`, taskID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := make([]Comment, 0)
	for rows.Next() {
		var c Comment
		if err := rows.Scan(&c.ID, &c.TaskID, &c.AuthorID, &c.AuthorName, &c.Text, &c.CreatedAt, &c.UpdatedAt); err != nil {
			return nil, err
		}
		items = append(items, c)
	}
	return items, rows.Err()
}
func (r *Repository) AddComment(ctx context.Context, userID, taskID, text string) (string, error) {
	if _, err := r.GetTask(ctx, userID, taskID); err != nil {
		return "", err
	}
	var id string
	err := r.db.QueryRowContext(ctx, `INSERT INTO comments(comment_id,task_id,author_user_id,content) VALUES(gen_random_uuid(),$1,$2,$3) RETURNING comment_id`, taskID, userID, text).Scan(&id)
	return id, err
}
func (r *Repository) UpdateComment(ctx context.Context, userID, commentID, text string) error {
	result, err := r.db.ExecContext(ctx, `UPDATE comments SET content=$1,updated_at=CURRENT_TIMESTAMP WHERE comment_id=$2 AND author_user_id=$3`, text, commentID, userID)
	if err != nil {
		return err
	}
	return requireAffected(result)
}
func (r *Repository) DeleteComment(ctx context.Context, userID, commentID string) error {
	result, err := r.db.ExecContext(ctx, `DELETE FROM comments WHERE comment_id=$1 AND author_user_id=$2`, commentID, userID)
	if err != nil {
		return err
	}
	return requireAffected(result)
}

func (r *Repository) ListAttachments(ctx context.Context, userID, taskID string) ([]Attachment, error) {
	if _, err := r.GetTask(ctx, userID, taskID); err != nil {
		return nil, err
	}
	rows, err := r.db.QueryContext(ctx, `SELECT attachment_id,task_id,uploaded_by_user_id,attachment_type,file_name,storage_key,mime_type,file_size,created_at FROM attachments WHERE task_id=$1 ORDER BY created_at`, taskID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := make([]Attachment, 0)
	for rows.Next() {
		var a Attachment
		if err := rows.Scan(&a.ID, &a.TaskID, &a.UploadedBy, &a.Type, &a.FileName, &a.StorageKey, &a.MimeType, &a.FileSize, &a.CreatedAt); err != nil {
			return nil, err
		}
		items = append(items, a)
	}
	return items, rows.Err()
}
func (r *Repository) AddAttachment(ctx context.Context, userID, taskID, kind, fileName, storageKey, mimeType string, size int64) (string, error) {
	if _, err := r.GetTask(ctx, userID, taskID); err != nil {
		return "", err
	}
	var id string
	err := r.db.QueryRowContext(ctx, `INSERT INTO attachments(attachment_id,task_id,uploaded_by_user_id,attachment_type,file_name,storage_key,mime_type,file_size) VALUES(gen_random_uuid(),$1,$2,$3,$4,$5,$6,$7) RETURNING attachment_id`, taskID, userID, kind, fileName, storageKey, mimeType, size).Scan(&id)
	return id, err
}
func (r *Repository) GetAttachment(ctx context.Context, userID, attachmentID string) (*Attachment, error) {
	var a Attachment
	var projectID string
	err := r.db.QueryRowContext(ctx, `SELECT a.attachment_id,a.task_id,a.uploaded_by_user_id,a.attachment_type,a.file_name,a.storage_key,a.mime_type,a.file_size,a.created_at,b.project_id FROM attachments a JOIN tasks t ON t.task_id=a.task_id JOIN board_columns bc ON bc.column_id=t.column_id JOIN boards b ON b.board_id=bc.board_id WHERE a.attachment_id=$1`, attachmentID).Scan(&a.ID, &a.TaskID, &a.UploadedBy, &a.Type, &a.FileName, &a.StorageKey, &a.MimeType, &a.FileSize, &a.CreatedAt, &projectID)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrNotFound
	} else if err != nil {
		return nil, err
	}
	if err := r.requireMember(ctx, r.db, userID, projectID); err != nil {
		return nil, err
	}
	return &a, nil
}
func (r *Repository) DeleteAttachment(ctx context.Context, userID, attachmentID string) (*Attachment, error) {
	attachment, err := r.GetAttachment(ctx, userID, attachmentID)
	if err != nil {
		return nil, err
	}
	if attachment.UploadedBy != userID {
		return nil, ErrForbidden
	}
	result, err := r.db.ExecContext(ctx, `DELETE FROM attachments WHERE attachment_id=$1`, attachmentID)
	if err != nil {
		return nil, err
	}
	if err := requireAffected(result); err != nil {
		return nil, err
	}
	return attachment, nil
}

func (r *Repository) AddTaskAssignee(ctx context.Context, actorID, taskID, userID string) error {
	task, err := r.GetTask(ctx, actorID, taskID)
	if err != nil {
		return err
	}
	if err := r.requireLead(ctx, r.db, actorID, task.ProjectID); err != nil {
		return err
	}
	_, err = r.db.ExecContext(ctx, `INSERT INTO task_assignees(task_id,user_id) SELECT $1,$2 WHERE EXISTS(SELECT 1 FROM project_members WHERE project_id=$3 AND user_id=$2 AND status='ACTIVE') ON CONFLICT DO NOTHING`, taskID, userID, task.ProjectID)
	return err
}
func (r *Repository) RemoveTaskAssignee(ctx context.Context, actorID, taskID, userID string) error {
	task, err := r.GetTask(ctx, actorID, taskID)
	if err != nil {
		return err
	}
	if err := r.requireLead(ctx, r.db, actorID, task.ProjectID); err != nil {
		return err
	}
	result, err := r.db.ExecContext(ctx, `DELETE FROM task_assignees WHERE task_id=$1 AND user_id=$2`, taskID, userID)
	if err != nil {
		return err
	}
	return requireAffected(result)
}

func (r *Repository) CreateColumn(ctx context.Context, userID, projectID, code, name string, position int) (string, error) {
	if err := r.requireLead(ctx, r.db, userID, projectID); err != nil {
		return "", err
	}
	var id string
	err := r.db.QueryRowContext(ctx, `INSERT INTO board_columns(column_id,board_id,code,name,position) SELECT gen_random_uuid(),board_id,$2,$3,$4 FROM boards WHERE project_id=$1 RETURNING column_id`, projectID, code, name, position).Scan(&id)
	return id, err
}
func (r *Repository) UpdateColumn(ctx context.Context, userID, columnID, code, name string, position int) error {
	var projectID string
	if err := r.db.QueryRowContext(ctx, `SELECT b.project_id FROM board_columns bc JOIN boards b ON b.board_id=bc.board_id WHERE bc.column_id=$1`, columnID).Scan(&projectID); errors.Is(err, sql.ErrNoRows) {
		return ErrNotFound
	} else if err != nil {
		return err
	}
	if err := r.requireLead(ctx, r.db, userID, projectID); err != nil {
		return err
	}
	result, err := r.db.ExecContext(ctx, `UPDATE board_columns SET code=$1,name=$2,position=$3 WHERE column_id=$4`, code, name, position, columnID)
	if err != nil {
		return err
	}
	return requireAffected(result)
}
func (r *Repository) DeleteColumn(ctx context.Context, userID, columnID string) error {
	var projectID string
	if err := r.db.QueryRowContext(ctx, `SELECT b.project_id FROM board_columns bc JOIN boards b ON b.board_id=bc.board_id WHERE bc.column_id=$1`, columnID).Scan(&projectID); errors.Is(err, sql.ErrNoRows) {
		return ErrNotFound
	} else if err != nil {
		return err
	}
	if err := r.requireLead(ctx, r.db, userID, projectID); err != nil {
		return err
	}
	result, err := r.db.ExecContext(ctx, `DELETE FROM board_columns WHERE column_id=$1`, columnID)
	if err != nil {
		return err
	}
	return requireAffected(result)
}

func (r *Repository) ListStudentGroups(ctx context.Context) ([]models.StudentGroup, error) {
	rows, err := r.db.QueryContext(ctx, `SELECT student_group_id,group_code,COALESCE(name,''),created_at,updated_at FROM student_groups ORDER BY group_code`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := make([]models.StudentGroup, 0)
	for rows.Next() {
		var g models.StudentGroup
		if err := rows.Scan(&g.StudentGroupID, &g.GroupCode, &g.Name, &g.CreatedAt, &g.UpdatedAt); err != nil {
			return nil, err
		}
		items = append(items, g)
	}
	return items, rows.Err()
}
func (r *Repository) ListLessons(ctx context.Context, groupCode string, from, to time.Time) ([]models.Lesson, error) {
	rows, err := r.db.QueryContext(ctx, `SELECT l.lesson_id,l.student_group_id,COALESCE(l.external_id,''),l.title,COALESCE(l.room,''),COALESCE(l.teacher,''),l.lesson_kind,l.starts_at,l.ends_at,l.source_status,l.source_updated_at,l.created_at,l.updated_at FROM lessons l JOIN student_groups sg ON sg.student_group_id=l.student_group_id WHERE sg.group_code=$1 AND l.starts_at >= $2 AND l.starts_at < $3 ORDER BY l.starts_at`, groupCode, from, to)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := make([]models.Lesson, 0)
	for rows.Next() {
		var l models.Lesson
		if err := rows.Scan(&l.LessonID, &l.StudentGroupID, &l.ExternalID, &l.Title, &l.Room, &l.Teacher, &l.LessonKind, &l.StartsAt, &l.EndsAt, &l.SourceStatus, &l.SourceUpdatedAt, &l.CreatedAt, &l.UpdatedAt); err != nil {
			return nil, err
		}
		items = append(items, l)
	}
	return items, rows.Err()
}

func (r *Repository) ListEventTypes(ctx context.Context, userID string) ([]EventType, error) {
	rows, err := r.db.QueryContext(ctx, `SELECT et.event_type_id,COALESCE(et.code,''),et.title,et.color,et.scope,et.created_by_user_id,et.project_id,et.created_at,et.updated_at FROM event_types et WHERE et.scope='GLOBAL' OR et.created_by_user_id=$1 OR EXISTS(SELECT 1 FROM project_members pm WHERE pm.project_id=et.project_id AND pm.user_id=$1 AND pm.status='ACTIVE') ORDER BY et.title`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := make([]EventType, 0)
	for rows.Next() {
		var et EventType
		if err := rows.Scan(&et.ID, &et.Code, &et.Title, &et.Color, &et.Scope, &et.CreatedByUserID, &et.ProjectID, &et.CreatedAt, &et.UpdatedAt); err != nil {
			return nil, err
		}
		et.DefaultReminderMinutes, err = r.reminders(ctx, "event_type_reminders", "event_type_id", et.ID)
		if err != nil {
			return nil, err
		}
		items = append(items, et)
	}
	return items, rows.Err()
}

func (r *Repository) GetEventType(ctx context.Context, userID, eventTypeID string) (*EventType, error) {
	items, err := r.ListEventTypes(ctx, userID)
	if err != nil {
		return nil, err
	}
	for i := range items {
		if items[i].ID == eventTypeID {
			return &items[i], nil
		}
	}
	return nil, ErrNotFound
}
func (r *Repository) CreateEventType(ctx context.Context, userID, title, color, scope string, projectID *string, minutes []int) (string, error) {
	if projectID != nil {
		if err := r.requireMember(ctx, r.db, userID, *projectID); err != nil {
			return "", err
		}
	}
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return "", err
	}
	defer tx.Rollback()
	var id string
	if err := tx.QueryRowContext(ctx, `INSERT INTO event_types(event_type_id,title,color,scope,created_by_user_id,project_id) VALUES(gen_random_uuid(),$1,$2,$3,$4,$5) RETURNING event_type_id`, title, color, scope, userID, projectID).Scan(&id); err != nil {
		return "", err
	}
	if err := replaceReminders(ctx, tx, "event_type_reminders", "event_type_id", id, minutes); err != nil {
		return "", err
	}
	if err := tx.Commit(); err != nil {
		return "", err
	}
	return id, nil
}
func (r *Repository) UpdateEventType(ctx context.Context, userID, eventTypeID, title, color string, minutes []int) error {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	result, err := tx.ExecContext(ctx, `UPDATE event_types SET title=$1,color=$2,updated_at=CURRENT_TIMESTAMP WHERE event_type_id=$3 AND created_by_user_id=$4`, title, color, eventTypeID, userID)
	if err != nil {
		return err
	}
	if err := requireAffected(result); err != nil {
		return err
	}
	if err := replaceReminders(ctx, tx, "event_type_reminders", "event_type_id", eventTypeID, minutes); err != nil {
		return err
	}
	return tx.Commit()
}
func (r *Repository) DeleteEventType(ctx context.Context, userID, eventTypeID string) error {
	result, err := r.db.ExecContext(ctx, `DELETE FROM event_types WHERE event_type_id=$1 AND created_by_user_id=$2`, eventTypeID, userID)
	if err != nil {
		return err
	}
	return requireAffected(result)
}

func (r *Repository) ListEvents(ctx context.Context, userID string, projectID *string, groupCode string, from, to time.Time) ([]Event, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT e.event_id,e.owner_user_id,e.event_type_id,e.project_id,e.student_group_id,COALESCE(sg.group_code,''),e.title,COALESCE(e.description,''),COALESCE(e.location,''),e.visibility,e.starts_at,e.ends_at,e.created_at,e.updated_at
		FROM events e LEFT JOIN student_groups sg ON sg.student_group_id=e.student_group_id
		WHERE e.starts_at >= $4 AND e.starts_at < $5 AND (
			(e.visibility='PERSONAL' AND e.owner_user_id=$1) OR
			(e.visibility='PROJECT' AND e.project_id=$2 AND EXISTS(SELECT 1 FROM project_members pm WHERE pm.project_id=e.project_id AND pm.user_id=$1 AND pm.status='ACTIVE')) OR
			(e.visibility='GROUP' AND sg.group_code=$3 AND EXISTS(SELECT 1 FROM users u WHERE u.user_id=$1 AND u.student_group_id=e.student_group_id))
		) ORDER BY e.starts_at
	`, userID, projectID, groupCode, from, to)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := make([]Event, 0)
	for rows.Next() {
		var e Event
		if err := rows.Scan(&e.ID, &e.OwnerUserID, &e.EventTypeID, &e.ProjectID, &e.StudentGroupID, &e.StudentGroup, &e.Title, &e.Description, &e.Location, &e.Visibility, &e.StartsAt, &e.EndsAt, &e.CreatedAt, &e.UpdatedAt); err != nil {
			return nil, err
		}
		e.ReminderMinutes, err = r.reminders(ctx, "event_reminders", "event_id", e.ID)
		if err != nil {
			return nil, err
		}
		items = append(items, e)
	}
	return items, rows.Err()
}

func (r *Repository) GetEvent(ctx context.Context, userID, eventID string) (*Event, error) {
	var event Event
	err := r.db.QueryRowContext(ctx, `
		SELECT e.event_id,e.owner_user_id,e.event_type_id,e.project_id,e.student_group_id,
			COALESCE(sg.group_code,''),e.title,COALESCE(e.description,''),COALESCE(e.location,''),
			e.visibility,e.starts_at,e.ends_at,e.created_at,e.updated_at
		FROM events e LEFT JOIN student_groups sg ON sg.student_group_id=e.student_group_id
		WHERE e.event_id=$2 AND (
			(e.visibility='PERSONAL' AND e.owner_user_id=$1) OR
			(e.visibility='PROJECT' AND EXISTS(SELECT 1 FROM project_members pm WHERE pm.project_id=e.project_id AND pm.user_id=$1 AND pm.status='ACTIVE')) OR
			(e.visibility='GROUP' AND EXISTS(SELECT 1 FROM users u WHERE u.user_id=$1 AND u.student_group_id=e.student_group_id))
		)
	`, userID, eventID).Scan(&event.ID, &event.OwnerUserID, &event.EventTypeID, &event.ProjectID,
		&event.StudentGroupID, &event.StudentGroup, &event.Title, &event.Description, &event.Location,
		&event.Visibility, &event.StartsAt, &event.EndsAt, &event.CreatedAt, &event.UpdatedAt)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrNotFound
	}
	if err != nil {
		return nil, err
	}
	event.ReminderMinutes, err = r.reminders(ctx, "event_reminders", "event_id", event.ID)
	return &event, err
}
func (r *Repository) CreateEvent(ctx context.Context, userID, eventTypeID, title, description, location, visibility string, projectID *string, groupCode string, startsAt, endsAt time.Time, minutes []int) (string, error) {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return "", err
	}
	defer tx.Rollback()
	if visibility == "PROJECT" {
		if projectID == nil {
			return "", ErrConflict
		}
		if err := r.requireMember(ctx, tx, userID, *projectID); err != nil {
			return "", err
		}
	}
	var groupID *string
	if visibility == "GROUP" {
		var id string
		if err := tx.QueryRowContext(ctx, `SELECT student_group_id FROM student_groups WHERE group_code=$1`, groupCode).Scan(&id); err != nil {
			return "", ErrNotFound
		}
		groupID = &id
		var ownGroup string
		if err := tx.QueryRowContext(ctx, `SELECT COALESCE(student_group_id::text,'') FROM users WHERE user_id=$1`, userID).Scan(&ownGroup); err != nil || ownGroup != id {
			return "", ErrForbidden
		}
	}
	var id string
	if err := tx.QueryRowContext(ctx, `INSERT INTO events(event_id,owner_user_id,event_type_id,project_id,student_group_id,title,description,location,visibility,starts_at,ends_at) VALUES(gen_random_uuid(),$1,$2,$3,$4,$5,NULLIF($6,''),NULLIF($7,''),$8,$9,$10) RETURNING event_id`, userID, eventTypeID, projectID, groupID, title, description, location, visibility, startsAt, endsAt).Scan(&id); err != nil {
		return "", err
	}
	if len(minutes) == 0 {
		rows, err := tx.QueryContext(ctx, `SELECT minutes_before FROM event_type_reminders WHERE event_type_id=$1`, eventTypeID)
		if err != nil {
			return "", err
		}
		for rows.Next() {
			var minute int
			if err := rows.Scan(&minute); err != nil {
				rows.Close()
				return "", err
			}
			minutes = append(minutes, minute)
		}
		rows.Close()
	}
	if err := replaceReminders(ctx, tx, "event_reminders", "event_id", id, minutes); err != nil {
		return "", err
	}
	if err := tx.Commit(); err != nil {
		return "", err
	}
	return id, nil
}
func (r *Repository) UpdateEvent(ctx context.Context, userID, eventID, title, description, location string, startsAt, endsAt time.Time, minutes []int) error {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	result, err := tx.ExecContext(ctx, `UPDATE events SET title=$1,description=NULLIF($2,''),location=NULLIF($3,''),starts_at=$4,ends_at=$5,updated_at=CURRENT_TIMESTAMP WHERE event_id=$6 AND owner_user_id=$7`, title, description, location, startsAt, endsAt, eventID, userID)
	if err != nil {
		return err
	}
	if err := requireAffected(result); err != nil {
		return err
	}
	if err := replaceReminders(ctx, tx, "event_reminders", "event_id", eventID, minutes); err != nil {
		return err
	}
	return tx.Commit()
}
func (r *Repository) DeleteEvent(ctx context.Context, userID, eventID string) error {
	result, err := r.db.ExecContext(ctx, `DELETE FROM events WHERE event_id=$1 AND owner_user_id=$2`, eventID, userID)
	if err != nil {
		return err
	}
	return requireAffected(result)
}

type queryer interface {
	QueryRowContext(context.Context, string, ...any) *sql.Row
}

func (r *Repository) requireMember(ctx context.Context, q queryer, userID, projectID string) error {
	var exists bool
	err := q.QueryRowContext(ctx, `SELECT EXISTS(SELECT 1 FROM project_members WHERE project_id=$1 AND user_id=$2 AND status='ACTIVE')`, projectID, userID).Scan(&exists)
	if err != nil {
		return err
	}
	if !exists {
		return ErrForbidden
	}
	return nil
}
func (r *Repository) requireLead(ctx context.Context, q queryer, userID, projectID string) error {
	var exists bool
	err := q.QueryRowContext(ctx, `SELECT EXISTS(SELECT 1 FROM project_members WHERE project_id=$1 AND user_id=$2 AND role='LEAD' AND status='ACTIVE')`, projectID, userID).Scan(&exists)
	if err != nil {
		return err
	}
	if !exists {
		return ErrForbidden
	}
	return nil
}
func (r *Repository) taskAssignees(ctx context.Context, taskID string) ([]string, error) {
	rows, err := r.db.QueryContext(ctx, `SELECT user_id FROM task_assignees WHERE task_id=$1 ORDER BY user_id`, taskID)
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
func requireAffected(result sql.Result) error {
	count, err := result.RowsAffected()
	if err != nil {
		return err
	}
	if count == 0 {
		return ErrNotFound
	}
	return nil
}
func (r *Repository) reminders(ctx context.Context, table, column, id string) ([]int, error) {
	query := fmt.Sprintf("SELECT minutes_before FROM %s WHERE %s=$1 ORDER BY minutes_before DESC", table, column)
	rows, err := r.db.QueryContext(ctx, query, id)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := make([]int, 0)
	for rows.Next() {
		var minute int
		if err := rows.Scan(&minute); err != nil {
			return nil, err
		}
		items = append(items, minute)
	}
	return items, rows.Err()
}
func replaceReminders(ctx context.Context, tx *sql.Tx, table, column, id string, minutes []int) error {
	deleteQuery := fmt.Sprintf("DELETE FROM %s WHERE %s=$1", table, column)
	if _, err := tx.ExecContext(ctx, deleteQuery, id); err != nil {
		return err
	}
	insertQuery := fmt.Sprintf("INSERT INTO %s(%s,minutes_before) VALUES($1,$2)", table, column)
	for _, minute := range minutes {
		if minute < 0 {
			return ErrConflict
		}
		if _, err := tx.ExecContext(ctx, insertQuery, id, minute); err != nil {
			return err
		}
	}
	return nil
}
