package comment_repo

import (
	"context"
	"database/sql"
	"fmt"
	"kanban-board/internal/models"
)

type CommentRepo struct {
	db *sql.DB
}

func Init(db *sql.DB) *CommentRepo {
	return &CommentRepo{
		db: db,
	}
}

func (r *CommentRepo) CreateComment(ctx context.Context, taskID string, authorID string, content string) (string, error) {
	var commentID string
	err := r.db.QueryRowContext(ctx, `
		INSERT INTO comments (comment_id, task_id, author_user_id, content)
		VALUES (gen_random_uuid(), $1, $2, $3)
		RETURNING comment_id
	`, taskID, authorID, content).Scan(&commentID)
	if err != nil {
		return "", fmt.Errorf("failed to create comment: %v", err)
	}

	return commentID, nil
}

func (r *CommentRepo) GetCommentByID(ctx context.Context, commentID string) (*models.Comment, error) {
	var comment models.Comment
	err := r.db.QueryRowContext(ctx, `
		SELECT comment_id, task_id, author_user_id, content, created_at
		FROM comments
		WHERE comment_id = $1
	`, commentID).Scan(
		&comment.CommentID,
		&comment.TaskID,
		&comment.AuthorUserID,
		&comment.Content,
		&comment.CreatedAt,
	)
	if err != nil {
		return nil, fmt.Errorf("failed to get comment by id: %v", err)
	}

	return &comment, nil
}

func (r *CommentRepo) GetCommentsByTaskID(ctx context.Context, taskID string) ([]models.Comment, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT comment_id, task_id, author_user_id, content, created_at
		FROM comments
		WHERE task_id = $1
		ORDER BY created_at
	`, taskID)
	if err != nil {
		return nil, fmt.Errorf("failed to get comments: %v", err)
	}
	defer rows.Close()

	var comments []models.Comment
	for rows.Next() {
		var comment models.Comment
		if err := rows.Scan(
			&comment.CommentID,
			&comment.TaskID,
			&comment.AuthorUserID,
			&comment.Content,
			&comment.CreatedAt,
		); err != nil {
			return nil, fmt.Errorf("failed to scan comment: %v", err)
		}
		comments = append(comments, comment)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("failed to iterate comments: %v", err)
	}

	return comments, nil
}

func (r *CommentRepo) UpdateComment(ctx context.Context, commentID string, content string) error {
	_, err := r.db.ExecContext(ctx, `
		UPDATE comments
		SET content = $1
		WHERE comment_id = $2
	`, content, commentID)
	if err != nil {
		return fmt.Errorf("failed to update comment: %v", err)
	}

	return nil
}

func (r *CommentRepo) DeleteComment(ctx context.Context, commentID string) error {
	_, err := r.db.ExecContext(ctx, `
		DELETE FROM comments
		WHERE comment_id = $1
	`, commentID)
	if err != nil {
		return fmt.Errorf("failed to delete comment: %v", err)
	}

	return nil
}
