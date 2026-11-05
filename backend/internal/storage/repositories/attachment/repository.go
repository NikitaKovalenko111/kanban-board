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
	return &Repository{db: db}
}

func (r *Repository) AddAttachment(ctx context.Context, userID, taskID, kind, fileName, storageKey, mimeType string, size int64) (*models.Attachment, error) {
	var systemRole, columnKind string
	var assigned bool
	err := r.db.QueryRowContext(ctx, `
		SELECT u.system_role, bc.kind,
			EXISTS(SELECT 1 FROM task_assignees ta WHERE ta.task_id = t.task_id AND ta.user_id = $2)
		FROM tasks t
		JOIN board_columns bc ON bc.column_id = t.column_id
		JOIN users u ON u.user_id = $2 AND u.is_active = TRUE
		WHERE t.task_id = $1
	`, taskID, userID).Scan(&systemRole, &columnKind, &assigned)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, repositorycommon.ErrNotFound
	}
	if err != nil {
		return nil, err
	}
	allowed := kind == "SOURCE" && systemRole == "CAPTAIN"
	allowed = allowed || kind == "RESULT" && assigned && (columnKind == "IN_PROGRESS" || columnKind == "REVIEW")
	if !allowed {
		return nil, repositorycommon.ErrForbidden
	}

	var attachment models.Attachment
	err = r.db.QueryRowContext(ctx, `
		INSERT INTO attachments (
			attachment_id, task_id, uploaded_by_user_id, attachment_type,
			file_name, storage_key, mime_type, file_size
		)
		VALUES (gen_random_uuid(), $1, $2, $3, $4, $5, $6, $7)
		RETURNING attachment_id, task_id, uploaded_by_user_id, attachment_type, created_at
	`, taskID, userID, kind, fileName, storageKey, mimeType, size).Scan(
		&attachment.ID, &attachment.TaskID, &attachment.UploadedBy, &attachment.Kind, &attachment.UploadedAt,
	)
	if err != nil {
		return nil, err
	}
	attachment.StorageKey = storageKey
	attachment.File = models.FileInfo{
		ID: attachment.ID, Name: fileName, SizeBytes: size, MimeType: mimeType,
		DownloadURL: "/api/v1/files/" + attachment.ID,
	}
	return &attachment, nil
}

func (r *Repository) GetAttachment(ctx context.Context, userID, attachmentID string) (*models.Attachment, error) {
	var attachment models.Attachment
	var projectID string
	err := r.db.QueryRowContext(ctx, `
		SELECT
			a.attachment_id, a.task_id, a.uploaded_by_user_id, a.attachment_type,
			a.file_name, a.storage_key, a.mime_type, a.file_size, a.created_at, b.project_id
		FROM attachments a
		JOIN tasks t ON t.task_id = a.task_id
		JOIN board_columns bc ON bc.column_id = t.column_id
		JOIN boards b ON b.board_id = bc.board_id
		WHERE a.attachment_id = $1
	`, attachmentID).Scan(
		&attachment.ID, &attachment.TaskID, &attachment.UploadedBy, &attachment.Kind,
		&attachment.File.Name, &attachment.StorageKey, &attachment.File.MimeType,
		&attachment.File.SizeBytes, &attachment.UploadedAt, &projectID,
	)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, repositorycommon.ErrNotFound
	}
	if err != nil {
		return nil, err
	}
	if err := repositorycommon.RequireMember(ctx, r.db, userID, projectID); err != nil {
		return nil, err
	}
	attachment.File.ID = attachment.ID
	attachment.File.DownloadURL = "/api/v1/files/" + attachment.ID
	return &attachment, nil
}

func (r *Repository) DeleteAttachment(ctx context.Context, userID, taskID, attachmentID string) (*models.Attachment, error) {
	attachment, err := r.GetAttachment(ctx, userID, attachmentID)
	if err != nil {
		return nil, err
	}
	if attachment.TaskID != taskID {
		return nil, repositorycommon.ErrNotFound
	}
	var role, kind string
	if err := r.db.QueryRowContext(ctx, `
		SELECT u.system_role, bc.kind
		FROM tasks t JOIN board_columns bc ON bc.column_id = t.column_id
		JOIN users u ON u.user_id = $2
		WHERE t.task_id = $1
	`, taskID, userID).Scan(&role, &kind); err != nil {
		return nil, err
	}
	if kind == "DONE" || (attachment.UploadedBy != userID && role != "CAPTAIN") {
		return nil, repositorycommon.ErrForbidden
	}
	result, err := r.db.ExecContext(ctx, `DELETE FROM attachments WHERE attachment_id = $1`, attachmentID)
	if err != nil {
		return nil, err
	}
	if err := repositorycommon.RequireAffected(result); err != nil {
		return nil, err
	}
	return attachment, nil
}
