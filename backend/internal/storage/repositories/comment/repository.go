package comment_repo

import (
	"context"
	"database/sql"
	"fmt"
	"kanban-board/internal/models"
)

type CommentRepo struct{ db *sql.DB }

func Init(db *sql.DB) *CommentRepo { return &CommentRepo{db: db} }
func (r *CommentRepo) CreateComment(ctx context.Context, taskID, authorID, content string) (string, error) {
	var id string
	err := r.db.QueryRowContext(ctx, `INSERT INTO comments(comment_id,task_id,author_user_id,content) VALUES(gen_random_uuid(),$1,$2,$3) RETURNING comment_id`, taskID, authorID, content).Scan(&id)
	if err != nil {
		return "", fmt.Errorf("failed to create comment: %w", err)
	}
	return id, nil
}
func scanComment(row interface{ Scan(...any) error }) (*models.Comment, error) {
	var c models.Comment
	err := row.Scan(&c.CommentID, &c.TaskID, &c.AuthorUserID, &c.Content, &c.CreatedAt, &c.UpdatedAt)
	return &c, err
}
func (r *CommentRepo) GetCommentByID(ctx context.Context, id string) (*models.Comment, error) {
	c, err := scanComment(r.db.QueryRowContext(ctx, `SELECT comment_id,task_id,author_user_id,content,created_at,updated_at FROM comments WHERE comment_id=$1`, id))
	if err != nil {
		return nil, fmt.Errorf("failed to get comment: %w", err)
	}
	return c, nil
}
func (r *CommentRepo) GetCommentsByTaskID(ctx context.Context, taskID string) ([]models.Comment, error) {
	rows, err := r.db.QueryContext(ctx, `SELECT comment_id,task_id,author_user_id,content,created_at,updated_at FROM comments WHERE task_id=$1 ORDER BY created_at`, taskID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := make([]models.Comment, 0)
	for rows.Next() {
		c, err := scanComment(rows)
		if err != nil {
			return nil, err
		}
		items = append(items, *c)
	}
	return items, rows.Err()
}
func (r *CommentRepo) UpdateComment(ctx context.Context, id, content string) error {
	_, err := r.db.ExecContext(ctx, `UPDATE comments SET content=$1,updated_at=CURRENT_TIMESTAMP WHERE comment_id=$2`, content, id)
	return err
}
func (r *CommentRepo) DeleteComment(ctx context.Context, id string) error {
	_, err := r.db.ExecContext(ctx, `DELETE FROM comments WHERE comment_id=$1`, id)
	return err
}
