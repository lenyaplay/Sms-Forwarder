package handlers

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"sms_forwarder/backend/internal/ratelimit"
)

func okHandler() http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	})
}

func TestRateLimitByUploadTokenRejectsBeyondBurst(t *testing.T) {
	limiter := ratelimit.New(1, 2)
	h := RateLimitByUploadToken(limiter)(okHandler())

	for i := 0; i < 2; i++ {
		req := httptest.NewRequest(http.MethodPost, "/webhook?upload_token=tok-a", nil)
		rec := httptest.NewRecorder()
		h.ServeHTTP(rec, req)
		if rec.Code != http.StatusOK {
			t.Fatalf("request %d: got status %d, want 200", i, rec.Code)
		}
	}

	req := httptest.NewRequest(http.MethodPost, "/webhook?upload_token=tok-a", nil)
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	if rec.Code != http.StatusTooManyRequests {
		t.Fatalf("got status %d, want 429", rec.Code)
	}
	if rec.Header().Get("Retry-After") == "" {
		t.Fatal("expected Retry-After header on 429 response")
	}
}

func TestRateLimitByUploadTokenKeysAreIndependent(t *testing.T) {
	limiter := ratelimit.New(1, 1)
	h := RateLimitByUploadToken(limiter)(okHandler())

	req1 := httptest.NewRequest(http.MethodPost, "/webhook?upload_token=tok-a", nil)
	rec1 := httptest.NewRecorder()
	h.ServeHTTP(rec1, req1)
	if rec1.Code != http.StatusOK {
		t.Fatalf("tok-a first request: got %d, want 200", rec1.Code)
	}

	req2 := httptest.NewRequest(http.MethodPost, "/webhook?upload_token=tok-b", nil)
	rec2 := httptest.NewRecorder()
	h.ServeHTTP(rec2, req2)
	if rec2.Code != http.StatusOK {
		t.Fatalf("tok-b should have its own budget: got %d, want 200", rec2.Code)
	}
}

func TestRateLimitByUploadTokenPassesThroughMissingToken(t *testing.T) {
	limiter := ratelimit.New(1, 1)
	h := RateLimitByUploadToken(limiter)(okHandler())

	for i := 0; i < 5; i++ {
		req := httptest.NewRequest(http.MethodPost, "/webhook", nil)
		rec := httptest.NewRecorder()
		h.ServeHTTP(rec, req)
		if rec.Code != http.StatusOK {
			t.Fatalf("request %d with no upload_token should pass through to the handler, got %d", i, rec.Code)
		}
	}
}

func TestRateLimitByIPUsesRemoteAddrWhenProxyNotTrusted(t *testing.T) {
	limiter := ratelimit.New(1, 1)
	h := RateLimitByIP(limiter, nil)(okHandler())

	makeReq := func(remoteAddr, forwardedFor string) *httptest.ResponseRecorder {
		req := httptest.NewRequest(http.MethodPost, "/auth/login", nil)
		req.RemoteAddr = remoteAddr
		if forwardedFor != "" {
			req.Header.Set("X-Forwarded-For", forwardedFor)
		}
		rec := httptest.NewRecorder()
		h.ServeHTTP(rec, req)
		return rec
	}

	if rec := makeReq("1.2.3.4:5555", "9.9.9.9"); rec.Code != http.StatusOK {
		t.Fatalf("first request from 1.2.3.4 should be allowed, got %d", rec.Code)
	}
	// Same RemoteAddr, spoofed X-Forwarded-For claiming to be a different IP.
	// Since no proxy is trusted, the header must be ignored and this must
	// still be rate-limited against 1.2.3.4's exhausted bucket.
	if rec := makeReq("1.2.3.4:6666", "9.9.9.9"); rec.Code != http.StatusTooManyRequests {
		t.Fatalf("spoofed X-Forwarded-For from an untrusted source must not bypass the limit, got %d", rec.Code)
	}
	// A genuinely different client IP gets its own budget.
	if rec := makeReq("5.6.7.8:5555", ""); rec.Code != http.StatusOK {
		t.Fatalf("a different RemoteAddr should have its own budget, got %d", rec.Code)
	}
}

func TestRateLimitByIPUsesForwardedForWhenProxyTrusted(t *testing.T) {
	limiter := ratelimit.New(1, 1)
	h := RateLimitByIP(limiter, []string{"10.0.0.0/8"})(okHandler())

	makeReq := func(remoteAddr, forwardedFor string) *httptest.ResponseRecorder {
		req := httptest.NewRequest(http.MethodPost, "/auth/login", nil)
		req.RemoteAddr = remoteAddr
		if forwardedFor != "" {
			req.Header.Set("X-Forwarded-For", forwardedFor)
		}
		rec := httptest.NewRecorder()
		h.ServeHTTP(rec, req)
		return rec
	}

	// Both requests come from the trusted proxy (10.0.0.1) but carry
	// different real client IPs via X-Forwarded-For - each real client
	// should get its own budget, not share the proxy's.
	if rec := makeReq("10.0.0.1:1111", "1.1.1.1"); rec.Code != http.StatusOK {
		t.Fatalf("client 1.1.1.1 via trusted proxy: got %d, want 200", rec.Code)
	}
	if rec := makeReq("10.0.0.1:2222", "2.2.2.2"); rec.Code != http.StatusOK {
		t.Fatalf("client 2.2.2.2 via trusted proxy: got %d, want 200", rec.Code)
	}
	if rec := makeReq("10.0.0.1:3333", "1.1.1.1"); rec.Code != http.StatusTooManyRequests {
		t.Fatalf("client 1.1.1.1's second request should be rate-limited, got %d", rec.Code)
	}
}

// TestRateLimitByIPIgnoresClientSuppliedForwardedForEntry guards against the
// classic X-Forwarded-For bypass: nginx's $proxy_add_x_forwarded_for APPENDS
// to any X-Forwarded-For the client already sent rather than replacing it,
// so a malicious client can prepend its own fake IP. With a single trusted
// hop, only the LAST entry (the one the proxy itself appended) may be
// trusted - taking the first would let the client pick a fresh fake IP on
// every request and dodge the limit entirely.
func TestRateLimitByIPIgnoresClientSuppliedForwardedForEntry(t *testing.T) {
	limiter := ratelimit.New(1, 1)
	h := RateLimitByIP(limiter, []string{"10.0.0.0/8"})(okHandler())

	makeReq := func(forwardedFor string) *httptest.ResponseRecorder {
		req := httptest.NewRequest(http.MethodPost, "/auth/login", nil)
		req.RemoteAddr = "10.0.0.1:1111" // always arrives via the trusted proxy
		req.Header.Set("X-Forwarded-For", forwardedFor)
		rec := httptest.NewRecorder()
		h.ServeHTTP(rec, req)
		return rec
	}

	// Real client is always 9.9.9.9 (the last, proxy-appended entry); the
	// attacker varies the fake first entry on every request.
	if rec := makeReq("1.1.1.1, 9.9.9.9"); rec.Code != http.StatusOK {
		t.Fatalf("first request from real client 9.9.9.9: got %d, want 200", rec.Code)
	}
	if rec := makeReq("2.2.2.2, 9.9.9.9"); rec.Code != http.StatusTooManyRequests {
		t.Fatalf("varying the spoofed first hop must not reset 9.9.9.9's budget, got %d", rec.Code)
	}
}
