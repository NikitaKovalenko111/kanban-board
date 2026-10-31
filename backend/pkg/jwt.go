package pkg

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

const (
	AccessTokenType  = "access"
	RefreshTokenType = "refresh"
	tokenIssuer      = "kanban-board"
)

type TokenClaims struct {
	TokenType string `json:"token_type"`
	jwt.RegisteredClaims
}

func CreateAccessToken(userID, secret string, ttl time.Duration) (string, time.Time, error) {
	return createToken(userID, AccessTokenType, secret, ttl)
}

func CreateRefreshToken(userID, secret string, ttl time.Duration) (string, time.Time, error) {
	return createToken(userID, RefreshTokenType, secret, ttl)
}

func ValidateAccessToken(tokenString, secret string) (*TokenClaims, error) {
	return validateToken(tokenString, AccessTokenType, secret)
}

func ValidateRefreshToken(tokenString, secret string) (*TokenClaims, error) {
	return validateToken(tokenString, RefreshTokenType, secret)
}

func HashToken(token string) string {
	hash := sha256.Sum256([]byte(token))

	return hex.EncodeToString(hash[:])
}

func createToken(userID, tokenType, secret string, ttl time.Duration) (string, time.Time, error) {
	if userID == "" {
		return "", time.Time{}, errors.New("user id is required")
	}
	if secret == "" {
		return "", time.Time{}, errors.New("token secret is required")
	}
	if ttl <= 0 {
		return "", time.Time{}, errors.New("token ttl must be positive")
	}

	now := time.Now().UTC()
	expiresAt := now.Add(ttl)
	tokenID, err := generateTokenID()
	if err != nil {
		return "", time.Time{}, err
	}

	claims := TokenClaims{
		TokenType: tokenType,
		RegisteredClaims: jwt.RegisteredClaims{
			Issuer:    tokenIssuer,
			Subject:   userID,
			ExpiresAt: jwt.NewNumericDate(expiresAt),
			IssuedAt:  jwt.NewNumericDate(now),
			NotBefore: jwt.NewNumericDate(now),
			ID:        tokenID,
		},
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	signedToken, err := token.SignedString([]byte(secret))
	if err != nil {
		return "", time.Time{}, fmt.Errorf("failed to sign %s token: %w", tokenType, err)
	}

	return signedToken, expiresAt, nil
}

func validateToken(tokenString, expectedType, secret string) (*TokenClaims, error) {
	if tokenString == "" {
		return nil, errors.New("token is required")
	}
	if secret == "" {
		return nil, errors.New("token secret is required")
	}

	claims := &TokenClaims{}
	token, err := jwt.ParseWithClaims(
		tokenString,
		claims,
		func(token *jwt.Token) (any, error) {
			return []byte(secret), nil
		},
		jwt.WithValidMethods([]string{jwt.SigningMethodHS256.Alg()}),
		jwt.WithIssuer(tokenIssuer),
		jwt.WithExpirationRequired(),
	)
	if err != nil {
		return nil, fmt.Errorf("invalid %s token: %w", expectedType, err)
	}
	if !token.Valid {
		return nil, fmt.Errorf("invalid %s token", expectedType)
	}
	if claims.TokenType != expectedType {
		return nil, fmt.Errorf("invalid token type: expected %s", expectedType)
	}
	if claims.Subject == "" {
		return nil, errors.New("token subject is required")
	}

	return claims, nil
}

func generateTokenID() (string, error) {
	bytes := make([]byte, 16)
	if _, err := rand.Read(bytes); err != nil {
		return "", fmt.Errorf("failed to generate token id: %w", err)
	}

	return hex.EncodeToString(bytes), nil
}
