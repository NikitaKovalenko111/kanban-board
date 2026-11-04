package attachment_repo

import (
	"context"
	"database/sql"
	"fmt"
	"kanban-board/internal/models"
)

type AttachmentRepo struct {
	db *sql.DB
}

func Init(db *sql.DB) *AttachmentRepo {
	return &AttachmentRepo{
		db: db,
	}
}

func (r *AttachmentRepo) CreateAttachment(ctx context.Context, taskID string, uploadedByUserID string, fileName string, storageKey string, mimeType string, fileSize int64) (string, error) {
	var attachmentID string
	err := r.db.QueryRowContext(ctx, `
		INSERT INTO attachments (
			attachment_id, task_id, uploaded_by_user_id, file_name,
			storage_key, mime_type, file_size
		)
		VALUES (gen_random_uuid(), $1, $2, $3, $4, $5, $6)
		RETURNING attachment_id
	`, taskID, uploadedByUserID, fileName, storageKey, mimeType, fileSize).Scan(&attachmentID)
	if err != nil {
		return "", fmt.Errorf("failed to create attachment: %v", err)
	}

	return attachmentID, nil
}

func (r *AttachmentRepo) GetAttachmentByID(ctx context.Context, attachmentID string) (*models.Attachment, error) {
	var attachment models.Attachment
	err := r.db.QueryRowContext(ctx, `
		SELECT attachment_id, task_id, uploaded_by_user_id, file_name,
			storage_key, mime_type, file_size, created_at
		FROM attachments
		WHERE attachment_id = $1
	`, attachmentID).Scan(
		&attachment.AttachmentID,
		&attachment.TaskID,
		&attachment.UploadedByUserID,
		&attachment.FileName,
		&attachment.StorageKey,
		&attachment.MimeType,
		&attachment.FileSize,
		&attachment.CreatedAt,
	)
	if err != nil {
		return nil, fmt.Errorf("failed to get attachment by id: %v", err)
	}

	return &attachment, nil
}

func (r *AttachmentRepo) GetAttachmentsByTaskID(ctx context.Context, taskID string) ([]models.Attachment, error) {
	rows, err := r.db.QueryContext(ctx, `
		SELECT attachment_id, task_id, uploaded_by_user_id, file_name,
			storage_key, mime_type, file_size, created_at
		FROM attachments
		WHERE task_id = $1
		ORDER BY created_at
	`, taskID)
	if err != nil {
		return nil, fmt.Errorf("failed to get attachments: %v", err)
	}
	defer rows.Close()

	var attachments []models.Attachment
	for rows.Next() {
		var attachment models.Attachment
		if err := rows.Scan(
			&attachment.AttachmentID,
			&attachment.TaskID,
			&attachment.UploadedByUserID,
			&attachment.FileName,
			&attachment.StorageKey,
			&attachment.MimeType,
			&attachment.FileSize,
			&attachment.CreatedAt,
		); err != nil {
			return nil, fmt.Errorf("failed to scan attachment: %v", err)
		}
		attachments = append(attachments, attachment)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("failed to iterate attachments: %v", err)
	}

	return attachments, nil
}

func (r *AttachmentRepo) UpdateAttachment(ctx context.Context, attachmentID string, fileName string, storageKey string, mimeType string, fileSize int64) error {
	_, err := r.db.ExecContext(ctx, `
		UPDATE attachments
		SET file_name = $1, storage_key = $2, mime_type = $3, file_size = $4
		WHERE attachment_id = $5
	`, fileName, storageKey, mimeType, fileSize, attachmentID)
	if err != nil {
		return fmt.Errorf("failed to update attachment: %v", err)
	}

	return nil
}

func (r *AttachmentRepo) DeleteAttachment(ctx context.Context, attachmentID string) error {
	_, err := r.db.ExecContext(ctx, `
		DELETE FROM attachments
		WHERE attachment_id = $1
	`, attachmentID)
	if err != nil {
		return fmt.Errorf("failed to delete attachment: %v", err)
	}

	return nil
}
