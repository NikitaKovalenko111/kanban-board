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

func (r *Repository) AddComment(ctx context.Context, userID, taskID, text string) (string, error) {
	if _, err := repositorycommon.RequireTaskMember(ctx, r.db, userID, taskID); err != nil {
		return "", err
	}
	var id string
	err := r.db.QueryRowContext(ctx, `
		INSERT INTO comments (
		    comment_id,
		    task_id,
		    author_user_id,
		    content
		)
		VALUES
		    (gen_random_uuid(),$1,$2,$3)
		RETURNING comment_id
	`, taskID, userID, text).Scan(&id)
	return id, err
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
