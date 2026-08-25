package handlers

import (
	"bytes"
	"crypto/hmac"
	"crypto/sha256"
	"database/sql"
	"encoding/hex"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"sms_forwarder/backend/internal/config"
	"sms_forwarder/backend/internal/storage"
)

// newTestServerWithDB is like newTestServer but also returns the underlying
// *sql.DB, needed to set hmac_secret directly (no HTTP endpoint manages it yet).
func newTestServerWithDB(t *testing.T) (*httptest.Server, *sql.DB) {
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

	server := httptest.NewServer(NewRouter(db, cfg))
	t.Cleanup(server.Close)
	return server, db
}

func createDeviceForWebhookTest(t *testing.T, serverURL, ownerLogin string) (uploadToken string, deviceID float64) {
	t.Helper()
	ownerToken := registerAndGetAccessToken(t, serverURL, ownerLogin)
	resp, body := doJSON(t, http.MethodPost, serverURL+"/devices", ownerToken, map[string]interface{}{"name": "Webhook Device"})
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("create device: status = %d, body = %v", resp.StatusCode, body)
	}
	return body["upload_token"].(string), body["id"].(float64)
}

func postWebhook(t *testing.T, serverURL, uploadToken string, rawBody []byte, signature string) (*http.Response, map[string]interface{}) {
	t.Helper()
	req, err := http.NewRequest(http.MethodPost, serverURL+"/webhook?upload_token="+uploadToken, bytes.NewReader(rawBody))
	if err != nil {
		t.Fatalf("NewRequest: %v", err)
	}
	req.Header.Set("Content-Type", "application/json")
	if signature != "" {
		req.Header.Set("X-Signature", signature)
	}

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("POST /webhook: %v", err)
	}
	defer resp.Body.Close()

	var parsed map[string]interface{}
	if resp.ContentLength != 0 {
		_ = json.NewDecoder(resp.Body).Decode(&parsed)
	}
	return resp, parsed
}

func TestWebhook_CreatesMessage(t *testing.T) {
	server := newTestServer(t)
	uploadToken, _ := createDeviceForWebhookTest(t, server.URL, "wh-owner1")

	body := []byte(`{"from":"+1234","text":"hello","sentStamp":"111","receivedStamp":"222","sim":"1"}`)
	resp, parsed := postWebhook(t, server.URL, uploadToken, body, "")
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("status = %d, body = %v", resp.StatusCode, parsed)
	}
	if parsed["id"] == nil {
		t.Errorf("expected non-nil id, got %v", parsed)
	}
	if parsed["duplicate"] != nil {
		t.Errorf("expected no duplicate field on first ingest, got %v", parsed)
	}
}

func TestWebhook_NumericStampsAccepted(t *testing.T) {
	server := newTestServer(t)
	uploadToken, _ := createDeviceForWebhookTest(t, server.URL, "wh-owner-numeric")

	// Gateway App's default template embeds sentStamp/receivedStamp unquoted,
	// so they arrive as JSON numbers, not strings.
	body := []byte(`{"from":"+1234","text":"hello","sentStamp":1787656413252,"receivedStamp":1787656413252,"sim":"sim1"}`)
	resp, parsed := postWebhook(t, server.URL, uploadToken, body, "")
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("status = %d, body = %v, want 201", resp.StatusCode, parsed)
	}
}

func TestWebhook_OptionalFieldsOmitted(t *testing.T) {
	server := newTestServer(t)
	uploadToken, _ := createDeviceForWebhookTest(t, server.URL, "wh-owner-optional")

	resp, body := postWebhook(t, server.URL, uploadToken, []byte(`{"from":"+1234","text":"hello"}`), "")
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("status = %d, body = %v, want 201", resp.StatusCode, body)
	}
}

func TestWebhook_DuplicateBodyReturns200(t *testing.T) {
	server := newTestServer(t)
	uploadToken, _ := createDeviceForWebhookTest(t, server.URL, "wh-owner2")

	body := []byte(`{"from":"+1234","text":"hello"}`)
	firstResp, firstBody := postWebhook(t, server.URL, uploadToken, body, "")
	if firstResp.StatusCode != http.StatusCreated {
		t.Fatalf("first: status = %d, body = %v", firstResp.StatusCode, firstBody)
	}

	secondResp, secondBody := postWebhook(t, server.URL, uploadToken, body, "")
	if secondResp.StatusCode != http.StatusOK {
		t.Fatalf("retry: status = %d, body = %v", secondResp.StatusCode, secondBody)
	}
	if secondBody["duplicate"] != true {
		t.Errorf("expected duplicate=true, got %v", secondBody)
	}
	if secondBody["id"] != firstBody["id"] {
		t.Errorf("retry id = %v, want %v", secondBody["id"], firstBody["id"])
	}
}

