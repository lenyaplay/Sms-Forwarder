package handlers

import (
	"net/http"
	"testing"
	"time"

	"sms_forwarder/backend/internal/config"
	"sms_forwarder/backend/internal/storage"

	"net/http/httptest"
)

// newTestServerWithRateLimit is like newTestServer but with a low,
// deterministic rate limit, for tests that need to actually trip it through
// the real router - the standalone middleware unit tests in
// ratelimit_middleware_test.go exercise RateLimitByUploadToken/RateLimitByIP
// in isolation with a stub handler, but don't prove the production router in
// handlers.go actually wires them onto /webhook and /auth/*.
func newTestServerWithRateLimit(t *testing.T, burst int) *httptest.Server {
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
		RateLimitRPS:    1,
		RateLimitBurst:  burst,
	}

	server := httptest.NewServer(NewRouter(db, cfg))
	t.Cleanup(server.Close)
	return server
}

func TestWebhookRoute_RateLimitedByUploadTokenEndToEnd(t *testing.T) {
	server := newTestServerWithRateLimit(t, 2)
	uploadToken, _ := createDeviceForWebhookTest(t, server.URL, "rl-webhook-owner")

	for i := 0; i < 2; i++ {
		// Distinct text per call - identical bodies would dedup to a 200
		// "duplicate" response (spec 0003) instead of a fresh 201, which
		// would be a false negative for "the rate limiter, not dedup, let
		// this through".
		resp, body := postWebhook(t, server.URL, uploadToken, []byte(`{"from":"+1","text":"x`+string(rune('a'+i))+`"}`), "")
		if resp.StatusCode != http.StatusCreated {
			t.Fatalf("request %d within burst: status = %d, body = %v", i, resp.StatusCode, body)
		}
	}

	resp, body := postWebhook(t, server.URL, uploadToken, []byte(`{"from":"+1","text":"xz"}`), "")
	if resp.StatusCode != http.StatusTooManyRequests {
		t.Fatalf("request beyond burst: status = %d, body = %v, want 429", resp.StatusCode, body)
	}
	if resp.Header.Get("Retry-After") == "" {
		t.Error("429 response missing Retry-After header")
	}
}

func TestAuthLoginRoute_RateLimitedByIPEndToEnd(t *testing.T) {
	// burst 3: registerAndGetAccessToken below issues its own POST
	// /auth/register from the same test client IP, and /auth/register
	// shares the same per-IP budget as /auth/login (spec 0008 assumption 3
	// groups all of register/login/refresh under one IP-keyed limiter) - so
	// 1 token is already spent before the login loop starts.
	server := newTestServerWithRateLimit(t, 3)
	registerAndGetAccessToken(t, server.URL, "rl-auth-owner")

	loginOnce := func() *http.Response {
		resp, _ := doJSON(t, http.MethodPost, server.URL+"/auth/login", "", map[string]interface{}{
			"login":    "rl-auth-owner",
			"password": "wrong-password",
		})
		return resp
	}

	for i := 0; i < 2; i++ {
		resp := loginOnce()
		if resp.StatusCode != http.StatusUnauthorized {
			t.Fatalf("request %d within burst: status = %d, want 401 (wrong password, but not yet rate-limited)", i, resp.StatusCode)
		}
	}

	resp := loginOnce()
	if resp.StatusCode != http.StatusTooManyRequests {
		t.Fatalf("request beyond burst: status = %d, want 429", resp.StatusCode)
	}
	if resp.Header.Get("Retry-After") == "" {
		t.Error("429 response missing Retry-After header")
	}
}

func TestDevicesRoute_NotRateLimited(t *testing.T) {
	// GET /devices is authenticated, already-known-client traffic - spec
	// 0008 assumption 3 explicitly excludes it from rate limiting. Burst of
	// 2 would trip a webhook/auth-style limiter well before 5 requests.
	server := newTestServerWithRateLimit(t, 2)
	token := registerAndGetAccessToken(t, server.URL, "rl-devices-owner")

	for i := 0; i < 5; i++ {
		resp, body := doJSON(t, http.MethodGet, server.URL+"/devices", token, nil)
		if resp.StatusCode != http.StatusOK {
			t.Fatalf("request %d: status = %d, body = %v, want 200 (this route must not be rate-limited)", i, resp.StatusCode, body)
		}
	}
}
