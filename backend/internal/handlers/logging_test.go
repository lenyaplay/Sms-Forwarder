package handlers

import (
	"bytes"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"sms_forwarder/backend/internal/config"
	"sms_forwarder/backend/internal/storage"
)

func newTestServerWithLogLevel(t *testing.T, level string) (*httptest.Server, *bytes.Buffer) {
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
	}

	var buf bytes.Buffer
	var lvl slog.Level
	if strings.EqualFold(level, "debug") {
		lvl = slog.LevelDebug
	} else {
		lvl = slog.LevelInfo
	}
	logger := slog.New(slog.NewJSONHandler(&buf, &slog.HandlerOptions{Level: lvl}))

	server := httptest.NewServer(NewRouterWithLogger(db, cfg, logger))
	t.Cleanup(server.Close)
	return server, &buf
}

func TestLogging_InfoLevel_NeverLeaksSecretsOrPII(t *testing.T) {
	server, buf := newTestServerWithLogLevel(t, "info")
	uploadToken, _ := createDeviceForWebhookTest(t, server.URL, "log-owner-info")
	buf.Reset() // isolate the assertions below to the webhook call itself, not device setup

	resp, body := postWebhook(t, server.URL, uploadToken, []byte(`{"from":"+1999888777","text":"secret sms text"}`), "")
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("status = %d, body = %v", resp.StatusCode, body)
	}

	logOutput := buf.String()
	if strings.Contains(logOutput, uploadToken) {
		t.Errorf("info-level log leaked upload_token: %s", logOutput)
	}
	if strings.Contains(logOutput, "+1999888777") || strings.Contains(logOutput, "secret sms text") {
		t.Errorf("info-level log leaked SMS body/sender: %s", logOutput)
	}
	if !strings.Contains(logOutput, `"path":"/webhook"`) || !strings.Contains(logOutput, `"status":201`) {
		t.Errorf("info-level log missing expected fields: %s", logOutput)
	}
	if !strings.Contains(logOutput, "duration_ms") {
		t.Errorf("info-level log missing duration_ms: %s", logOutput)
	}
	if strings.Contains(logOutput, `"user_id"`) {
		t.Errorf("info-level log should not have user_id for unauthenticated POST /webhook: %s", logOutput)
	}
}

func TestLogging_InfoLevel_AuthenticatedRequestHasUserID(t *testing.T) {
	server, buf := newTestServerWithLogLevel(t, "info")
	token := registerAndGetAccessToken(t, server.URL, "log-owner-info-auth")

	resp, body := doJSON(t, http.MethodPost, server.URL+"/devices", token, map[string]interface{}{"name": "Phone"})
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("status = %d, body = %v", resp.StatusCode, body)
	}

	logOutput := buf.String()
	if !strings.Contains(logOutput, `"user_id"`) {
		t.Errorf("info-level log missing user_id for authenticated request: %s", logOutput)
	}
	if strings.Contains(logOutput, token) {
		t.Errorf("info-level log leaked bearer access token: %s", logOutput)
	}
}

func TestLogging_XSignatureHeaderNeverLogged(t *testing.T) {
	server, buf := newTestServerWithLogLevel(t, "debug")
	uploadToken, _ := createDeviceForWebhookTest(t, server.URL, "log-owner-xsig")

	// Device has no hmac_secret, so an arbitrary X-Signature is ignored for
	// validation purposes - what matters here is that its value, sent on
	// every request regardless, never ends up in the log at any level.
	secretSignature := "super-secret-signature-value"
	resp, respBody := postWebhook(t, server.URL, uploadToken, []byte(`{"from":"+1234","text":"hi"}`), secretSignature)
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("status = %d, body = %v", resp.StatusCode, respBody)
	}

	logOutput := buf.String()
	if strings.Contains(logOutput, secretSignature) {
		t.Errorf("log leaked X-Signature header value: %s", logOutput)
	}
}

func TestLogging_DebugLevel_RedactsTokenButShowsRestOfQueryAndBody(t *testing.T) {
	server, buf := newTestServerWithLogLevel(t, "debug")
	uploadToken, _ := createDeviceForWebhookTest(t, server.URL, "log-owner-debug")

	resp, body := postWebhook(t, server.URL, uploadToken, []byte(`{"from":"+1999888777","text":"visible at debug","sim":"1"}`), "")
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("status = %d, body = %v", resp.StatusCode, body)
	}

	logOutput := buf.String()
	if strings.Contains(logOutput, uploadToken) {
		t.Errorf("debug-level log leaked raw upload_token: %s", logOutput)
	}
	if !strings.Contains(logOutput, "[REDACTED]") {
		t.Errorf("debug-level log missing [REDACTED] marker for token: %s", logOutput)
	}
	if !strings.Contains(logOutput, "visible at debug") || !strings.Contains(logOutput, "+1999888777") {
		t.Errorf("debug-level log should show non-secret body fields: %s", logOutput)
	}
}

func TestLogging_DebugLevel_AuthHeadersNeverLogged(t *testing.T) {
	server, buf := newTestServerWithLogLevel(t, "debug")
	token := registerAndGetAccessToken(t, server.URL, "log-owner-auth")

	resp, body := doJSON(t, http.MethodPost, server.URL+"/devices", token, map[string]interface{}{"name": "Phone"})
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("status = %d, body = %v", resp.StatusCode, body)
	}

	logOutput := buf.String()
	if strings.Contains(logOutput, token) {
		t.Errorf("debug-level log leaked bearer access token: %s", logOutput)
	}
	if !strings.Contains(logOutput, `"user_id"`) {
		t.Errorf("expected user_id on authenticated request log: %s", logOutput)
	}
}
