package auth_service

import (
	"context"
	"database/sql/driver"
	"errors"
	"regexp"
	"testing"
	"time"

	"kanban-board/internal/config"
	session_repo "kanban-board/internal/storage/repositories/session"
	users_repo "kanban-board/internal/storage/repositories/user"
	"kanban-board/pkg"

	"github.com/DATA-DOG/go-sqlmock"
	"github.com/jackc/pgx/v5/pgconn"
)

const (
	testUserID        = "f5f93c16-5426-4f70-8718-e39f29a6e242"
	testUsername      = "student.one"
	testEmail         = "student@example.com"
	testPassword      = "Password123"
	testGroupName     = "ТОП-203Б"
	testAccessSecret  = "test-access-secret"
	testRefreshSecret = "test-refresh-secret"
)

type stringCapture struct {
	value string
}

func (capture *stringCapture) Match(value driver.Value) bool {
	stringValue, ok := value.(string)
	if !ok {
		return false
	}

	capture.value = stringValue
	return true
}

type timeCapture struct {
	value time.Time
}

func (capture *timeCapture) Match(value driver.Value) bool {
	timeValue, ok := value.(time.Time)
	if !ok {
		return false
	}

	capture.value = timeValue
	return true
}

func TestRegisterUserSuccess(t *testing.T) {
	service, mock, closeDB := newTestService(t)
	defer closeDB()

	passwordHash := &stringCapture{}
	mock.ExpectQuery(regexp.QuoteMeta(`
		INSERT INTO users (user_id, username, email, group_name, image_url, password_hash)
		VALUES (gen_random_uuid(), $1, $2, $3, $4, $5)
		RETURNING user_id
	`)).
		WithArgs(testUsername, testEmail, testGroupName, "", passwordHash).
		WillReturnRows(sqlmock.NewRows([]string{"user_id"}).AddRow(testUserID))

	userID, err := service.RegisterUser(
		context.Background(),
		testUsername,
		testEmail,
		testPassword,
		testGroupName,
	)
	if err != nil {
		t.Fatalf("RegisterUser() error = %v", err)
	}
	if userID != testUserID {
		t.Fatalf("RegisterUser() userID = %q, want %q", userID, testUserID)
	}
	if passwordHash.value == testPassword {
		t.Fatal("RegisterUser() stored the plain-text password")
	}
	if !VerifyPassword(testPassword, passwordHash.value) {
		t.Fatal("RegisterUser() stored an invalid password hash")
	}

	assertSQLExpectations(t, mock)
}

func TestRegisterUserValidationErrorDoesNotWriteToDatabase(t *testing.T) {
	service, mock, closeDB := newTestService(t)
	defer closeDB()

	userID, err := service.RegisterUser(
		context.Background(),
		testUsername,
		testEmail,
		"short",
		testGroupName,
	)
	if err == nil {
		t.Fatal("RegisterUser() error = nil, want validation error")
	}
	if userID != "" {
		t.Fatalf("RegisterUser() userID = %q, want empty string", userID)
	}

	assertSQLExpectations(t, mock)
}

