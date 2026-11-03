package servicecommon

import (
	"errors"
	"fmt"

	repositorycommon "kanban-board/internal/storage/repositories/common"

	"github.com/jackc/pgx/v5/pgconn"
)

var (
	ErrInvalidInput = errors.New("invalid input")
	ErrNotFound     = repositorycommon.ErrNotFound
	ErrForbidden    = repositorycommon.ErrForbidden
	ErrConflict     = repositorycommon.ErrConflict
)

func InvalidInput(format string, arguments ...any) error {
	return fmt.Errorf("%w: %s", ErrInvalidInput, fmt.Sprintf(format, arguments...))
}

func DatabaseError(err error) error {
	var postgresError *pgconn.PgError
	if errors.As(err, &postgresError) && postgresError.Code == "23505" {
		return fmt.Errorf("%w: value must be unique", ErrConflict)
	}

	return err
}
