package attachment_repo

import (
	"context"
	"database/sql"
	"errors"
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

func (r *Repository) ListAttachments(ctx context.Context, userID, taskID string) ([]models.Attachment, error) {
	if _, err := repositorycommon.RequireTaskMember(ctx, r.db, userID, taskID); err != nil {
		return nil, err
	}
	rows, err := r.db.QueryContext(ctx, `
		SELECT
		    attachment_id,
		    task_id,
		    uploaded_by_user_id,
		    attachment_type,
		    file_name,
		    storage_key,
		    mime_type,
		    file_size,
		    created_at
		FROM attachments
		WHERE task_id=$1
		ORDER BY created_at
	`, taskID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := make([]models.Attachment, 0)
	for rows.Next() {
		var a models.Attachment
		if err := rows.Scan(&a.ID, &a.TaskID, &a.UploadedBy, &a.Type, &a.FileName, &a.StorageKey, &a.MimeType, &a.FileSize, &a.CreatedAt); err != nil {
			return nil, err
		}
		items = append(items, a)
	}
	return items, rows.Err()
}

func (r *Repository) AddAttachment(ctx context.Context, userID, taskID, kind, fileName, storageKey, mimeType string, size int64) (string, error) {
	if _, err := repositorycommon.RequireTaskMember(ctx, r.db, userID, taskID); err != nil {
		return "", err
	}
	var id string
	err := r.db.QueryRowContext(ctx, `
		INSERT INTO attachments (
		    attachment_id,
		    task_id,
		    uploaded_by_user_id,
		    attachment_type,
		    file_name,
		    storage_key,
		    mime_type,
		    file_size
		)
		VALUES
		    (gen_random_uuid(),$1,$2,$3,$4,$5,$6,$7)
		RETURNING attachment_id
	`, taskID, userID, kind, fileName, storageKey, mimeType, size).Scan(&id)
	return id, err
}

func (r *Repository) GetAttachment(ctx context.Context, userID, attachmentID string) (*models.Attachment, error) {
	var a models.Attachment
	var projectID string
	err := r.db.QueryRowContext(ctx, `
		SELECT
		    a.attachment_id,
		    a.task_id,
		    a.uploaded_by_user_id,
		    a.attachment_type,
		    a.file_name,
		    a.storage_key,
		    a.mime_type,
		    a.file_size,
		    a.created_at,
		    b.project_id
		FROM attachments a
		JOIN tasks t ON t.task_id=a.task_id
		JOIN board_columns bc ON bc.column_id=t.column_id
		JOIN boards b ON b.board_id=bc.board_id
		WHERE a.attachment_id=$1
	`, attachmentID).Scan(&a.ID, &a.TaskID, &a.UploadedBy, &a.Type, &a.FileName, &a.StorageKey, &a.MimeType, &a.FileSize, &a.CreatedAt, &projectID)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, repositorycommon.ErrNotFound
	} else if err != nil {
		return nil, err
	}
	if err := repositorycommon.RequireMember(ctx, r.db, userID, projectID); err != nil {
		return nil, err
	}
	return &a, nil
}

func (r *Repository) DeleteAttachment(ctx context.Context, userID, attachmentID string) (*models.Attachment, error) {
	attachment, err := r.GetAttachment(ctx, userID, attachmentID)
	if err != nil {
		return nil, err
	}
	if attachment.UploadedBy != userID {
		return nil, repositorycommon.ErrForbidden
	}
	result, err := r.db.ExecContext(ctx, `
		DELETE FROM attachments
		WHERE attachment_id=$1
	`, attachmentID)
	if err != nil {
		return nil, err
	}
	if err := repositorycommon.RequireAffected(result); err != nil {
		return nil, err
	}
	return attachment, nil
}
