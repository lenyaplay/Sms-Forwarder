package services

import (
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"database/sql"
	"encoding/hex"
	"testing"
	"time"

	"sms_forwarder/backend/internal/storage"
)

func newTestMessageService(t *testing.T) (*MessageService, *sql.DB) {
	db := newTestDB(t)
	return NewMessageService(db), db
}

func createTestDevice(t *testing.T, db *sql.DB, ownerLogin, uploadToken string) storage.Device {
	t.Helper()
	owner := registerUser(t, db, ownerLogin)
	device, err := storage.CreateDevice(context.Background(), db, owner, "Device", uploadToken, sql.NullTime{})
	if err != nil {
		t.Fatalf("CreateDevice: %v", err)
	}
	return device
}

func setHMACSecret(t *testing.T, db *sql.DB, deviceID int64, secret string) {
	t.Helper()
	if _, err := db.Exec("UPDATE devices SET hmac_secret = ? WHERE id = ?", secret, deviceID); err != nil {
		t.Fatalf("set hmac_secret: %v", err)
	}
}

func TestMessageService_IngestWebhook_Success(t *testing.T) {
	svc, db := newTestMessageService(t)
	ctx := context.Background()
	device := createTestDevice(t, db, "webhook-owner1", "webhook-tok-1")

	body := []byte(`{"from":"+1234","text":"hi"}`)
	msg, duplicate, err := svc.IngestWebhook(ctx, "webhook-tok-1", body, "", IncomingMessage{From: "+1234", Text: "hi"})
	if err != nil {
		t.Fatalf("IngestWebhook: %v", err)
	}
	if duplicate {
		t.Errorf("expected duplicate=false on first ingest")
	}
	if msg.DeviceID != device.ID || msg.Sender != "+1234" || msg.Text != "hi" {
		t.Errorf("IngestWebhook message = %+v", msg)
	}
}

func TestMessageService_IngestWebhook_UnknownToken(t *testing.T) {
	svc, _ := newTestMessageService(t)
	ctx := context.Background()

	_, _, err := svc.IngestWebhook(ctx, "no-such-token", []byte(`{}`), "", IncomingMessage{From: "+1", Text: "x"})
	if err != ErrInvalidUploadToken {
		t.Errorf("got err=%v, want ErrInvalidUploadToken", err)
	}
}

func TestMessageService_IngestWebhook_ExpiredToken(t *testing.T) {
	svc, db := newTestMessageService(t)
	ctx := context.Background()
	owner := registerUser(t, db, "webhook-owner-exp")
	expired := sql.NullTime{Time: time.Now().UTC().Add(-time.Hour), Valid: true}
	device, err := storage.CreateDevice(ctx, db, owner, "Device", "expired-tok", expired)
	if err != nil {
		t.Fatalf("CreateDevice: %v", err)
	}
	_ = device

	_, _, err = svc.IngestWebhook(ctx, "expired-tok", []byte(`{}`), "", IncomingMessage{From: "+1", Text: "x"})
	if err != ErrInvalidUploadToken {
		t.Errorf("got err=%v, want ErrInvalidUploadToken", err)
	}
}

func TestMessageService_IngestWebhook_MissingFromOrText(t *testing.T) {
	svc, db := newTestMessageService(t)
	ctx := context.Background()
	createTestDevice(t, db, "webhook-owner2", "webhook-tok-2")

	if _, _, err := svc.IngestWebhook(ctx, "webhook-tok-2", []byte(`{}`), "", IncomingMessage{From: "", Text: "hi"}); err != ErrMissingSender {
		t.Errorf("got err=%v, want ErrMissingSender", err)
	}
	if _, _, err := svc.IngestWebhook(ctx, "webhook-tok-2", []byte(`{}`), "", IncomingMessage{From: "+1", Text: ""}); err != ErrMissingText {
		t.Errorf("got err=%v, want ErrMissingText", err)
	}
}