func TestRegisterUserReturnsConflictError(t *testing.T) {
	tests := []struct {
		name           string
		constraintName string
		expectedError  error
	}{
		{
			name:           "username already exists",
			constraintName: "users_username_idx",
			expectedError:  ErrUsernameTaken,
		},
		{
			name:           "email already exists",
			constraintName: "users_email_idx",
			expectedError:  ErrEmailTaken,
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			service, mock, closeDB := newTestService(t)
			defer closeDB()

			mock.ExpectQuery(regexp.QuoteMeta(`
				INSERT INTO users (user_id, username, email, group_name, image_url, password_hash)
				VALUES (gen_random_uuid(), $1, $2, $3, $4, $5)
				RETURNING user_id
			`)).
				WithArgs(testUsername, testEmail, testGroupName, "", sqlmock.AnyArg()).
				WillReturnError(&pgconn.PgError{
					Code:           "23505",
					ConstraintName: test.constraintName,
				})

			userID, err := service.RegisterUser(
				context.Background(),
				testUsername,
				testEmail,
				testPassword,
				testGroupName,
			)
			if !errors.Is(err, test.expectedError) {
				t.Fatalf("RegisterUser() error = %v, want %v", err, test.expectedError)
			}
			if userID != "" {
				t.Fatalf("RegisterUser() userID = %q, want empty string", userID)
			}

			assertSQLExpectations(t, mock)
		})
	}
}
func TestLoginUserSuccessWithUsernameOrEmail(t *testing.T) {
	passwordHash, err := HashPassword(testPassword)
	if err != nil {
		t.Fatalf("HashPassword() error = %v", err)
	}

	for _, login := range []string{testUsername, testEmail} {
		t.Run(login, func(t *testing.T) {
			service, mock, closeDB := newTestService(t)
			defer closeDB()

			mock.ExpectQuery(regexp.QuoteMeta(`
				SELECT user_id, username, email, password_hash
				FROM users
				WHERE email = $1 OR username = $1
				LIMIT 1
			`)).
				WithArgs(login).
				WillReturnRows(sqlmock.NewRows(
					[]string{"user_id", "username", "email", "password_hash"},
				).AddRow(testUserID, testUsername, testEmail, passwordHash))

			refreshTokenHash := &stringCapture{}
			refreshExpiresAt := &timeCapture{}
			mock.ExpectExec(regexp.QuoteMeta(`
				INSERT INTO sessions (session_id, user_id, refresh_token_hash, expires_at)
				VALUES (gen_random_uuid(), $1, $2, $3)
			`)).
				WithArgs(testUserID, refreshTokenHash, refreshExpiresAt).
				WillReturnResult(sqlmock.NewResult(1, 1))

			userID, accessToken, refreshToken, err := service.LoginUser(
				context.Background(),
				login,
				testPassword,
			)
			if err != nil {
				t.Fatalf("LoginUser() error = %v", err)
			}
			if userID != testUserID {
				t.Fatalf("LoginUser() userID = %q, want %q", userID, testUserID)
			}

			accessClaims, err := pkg.ValidateAccessToken(accessToken, testAccessSecret)
			if err != nil {
				t.Fatalf("ValidateAccessToken() error = %v", err)
			}
			if accessClaims.Subject != testUserID {
				t.Fatalf("access token subject = %q, want %q", accessClaims.Subject, testUserID)
			}

			refreshClaims, err := pkg.ValidateRefreshToken(refreshToken, testRefreshSecret)
			if err != nil {
				t.Fatalf("ValidateRefreshToken() error = %v", err)
			}
			if refreshClaims.Subject != testUserID {
				t.Fatalf("refresh token subject = %q, want %q", refreshClaims.Subject, testUserID)
			}
			if refreshTokenHash.value != pkg.HashToken(refreshToken) {
				t.Fatal("session contains a hash that does not match the refresh token")
			}
			if refreshExpiresAt.value.IsZero() || !refreshExpiresAt.value.After(time.Now()) {
				t.Fatal("session contains an invalid refresh token expiration time")
			}

			assertSQLExpectations(t, mock)
		})
	}
}

func TestLoginUserRejectsWrongPassword(t *testing.T) {
	service, mock, closeDB := newTestService(t)
	defer closeDB()

	passwordHash, err := HashPassword(testPassword)
	if err != nil {
		t.Fatalf("HashPassword() error = %v", err)
	}

	mock.ExpectQuery(regexp.QuoteMeta(`
		SELECT user_id, username, email, password_hash
		FROM users
		WHERE email = $1 OR username = $1
		LIMIT 1
	`)).
		WithArgs(testUsername).
		WillReturnRows(sqlmock.NewRows(
			[]string{"user_id", "username", "email", "password_hash"},
		).AddRow(testUserID, testUsername, testEmail, passwordHash))

	userID, accessToken, refreshToken, err := service.LoginUser(
		context.Background(),
		testUsername,
		"WrongPassword123",
	)
	if !errors.Is(err, ErrInvalidCredentials) {
		t.Fatalf("LoginUser() error = %v, want ErrInvalidCredentials", err)
	}
	if userID != "" || accessToken != "" || refreshToken != "" {
		t.Fatal("LoginUser() returned user data or tokens for a wrong password")
	}

	assertSQLExpectations(t, mock)
}

