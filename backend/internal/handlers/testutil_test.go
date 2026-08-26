package handlers

import (
	"net/http/httptest"
	"testing"
	"time"

	"sms_forwarder/backend/internal/config"
	"sms_forwarder/backend/internal/storage"
)

func newTestServer(t *testing.T) *httptest.Server {
	t.Helper()

	db, err := storage.Open("file::memory:?cache=shared")
	if err != nil {
		t.Fatalf("Open: %v", err)
	}
	t.Cleanup(func() { db.Close() })

	if err := storage.Migrate(db); err != nil {
		t.Fatalf("Migrate: %v", err)
	}

	cfg := config.Config{
		JWTSecret:       "test-secret",
		AccessTokenTTL:  15 * time.Minute,
		RefreshTokenTTL: 30 * 24 * time.Hour,
		// High enough that existing tests' sequential requests never trip the
		// rate limiter incidentally - rate-limiting itself is tested against
		// its own low-limit server in ratelimit_middleware_test.go.
		RateLimitRPS:   1000,
		RateLimitBurst: 1000,
	}

	server := httptest.NewServer(NewRouter(db, cfg))
	t.Cleanup(server.Close)
	return server
}
