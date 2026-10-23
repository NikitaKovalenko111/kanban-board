package attachment_repo

import (
	"context"
	"database/sql"
	"fmt"
	"kanban-board/internal/models"
)

type AttachmentRepo struct {
	db  *sql.DB
	ctx context.Context
}

func Init(db *sql.DB, ctx context.Context) *AttachmentRepo {
	return &AttachmentRepo{
		db:  db,
		ctx: ctx,
	}
}

func (r *AttachmentRepo) CreateAttachment(taskID string, uploadedBy string, fileName string, storageKey string, mimeType string, fileSize int64) (string, error) {
	var id string
	err := r.db.QueryRowContext(r.ctx, `
		INSERT INTO attachment (
			id, task_id, uploaded_by, file_name,
			storage_key, mime_type, file_size
		)
		VALUES (gen_random_uuid(), $1, $2, $3, $4, $5, $6)
		RETURNING id
	`, taskID, uploadedBy, fileName, storageKey, mimeType, fileSize).Scan(&id)
	if err != nil {
		return "", fmt.Errorf("failed to create attachment: %v", err)
	}

	return id, nil
}

func (r *AttachmentRepo) GetAttachmentByID(id string) (*models.Attachment, error) {
	var attachment models.Attachment
	err := r.db.QueryRowContext(r.ctx, `
		SELECT id, task_id, uploaded_by, file_name,
			storage_key, mime_type, file_size, created_at
		FROM attachment
		WHERE id = $1
	`, id).Scan(
		&attachment.ID,
		&attachment.TaskID,
		&attachment.UploadedBy,
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

func (r *AttachmentRepo) GetAttachmentsByTaskID(taskID string) ([]models.Attachment, error) {
	rows, err := r.db.QueryContext(r.ctx, `
		SELECT id, task_id, uploaded_by, file_name,
			storage_key, mime_type, file_size, created_at
		FROM attachment
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
			&attachment.ID,
			&attachment.TaskID,
			&attachment.UploadedBy,
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

func (r *AttachmentRepo) UpdateAttachment(id string, fileName string, storageKey string, mimeType string, fileSize int64) error {
	_, err := r.db.ExecContext(r.ctx, `
		UPDATE attachment
		SET file_name = $1, storage_key = $2, mime_type = $3, file_size = $4
		WHERE id = $5
	`, fileName, storageKey, mimeType, fileSize, id)
	if err != nil {
		return fmt.Errorf("failed to update attachment: %v", err)
	}

	return nil
}

func (r *AttachmentRepo) DeleteAttachment(id string) error {
	_, err := r.db.ExecContext(r.ctx, `
		DELETE FROM attachment
		WHERE id = $1
	`, id)
	if err != nil {
		return fmt.Errorf("failed to delete attachment: %v", err)
	}

	return nil
}
