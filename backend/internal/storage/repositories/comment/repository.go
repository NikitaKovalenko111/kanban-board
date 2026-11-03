package comment_repo

import (
	"context"
	"database/sql"

	"kanban-board/internal/models"
	repositorycommon "kanban-board/internal/storage/repositories/common"
)

type Repository struct {
	db *sql.DB
}

func Init(db *sql.DB) *Repository {
	return &Repository{
		db: db,
	}
}

func (r *Repository) ListComments(ctx context.Context, userID, taskID string) ([]models.Comment, error) {
	if _, err := repositorycommon.RequireTaskMember(ctx, r.db, userID, taskID); err != nil {
		return nil, err
	}
	rows, err := r.db.QueryContext(ctx, `
		SELECT
		    c.comment_id,
		    c.task_id,
		    c.author_user_id,
		    u.display_name,
		    c.content,
		    c.created_at,
		    c.updated_at
		FROM comments c
		JOIN users u ON u.user_id=c.author_user_id
		WHERE c.task_id=$1
		ORDER BY c.created_at
	`, taskID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := make([]models.Comment, 0)
	for rows.Next() {
		var c models.Comment
		if err := rows.Scan(&c.ID, &c.TaskID, &c.AuthorID, &c.AuthorName, &c.Text, &c.CreatedAt, &c.UpdatedAt); err != nil {
			return nil, err
		}
		items = append(items, c)
	}
	return items, rows.Err()
}

func (r *Repository) AddComment(ctx context.Context, userID, taskID, text string) (*models.Comment, error) {
	if _, err := repositorycommon.RequireTaskMember(ctx, r.db, userID, taskID); err != nil {
		return nil, err
	}
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, err
	}
	defer tx.Rollback()

	var comment models.Comment
	err = tx.QueryRowContext(ctx, `
		INSERT INTO comments (
		    comment_id,
		    task_id,
		    author_user_id,
		    content
		)
		VALUES
		    (gen_random_uuid(),$1,$2,$3)
		RETURNING comment_id, task_id, author_user_id, content, created_at
	`, taskID, userID, text).Scan(&comment.ID, &comment.TaskID, &comment.AuthorID, &comment.Text, &comment.CreatedAt)
	if err != nil {
		return nil, err
	}
	if _, err := tx.ExecContext(ctx, `
		INSERT INTO notifications (notification_id, user_id, kind, title, text, task_id)
		SELECT
			gen_random_uuid(),
			recipient.user_id,
			'COMMENT',
			'Новый комментарий',
			LEFT($3, 500),
			$1
		FROM (
			SELECT created_by_user_id AS user_id FROM tasks WHERE task_id = $1
			UNION
			SELECT user_id FROM task_assignees WHERE task_id = $1
		) recipient
		WHERE recipient.user_id <> $2
	`, taskID, userID, text); err != nil {
		return nil, err
	}
	if err := tx.Commit(); err != nil {
		return nil, err
	}

	return &comment, nil
}

func (r *Repository) UpdateComment(ctx context.Context, userID, commentID, text string) error {
	result, err := r.db.ExecContext(ctx, `
		UPDATE comments
		SET
		    content=$1,
		    updated_at=CURRENT_TIMESTAMP
		WHERE comment_id=$2 AND author_user_id=$3
	`, text, commentID, userID)
	if err != nil {
		return err
	}
	return repositorycommon.RequireAffected(result)
}

func (r *Repository) DeleteComment(ctx context.Context, userID, commentID string) error {
	result, err := r.db.ExecContext(ctx, `
		DELETE FROM comments
		WHERE comment_id=$1 AND author_user_id=$2
	`, commentID, userID)
	if err != nil {
		return err
	}
	return repositorycommon.RequireAffected(result)
}
