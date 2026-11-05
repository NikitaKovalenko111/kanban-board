package attachment_service

import servicecommon "kanban-board/internal/services/common"

func validateAttachment(kind, name, storageKey, mimeType string, size int64) error {
	if kind != "SOURCE" && kind != "RESULT" {
		return servicecommon.InvalidInput("attachmentType must be SOURCE or RESULT")
	}
	if err := servicecommon.ValidateRequired(name, "fileName"); err != nil {
		return err
	}
	if err := servicecommon.ValidateRequired(storageKey, "storageKey"); err != nil {
		return err
	}
	if err := servicecommon.ValidateRequired(mimeType, "mimeType"); err != nil {
		return err
	}
	if size < 0 {
		return servicecommon.InvalidInput("fileSize must not be negative")
	}

	return nil
}
