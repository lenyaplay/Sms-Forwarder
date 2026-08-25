package handlers

import (
	"context"
	"net/http"
	"strings"

	"sms_forwarder/backend/internal/auth"
)

type contextKey string

const userIDContextKey contextKey = "userID"
const userIDHolderContextKey contextKey = "userIDHolder"

// userIDHolder lets a middleware wrapping RequireAuth from the outside (e.g.
// request logging) observe the authenticated user ID after the inner handler
// runs. Plain context values set by RequireAuth don't work for this: they're
// attached to a new *http.Request created by r.WithContext and passed only to
// handlers further down the chain, never back up to an outer *http.Request
// variable. The holder is a pointer, so mutating the value it points to is
// visible to whoever attached it, regardless of how deep it's mutated.
type userIDHolder struct {
	id int64
	ok bool
}

// withUserIDHolder attaches a fresh holder to the request context and
// returns the updated request along with the holder to read after
// next.ServeHTTP returns.
func withUserIDHolder(r *http.Request) (*http.Request, *userIDHolder) {
	h := &userIDHolder{}
	return r.WithContext(context.WithValue(r.Context(), userIDHolderContextKey, h)), h
}

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
			if holder, ok := ctx.Value(userIDHolderContextKey).(*userIDHolder); ok {
				holder.id = userID
				holder.ok = true
			}
			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}

// UserIDFromContext extracts the authenticated user ID set by RequireAuth.
func UserIDFromContext(ctx context.Context) (int64, bool) {
	id, ok := ctx.Value(userIDContextKey).(int64)
	return id, ok
}