func TestRefreshTokensRotatesSession(t *testing.T) {
	service, mock, closeDB := newTestService(t)
	defer closeDB()

	currentRefreshToken, _, err := pkg.CreateRefreshToken(testUserID, testRefreshSecret, time.Hour)
	if err != nil {
		t.Fatalf("CreateRefreshToken() error = %v", err)
	}
	currentTokenHash := pkg.HashToken(currentRefreshToken)

	mock.ExpectQuery(regexp.QuoteMeta(`
		SELECT session_id, user_id, refresh_token_hash, expires_at, created_at, revoked_at
		FROM sessions
		WHERE refresh_token_hash = $1
			AND revoked_at IS NULL
			AND expires_at > CURRENT_TIMESTAMP
	`)).
		WithArgs(currentTokenHash).
		WillReturnRows(sqlmock.NewRows(
			[]string{"session_id", "user_id", "refresh_token_hash", "expires_at", "created_at", "revoked_at"},
		).AddRow(
			"session-123",
			testUserID,
			currentTokenHash,
			time.Now().Add(time.Hour),
			time.Now(),
			nil,
		))

	newTokenHash := &stringCapture{}
	newExpiresAt := &timeCapture{}
	mock.ExpectExec(regexp.QuoteMeta(`
		UPDATE sessions
		SET refresh_token_hash = $1, expires_at = $2
		WHERE refresh_token_hash = $3
			AND revoked_at IS NULL
			AND expires_at > CURRENT_TIMESTAMP
	`)).
		WithArgs(newTokenHash, newExpiresAt, currentTokenHash).
		WillReturnResult(sqlmock.NewResult(0, 1))

	userID, accessToken, refreshToken, err := service.RefreshTokens(
		context.Background(),
		currentRefreshToken,
	)
	if err != nil {
		t.Fatalf("RefreshTokens() error = %v", err)
	}
	if userID != testUserID {
		t.Fatalf("RefreshTokens() userID = %q, want %q", userID, testUserID)
	}
	if refreshToken == currentRefreshToken {
		t.Fatal("RefreshTokens() returned the current refresh token instead of rotating it")
	}
	if _, err := pkg.ValidateAccessToken(accessToken, testAccessSecret); err != nil {
		t.Fatalf("ValidateAccessToken() error = %v", err)
	}
	if _, err := pkg.ValidateRefreshToken(refreshToken, testRefreshSecret); err != nil {
		t.Fatalf("ValidateRefreshToken() error = %v", err)
	}
	if newTokenHash.value != pkg.HashToken(refreshToken) {
		t.Fatal("session contains a hash that does not match the new refresh token")
	}
	if newExpiresAt.value.IsZero() || !newExpiresAt.value.After(time.Now()) {
		t.Fatal("session contains an invalid refresh token expiration time")
	}

	assertSQLExpectations(t, mock)
}

func TestLogoutRevokesSession(t *testing.T) {
	service, mock, closeDB := newTestService(t)
	defer closeDB()

	refreshToken, _, err := pkg.CreateRefreshToken(testUserID, testRefreshSecret, time.Hour)
	if err != nil {
		t.Fatalf("CreateRefreshToken() error = %v", err)
	}
	refreshTokenHash := pkg.HashToken(refreshToken)

	mock.ExpectExec(regexp.QuoteMeta(`
		UPDATE sessions
		SET revoked_at = CURRENT_TIMESTAMP
		WHERE refresh_token_hash = $1
			AND revoked_at IS NULL
			AND expires_at > CURRENT_TIMESTAMP
	`)).
		WithArgs(refreshTokenHash).
		WillReturnResult(sqlmock.NewResult(0, 1))

	if err := service.Logout(context.Background(), refreshToken); err != nil {
		t.Fatalf("Logout() error = %v", err)
	}

	assertSQLExpectations(t, mock)
}

func TestLogoutRejectsInvalidRefreshToken(t *testing.T) {
	service, mock, closeDB := newTestService(t)
	defer closeDB()

	err := service.Logout(context.Background(), "invalid-token")
	if !errors.Is(err, ErrInvalidRefreshToken) {
		t.Fatalf("Logout() error = %v, want ErrInvalidRefreshToken", err)
	}

	assertSQLExpectations(t, mock)
}
func newTestService(t *testing.T) (*AuthService, sqlmock.Sqlmock, func()) {
	t.Helper()

	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock.New() error = %v", err)
	}

	service := Init(
		session_repo.Init(db),
		users_repo.Init(db),
		config.JWT{
			AccessSecret:    testAccessSecret,
			RefreshSecret:   testRefreshSecret,
			AccessTokenTTL:  "15m",
			RefreshTokenTTL: "24h",
		},
	)

	return service, mock, func() {
		mock.ExpectClose()
		if err := db.Close(); err != nil {
			t.Errorf("db.Close() error = %v", err)
		}
	}
}

func assertSQLExpectations(t *testing.T, mock sqlmock.Sqlmock) {
	t.Helper()

	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet SQL expectations: %v", err)
	}
}
