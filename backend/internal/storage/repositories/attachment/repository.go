package attachment_repo

import (
	"context"
	"database/sql"
	"fmt"
	"kanban-board/internal/models"
)

type AttachmentRepo struct{ db *sql.DB }

func Init(db *sql.DB) *AttachmentRepo { return &AttachmentRepo{db: db} }
func (r *AttachmentRepo) CreateAttachment(ctx context.Context, taskID, userID, kind, fileName, storageKey, mimeType string, size int64) (string, error) {
	var id string
	err := r.db.QueryRowContext(ctx, `INSERT INTO attachments(attachment_id,task_id,uploaded_by_user_id,attachment_type,file_name,storage_key,mime_type,file_size) VALUES(gen_random_uuid(),$1,$2,$3,$4,$5,$6,$7) RETURNING attachment_id`, taskID, userID, kind, fileName, storageKey, mimeType, size).Scan(&id)
	if err != nil {
		return "", fmt.Errorf("failed to create attachment: %w", err)
	}
	return id, nil
}
func scanAttachment(row interface{ Scan(...any) error }) (*models.Attachment, error) {
	var a models.Attachment
	err := row.Scan(&a.AttachmentID, &a.TaskID, &a.UploadedByUserID, &a.AttachmentType, &a.FileName, &a.StorageKey, &a.MimeType, &a.FileSize, &a.CreatedAt)
	return &a, err
}
func (r *AttachmentRepo) GetAttachmentByID(ctx context.Context, id string) (*models.Attachment, error) {
	a, err := scanAttachment(r.db.QueryRowContext(ctx, `SELECT attachment_id,task_id,uploaded_by_user_id,attachment_type,file_name,storage_key,mime_type,file_size,created_at FROM attachments WHERE attachment_id=$1`, id))
	if err != nil {
		return nil, fmt.Errorf("failed to get attachment: %w", err)
	}
	return a, nil
}
func (r *AttachmentRepo) GetAttachmentsByTaskID(ctx context.Context, taskID string) ([]models.Attachment, error) {
	rows, err := r.db.QueryContext(ctx, `SELECT attachment_id,task_id,uploaded_by_user_id,attachment_type,file_name,storage_key,mime_type,file_size,created_at FROM attachments WHERE task_id=$1 ORDER BY created_at`, taskID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := make([]models.Attachment, 0)
	for rows.Next() {
		a, err := scanAttachment(rows)
		if err != nil {
			return nil, err
		}
		items = append(items, *a)
	}
	return items, rows.Err()
}
func (r *AttachmentRepo) UpdateAttachment(ctx context.Context, id, kind, fileName, storageKey, mimeType string, size int64) error {
	_, err := r.db.ExecContext(ctx, `UPDATE attachments SET attachment_type=$1,file_name=$2,storage_key=$3,mime_type=$4,file_size=$5 WHERE attachment_id=$6`, kind, fileName, storageKey, mimeType, size, id)
	return err
}
func (r *AttachmentRepo) DeleteAttachment(ctx context.Context, id string) error {
	_, err := r.db.ExecContext(ctx, `DELETE FROM attachments WHERE attachment_id=$1`, id)
	return err
}
