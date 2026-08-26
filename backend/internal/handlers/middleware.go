package handlers

import (
	"context"
	"net"
	"net/http"
	"strconv"
	"strings"

	"sms_forwarder/backend/internal/auth"
	"sms_forwarder/backend/internal/ratelimit"
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

// rateLimitExceeded writes the 429 response shared by both rate-limit
// middlewares, per docs/specs/0008-security-and-ops.md.
func rateLimitExceeded(w http.ResponseWriter, retryAfterSeconds int) {
	w.Header().Set("Retry-After", strconv.Itoa(retryAfterSeconds))
	writeError(w, http.StatusTooManyRequests, "rate limit exceeded")
}

// RateLimitByUploadToken throttles requests per upload_token query parameter,
// used on the webhook route (spec 0008, assumption 1). Requests with no
// upload_token are passed through unlimited - webhookHandler already rejects
// those with 400, no need to rate-limit an already-guaranteed-bad request.
func RateLimitByUploadToken(limiter *ratelimit.Limiter) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			token := r.URL.Query().Get("upload_token")
			if token != "" && !limiter.Allow(token) {
				rateLimitExceeded(w, 60)
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}

// RateLimitByIP throttles requests per client IP, used on unauthenticated
// auth routes (spec 0008, assumption 1). trustedCIDRs lists proxy ranges
// allowed to set X-Forwarded-For; from anyone else the header is ignored, so
// a client can't spoof its way around the limit by forging it.
func RateLimitByIP(limiter *ratelimit.Limiter, trustedCIDRs []string) func(http.Handler) http.Handler {
	nets := parseCIDRs(trustedCIDRs)
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			ip := clientIP(r, nets)
			if ip != "" && !limiter.Allow(ip) {
				rateLimitExceeded(w, 60)
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}

func parseCIDRs(cidrs []string) []*net.IPNet {
	nets := make([]*net.IPNet, 0, len(cidrs))
	for _, c := range cidrs {
		if _, n, err := net.ParseCIDR(c); err == nil {
			nets = append(nets, n)
		}
	}
	return nets
}

// clientIP returns the request's client IP: RemoteAddr's IP, or the first
// entry of X-Forwarded-For if RemoteAddr's IP falls inside one of the
// trusted proxy ranges.
func clientIP(r *http.Request, trustedNets []*net.IPNet) string {
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		host = r.RemoteAddr
	}
	remote := net.ParseIP(host)
	if remote == nil {
		return host
	}

	trusted := false
	for _, n := range trustedNets {
		if n.Contains(remote) {
			trusted = true
			break
		}
	}
	if !trusted {
		return remote.String()
	}

	fwd := r.Header.Get("X-Forwarded-For")
	if fwd == "" {
		return remote.String()
	}
	// Take the LAST entry, not the first. nginx's $proxy_add_x_forwarded_for
	// appends to any X-Forwarded-For the client already sent rather than
	// replacing it, so with a single trusted hop the last entry is the only
	// one the proxy itself added - anything before it is client-controlled
	// and trusting it would let a client spoof its way around the IP limit
	// by sending its own fake X-Forwarded-For header.
	parts := strings.Split(fwd, ",")
	last := strings.TrimSpace(parts[len(parts)-1])
	if ip := net.ParseIP(last); ip != nil {
		return ip.String()
	}
	return remote.String()
}
