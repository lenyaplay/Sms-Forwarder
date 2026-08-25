package handlers

import (
	"context"
	"net/http"
	"strings"

	"sms_forwarder/backend/internal/auth"
)

type contextKey string

const userIDContextKey contextKey = "userID"

// RequireAuth validates the "Authorization: Bearer <access token>" header and
// stores the authenticated user ID in the request context. Not wired to any
// route yet in Milestone 1; reused by protected routes from Milestone 2 onward.
func RequireAuth(jwtSecret string) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			header := r.Header.Get("Authorization")
			token, ok := strings.CutPrefix(header, "Bearer ")
			if !ok || token == "" {
				writeError(w, http.StatusUnauthorized, "missing bearer token")
				return
			}

			userID, err := auth.ParseAccessToken(token, jwtSecret)
			if err != nil {
				writeError(w, http.StatusUnauthorized, "invalid access token")
				return
			}

			ctx := context.WithValue(r.Context(), userIDContextKey, userID)
			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}

// UserIDFromContext extracts the authenticated user ID set by RequireAuth.
func UserIDFromContext(ctx context.Context) (int64, bool) {
	id, ok := ctx.Value(userIDContextKey).(int64)
	return id, ok
}
