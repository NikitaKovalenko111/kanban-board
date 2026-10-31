package pkg

import (
	"testing"
	"time"
)

func TestCreateAndValidateAccessToken(t *testing.T) {
	const (
		userID = "user-123"
		secret = "access-secret"
	)

	token, expiresAt, err := CreateAccessToken(userID, secret, time.Minute)
	if err != nil {
		t.Fatalf("CreateAccessToken() error = %v", err)
	}
	if token == "" {
		t.Fatal("CreateAccessToken() returned an empty token")
	}
	if !expiresAt.After(time.Now()) {
		t.Fatal("CreateAccessToken() returned an expired token")
	}

	claims, err := ValidateAccessToken(token, secret)
	if err != nil {
		t.Fatalf("ValidateAccessToken() error = %v", err)
	}
	if claims.Subject != userID {
		t.Fatalf("ValidateAccessToken() subject = %q, want %q", claims.Subject, userID)
	}
	if claims.TokenType != AccessTokenType {
		t.Fatalf("ValidateAccessToken() token type = %q, want %q", claims.TokenType, AccessTokenType)
	}
}

func TestValidateTokenRejectsWrongSecret(t *testing.T) {
	token, _, err := CreateAccessToken("user-123", "correct-secret", time.Minute)
	if err != nil {
		t.Fatalf("CreateAccessToken() error = %v", err)
	}

	if _, err := ValidateAccessToken(token, "wrong-secret"); err == nil {
		t.Fatal("ValidateAccessToken() accepted a wrong secret")
	}
}

func TestValidateAccessTokenRejectsRefreshToken(t *testing.T) {
	const secret = "shared-secret"

	token, _, err := CreateRefreshToken("user-123", secret, time.Hour)
	if err != nil {
		t.Fatalf("CreateRefreshToken() error = %v", err)
	}

	if _, err := ValidateAccessToken(token, secret); err == nil {
		t.Fatal("ValidateAccessToken() accepted a refresh token")
	}
}
