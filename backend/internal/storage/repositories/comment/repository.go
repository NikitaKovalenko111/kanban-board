package comment_repo

import (
	"context"
	"database/sql"
	"fmt"
	"kanban-board/internal/models"
)

type CommentRepo struct {
	db  *sql.DB
	ctx context.Context
}

func Init(db *sql.DB, ctx context.Context) *CommentRepo {
	return &CommentRepo{
		db:  db,
		ctx: ctx,
	}
}

func (r *CommentRepo) CreateComment(taskID string, authorID string, content string) (string, error) {
	var id string
	err := r.db.QueryRowContext(r.ctx, `
		INSERT INTO comment (id, task_id, author_id, content)
		VALUES (gen_random_uuid(), $1, $2, $3)
		RETURNING id
	`, taskID, authorID, content).Scan(&id)
	if err != nil {
		return "", fmt.Errorf("failed to create comment: %v", err)
	}

	return id, nil
}

func (r *CommentRepo) GetCommentByID(id string) (*models.Comment, error) {
	var comment models.Comment
	err := r.db.QueryRowContext(r.ctx, `
		SELECT id, task_id, author_id, content, created_at
		FROM comment
		WHERE id = $1
	`, id).Scan(
		&comment.ID,
		&comment.TaskID,
		&comment.AuthorID,
		&comment.Content,
		&comment.CreatedAt,
	)
	if err != nil {
		return nil, fmt.Errorf("failed to get comment by id: %v", err)
	}

	return &comment, nil
}

func (r *CommentRepo) GetCommentsByTaskID(taskID string) ([]models.Comment, error) {
	rows, err := r.db.QueryContext(r.ctx, `
		SELECT id, task_id, author_id, content, created_at
		FROM comment
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
			&comment.ID,
			&comment.TaskID,
			&comment.AuthorID,
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

func (r *CommentRepo) UpdateComment(id string, content string) error {
	_, err := r.db.ExecContext(r.ctx, `
		UPDATE comment
		SET content = $1
		WHERE id = $2
	`, content, id)
	if err != nil {
		return fmt.Errorf("failed to update comment: %v", err)
	}

	return nil
}

func (r *CommentRepo) DeleteComment(id string) error {
	_, err := r.db.ExecContext(r.ctx, `
		DELETE FROM comment
		WHERE id = $1
	`, id)
	if err != nil {
		return fmt.Errorf("failed to delete comment: %v", err)
	}

	return nil
}
