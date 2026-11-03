package middleware

import (
	"context"
	"database/sql"
	"log/slog"
	"net/http"
	"strings"

	"kanban-board/pkg"
)

type authContextKey uint8

const accessTokenClaimsContextKey authContextKey = iota

type UserActiveChecker func(ctx context.Context, userID string) (bool, error)

func Authorization(accessSecret string, logger *slog.Logger, activeCheckers ...UserActiveChecker) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			tokenString, ok := bearerToken(r.Header.Get("Authorization"))
			if !ok {
				logger.Warn("authorization failed", slog.String("error", "missing or malformed bearer token"))
				pkg.WriteError(w, http.StatusUnauthorized, "invalid_access_token", "Valid access token is required")
				return
			}

			claims, err := pkg.ValidateAccessToken(tokenString, accessSecret)
			if err != nil {
				logger.Warn("authorization failed", slog.Any("error", err))
				pkg.WriteError(w, http.StatusUnauthorized, "invalid_access_token", "Valid access token is required")
				return
			}

			if len(activeCheckers) > 0 && activeCheckers[0] != nil {
				active, checkErr := activeCheckers[0](r.Context(), claims.Subject)
				if checkErr != nil {
					if checkErr == sql.ErrNoRows {
						pkg.WriteError(w, http.StatusUnauthorized, "INVALID_ACCESS_TOKEN", "Токен недействителен")
						return
					}
					logger.Error("authorization user check failed", slog.Any("error", checkErr))
					pkg.WriteError(w, http.StatusInternalServerError, "INTERNAL_ERROR", "Внутренняя ошибка сервера")
					return
				}
				if !active {
					pkg.WriteError(w, http.StatusForbidden, "ACCOUNT_BLOCKED", "Пользователь заблокирован")
					return
				}
			}

			ctx := context.WithValue(r.Context(), accessTokenClaimsContextKey, claims)
			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}

func UserIDFromContext(ctx context.Context) (string, bool) {
	claims, ok := TokenClaimsFromContext(ctx)
	if !ok || claims.Subject == "" {
		return "", false
	}

	return claims.Subject, true
}

func TokenClaimsFromContext(ctx context.Context) (*pkg.TokenClaims, bool) {
	claims, ok := ctx.Value(accessTokenClaimsContextKey).(*pkg.TokenClaims)

	return claims, ok
}

func bearerToken(authorizationHeader string) (string, bool) {
	parts := strings.Fields(authorizationHeader)
	if len(parts) != 2 || !strings.EqualFold(parts[0], "Bearer") || parts[1] == "" {
		return "", false
	}

	return parts[1], true
}