func TestWebhook_MissingUploadToken(t *testing.T) {
	server := newTestServer(t)

	req, _ := http.NewRequest(http.MethodPost, server.URL+"/webhook", bytes.NewReader([]byte(`{"from":"+1","text":"x"}`)))
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("POST /webhook: %v", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusBadRequest {
		t.Errorf("status = %d, want 400", resp.StatusCode)
	}
}

func TestWebhook_UnknownUploadToken(t *testing.T) {
	server := newTestServer(t)

	resp, body := postWebhook(t, server.URL, "no-such-token", []byte(`{"from":"+1","text":"x"}`), "")
	if resp.StatusCode != http.StatusUnauthorized {
		t.Errorf("status = %d, body = %v, want 401", resp.StatusCode, body)
	}
}

func TestWebhook_InvalidJSONBody(t *testing.T) {
	server := newTestServer(t)
	uploadToken, _ := createDeviceForWebhookTest(t, server.URL, "wh-owner3")

	resp, body := postWebhook(t, server.URL, uploadToken, []byte(`not json`), "")
	if resp.StatusCode != http.StatusBadRequest {
		t.Errorf("status = %d, body = %v, want 400", resp.StatusCode, body)
	}
}

func TestWebhook_MissingFromOrText(t *testing.T) {
	server := newTestServer(t)
	uploadToken, _ := createDeviceForWebhookTest(t, server.URL, "wh-owner4")

	resp, body := postWebhook(t, server.URL, uploadToken, []byte(`{"from":"","text":"hi"}`), "")
	if resp.StatusCode != http.StatusBadRequest {
		t.Errorf("missing from: status = %d, body = %v, want 400", resp.StatusCode, body)
	}

	resp2, body2 := postWebhook(t, server.URL, uploadToken, []byte(`{"from":"+1","text":""}`), "")
	if resp2.StatusCode != http.StatusBadRequest {
		t.Errorf("missing text: status = %d, body = %v, want 400", resp2.StatusCode, body2)
	}
}

func TestWebhook_ExpiredOrReissuedUploadToken(t *testing.T) {
	server, db := newTestServerWithDB(t)
	uploadToken, deviceID := createDeviceForWebhookTest(t, server.URL, "wh-owner-exp")

	// expired token
	if _, err := db.Exec("UPDATE devices SET upload_token_expires_at = ? WHERE id = ?",
		time.Now().UTC().Add(-time.Hour), int64(deviceID)); err != nil {
		t.Fatalf("set upload_token_expires_at: %v", err)
	}
	resp, body := postWebhook(t, server.URL, uploadToken, []byte(`{"from":"+1","text":"x"}`), "")
	if resp.StatusCode != http.StatusUnauthorized {
		t.Errorf("expired token: status = %d, body = %v, want 401", resp.StatusCode, body)
	}

	// reissued (old token no longer valid) - simulate by overwriting upload_token directly
	if _, err := db.Exec("UPDATE devices SET upload_token = ?, upload_token_expires_at = NULL WHERE id = ?",
		"a-new-token", int64(deviceID)); err != nil {
		t.Fatalf("reissue upload_token: %v", err)
	}
	resp2, body2 := postWebhook(t, server.URL, uploadToken, []byte(`{"from":"+1","text":"x"}`), "")
	if resp2.StatusCode != http.StatusUnauthorized {
		t.Errorf("old (reissued) token: status = %d, body = %v, want 401", resp2.StatusCode, body2)
	}
}

func TestWebhook_HMACRequiredWhenSecretSet(t *testing.T) {
	server, db := newTestServerWithDB(t)
	uploadToken, deviceID := createDeviceForWebhookTest(t, server.URL, "wh-owner5")

	secret := "test-hmac-secret"
	if _, err := db.Exec("UPDATE devices SET hmac_secret = ? WHERE id = ?", secret, int64(deviceID)); err != nil {
		t.Fatalf("set hmac_secret: %v", err)
	}

	body := []byte(`{"from":"+1234","text":"hi"}`)

	// no signature
	resp, respBody := postWebhook(t, server.URL, uploadToken, body, "")
	if resp.StatusCode != http.StatusUnauthorized {
		t.Errorf("no signature: status = %d, body = %v, want 401", resp.StatusCode, respBody)
	}

	// wrong signature
	resp2, respBody2 := postWebhook(t, server.URL, uploadToken, body, "deadbeef")
	if resp2.StatusCode != http.StatusUnauthorized {
		t.Errorf("wrong signature: status = %d, body = %v, want 401", resp2.StatusCode, respBody2)
	}

	// valid signature
	mac := hmac.New(sha256.New, []byte(secret))
	mac.Write(body)
	validSig := hex.EncodeToString(mac.Sum(nil))
	resp3, respBody3 := postWebhook(t, server.URL, uploadToken, body, validSig)
	if resp3.StatusCode != http.StatusCreated {
		t.Errorf("valid signature: status = %d, body = %v, want 201", resp3.StatusCode, respBody3)
	}
}