func TestMessageService_IngestWebhook_DedupSameBody(t *testing.T) {
	svc, db := newTestMessageService(t)
	ctx := context.Background()
	createTestDevice(t, db, "webhook-owner3", "webhook-tok-3")

	body := []byte(`{"from":"+1234","text":"hi","receivedStamp":"111"}`)
	first, dup1, err := svc.IngestWebhook(ctx, "webhook-tok-3", body, "", IncomingMessage{From: "+1234", Text: "hi", ReceivedStamp: "111"})
	if err != nil {
		t.Fatalf("IngestWebhook first: %v", err)
	}
	if dup1 {
		t.Errorf("expected duplicate=false on first ingest")
	}

	second, dup2, err := svc.IngestWebhook(ctx, "webhook-tok-3", body, "", IncomingMessage{From: "+1234", Text: "hi", ReceivedStamp: "111"})
	if err != nil {
		t.Fatalf("IngestWebhook retry: %v", err)
	}
	if !dup2 {
		t.Errorf("expected duplicate=true on retry with identical body")
	}
	if second.ID != first.ID {
		t.Errorf("retry returned different message: got ID %d, want %d", second.ID, first.ID)
	}
}

func TestMessageService_IngestWebhook_DifferentBodyNotDeduped(t *testing.T) {
	svc, db := newTestMessageService(t)
	ctx := context.Background()
	createTestDevice(t, db, "webhook-owner4", "webhook-tok-4")

	body1 := []byte(`{"from":"+1234","text":"hi","receivedStamp":"111"}`)
	body2 := []byte(`{"from":"+1234","text":"hi","receivedStamp":"222"}`)

	first, _, err := svc.IngestWebhook(ctx, "webhook-tok-4", body1, "", IncomingMessage{From: "+1234", Text: "hi", ReceivedStamp: "111"})
	if err != nil {
		t.Fatalf("IngestWebhook first: %v", err)
	}
	second, dup, err := svc.IngestWebhook(ctx, "webhook-tok-4", body2, "", IncomingMessage{From: "+1234", Text: "hi", ReceivedStamp: "222"})
	if err != nil {
		t.Fatalf("IngestWebhook second: %v", err)
	}
	if dup {
		t.Errorf("expected duplicate=false for different body")
	}
	if second.ID == first.ID {
		t.Errorf("expected a new message row for a different body")
	}
}

func TestMessageService_IngestWebhook_HMAC(t *testing.T) {
	svc, db := newTestMessageService(t)
	ctx := context.Background()
	device := createTestDevice(t, db, "webhook-owner5", "webhook-tok-5")
	setHMACSecret(t, db, device.ID, "shh-secret")

	body := []byte(`{"from":"+1234","text":"hi"}`)
	mac := hmac.New(sha256.New, []byte("shh-secret"))
	mac.Write(body)
	validSig := hex.EncodeToString(mac.Sum(nil))

	// missing signature
	if _, _, err := svc.IngestWebhook(ctx, "webhook-tok-5", body, "", IncomingMessage{From: "+1234", Text: "hi"}); err != ErrInvalidSignature {
		t.Errorf("missing signature: got err=%v, want ErrInvalidSignature", err)
	}

	// wrong signature
	if _, _, err := svc.IngestWebhook(ctx, "webhook-tok-5", body, "deadbeef", IncomingMessage{From: "+1234", Text: "hi"}); err != ErrInvalidSignature {
		t.Errorf("wrong signature: got err=%v, want ErrInvalidSignature", err)
	}

	// valid signature
	msg, _, err := svc.IngestWebhook(ctx, "webhook-tok-5", body, validSig, IncomingMessage{From: "+1234", Text: "hi"})
	if err != nil {
		t.Fatalf("IngestWebhook valid signature: %v", err)
	}
	if msg.DeviceID != device.ID {
		t.Errorf("IngestWebhook message = %+v", msg)
	}
}

func TestMessageService_IngestWebhook_NoHMACSecretIgnoresSignatureHeader(t *testing.T) {
	svc, db := newTestMessageService(t)
	ctx := context.Background()
	createTestDevice(t, db, "webhook-owner6", "webhook-tok-6")

	body := []byte(`{"from":"+1234","text":"hi"}`)
	if _, _, err := svc.IngestWebhook(ctx, "webhook-tok-6", body, "garbage-not-checked", IncomingMessage{From: "+1234", Text: "hi"}); err != nil {
		t.Errorf("expected success when device has no hmac_secret regardless of signature header, got %v", err)
	}
}
