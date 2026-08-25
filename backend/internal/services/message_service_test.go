package services

import (
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"database/sql"
	"encoding/hex"
	"sync"
	"testing"
	"time"

	"sms_forwarder/backend/internal/storage"
)

// fakePublisher is a test double for EventPublisher, recording every
// Publish call so tests can assert IngestWebhook publishes on a fresh
// insert and not on a deduplicated retry.
type fakePublisher struct {
	mu     sync.Mutex
	events []publishedEvent
}

type publishedEvent struct {
	DeviceID int64
	Msg      storage.Message
}

func (p *fakePublisher) Publish(deviceID int64, msg storage.Message) {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.events = append(p.events, publishedEvent{DeviceID: deviceID, Msg: msg})
}

func (p *fakePublisher) count() int {
	p.mu.Lock()
	defer p.mu.Unlock()
	return len(p.events)
}

func newTestMessageService(t *testing.T) (*MessageService, *sql.DB) {
	db := newTestDB(t)
	return NewMessageService(db, &fakePublisher{}), db
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

func TestMessageService_IngestWebhook_PublishesOnFreshInsert(t *testing.T) {
	db := newTestDB(t)
	pub := &fakePublisher{}
	svc := NewMessageService(db, pub)
	ctx := context.Background()
	device := createTestDevice(t, db, "publish-owner1", "publish-tok-1")

	msg, _, err := svc.IngestWebhook(ctx, "publish-tok-1", []byte(`{"from":"+1234","text":"hi"}`), "", IncomingMessage{From: "+1234", Text: "hi"})
	if err != nil {
		t.Fatalf("IngestWebhook: %v", err)
	}

	if pub.count() != 1 {
		t.Fatalf("publisher.count() = %d, want 1", pub.count())
	}
	if pub.events[0].DeviceID != device.ID || pub.events[0].Msg.ID != msg.ID {
		t.Errorf("published event = %+v, want DeviceID=%d Msg.ID=%d", pub.events[0], device.ID, msg.ID)
	}
}

func TestMessageService_IngestWebhook_DoesNotPublishOnDuplicate(t *testing.T) {
	db := newTestDB(t)
	pub := &fakePublisher{}
	svc := NewMessageService(db, pub)
	ctx := context.Background()
	createTestDevice(t, db, "publish-owner2", "publish-tok-2")

	body := []byte(`{"from":"+1234","text":"hi"}`)
	if _, _, err := svc.IngestWebhook(ctx, "publish-tok-2", body, "", IncomingMessage{From: "+1234", Text: "hi"}); err != nil {
		t.Fatalf("IngestWebhook first: %v", err)
	}
	if pub.count() != 1 {
		t.Fatalf("after first ingest, publisher.count() = %d, want 1", pub.count())
	}

	if _, dup, err := svc.IngestWebhook(ctx, "publish-tok-2", body, "", IncomingMessage{From: "+1234", Text: "hi"}); err != nil || !dup {
		t.Fatalf("IngestWebhook retry: err=%v, duplicate=%v", err, dup)
	}
	if pub.count() != 1 {
		t.Errorf("after duplicate retry, publisher.count() = %d, want still 1 (no re-publish)", pub.count())
	}
}

func TestMessageService_ListMessages_OwnerSeesMessages(t *testing.T) {
	svc, db := newTestMessageService(t)
	deviceSvc := NewDeviceService(db)
	ctx := context.Background()
	owner := registerUser(t, db, "list-svc-owner1")

	device, err := deviceSvc.CreateDevice(ctx, owner, "Device", nil)
	if err != nil {
		t.Fatalf("CreateDevice: %v", err)
	}
	if _, _, err := svc.IngestWebhook(ctx, device.UploadToken, []byte(`{"from":"+1","text":"hi"}`), "", IncomingMessage{From: "+1", Text: "hi"}); err != nil {
		t.Fatalf("IngestWebhook: %v", err)
	}

	messages, err := svc.ListMessages(ctx, deviceSvc, owner, device.ID, ListMessagesInput{Limit: 50})
	if err != nil {
		t.Fatalf("ListMessages: %v", err)
	}
	if len(messages) != 1 {
		t.Fatalf("messages = %+v, want 1", messages)
	}
}

func TestMessageService_ListMessages_ViewerWithBindingSeesMessages(t *testing.T) {
	svc, db := newTestMessageService(t)
	deviceSvc := NewDeviceService(db)
	ctx := context.Background()
	owner := registerUser(t, db, "list-svc-owner2")
	viewer := registerUser(t, db, "list-svc-viewer2")

	device, err := deviceSvc.CreateDevice(ctx, owner, "Device", nil)
	if err != nil {
		t.Fatalf("CreateDevice: %v", err)
	}
	token, err := deviceSvc.CreateDownloadToken(ctx, owner, device.ID, nil, nil)
	if err != nil {
		t.Fatalf("CreateDownloadToken: %v", err)
	}
	if _, _, err := deviceSvc.AddViewerBinding(ctx, viewer, token.Token); err != nil {
		t.Fatalf("AddViewerBinding: %v", err)
	}
	if _, _, err := svc.IngestWebhook(ctx, device.UploadToken, []byte(`{"from":"+1","text":"hi"}`), "", IncomingMessage{From: "+1", Text: "hi"}); err != nil {
		t.Fatalf("IngestWebhook: %v", err)
	}

	messages, err := svc.ListMessages(ctx, deviceSvc, viewer, device.ID, ListMessagesInput{Limit: 50})
	if err != nil {
		t.Fatalf("ListMessages: %v", err)
	}
	if len(messages) != 1 {
		t.Fatalf("messages = %+v, want 1", messages)
	}
}

func TestMessageService_ListMessages_ViewerLosesAccessWhenTokenExpires(t *testing.T) {
	svc, db := newTestMessageService(t)
	deviceSvc := NewDeviceService(db)
	ctx := context.Background()
	owner := registerUser(t, db, "list-svc-owner5")
	viewer := registerUser(t, db, "list-svc-viewer5")

	device, err := deviceSvc.CreateDevice(ctx, owner, "Device", nil)
	if err != nil {
		t.Fatalf("CreateDevice: %v", err)
	}
	token, err := deviceSvc.CreateDownloadToken(ctx, owner, device.ID, nil, nil)
	if err != nil {
		t.Fatalf("CreateDownloadToken: %v", err)
	}
	if _, _, err := deviceSvc.AddViewerBinding(ctx, viewer, token.Token); err != nil {
		t.Fatalf("AddViewerBinding: %v", err)
	}
	if _, _, err := svc.IngestWebhook(ctx, device.UploadToken, []byte(`{"from":"+1","text":"hi"}`), "", IncomingMessage{From: "+1", Text: "hi"}); err != nil {
		t.Fatalf("IngestWebhook: %v", err)
	}

	if _, err := db.Exec("UPDATE device_download_tokens SET expires_at = ? WHERE id = ?", time.Now().UTC().Add(-time.Hour), token.ID); err != nil {
		t.Fatalf("backdate token: %v", err)
	}

	if _, err := svc.ListMessages(ctx, deviceSvc, viewer, device.ID, ListMessagesInput{Limit: 50}); err != storage.ErrDeviceNotFound {
		t.Errorf("got err=%v, want storage.ErrDeviceNotFound (token expired)", err)
	}
}

func TestMessageService_ListMessages_UnrelatedUserNotFound(t *testing.T) {
	svc, db := newTestMessageService(t)
	deviceSvc := NewDeviceService(db)
	ctx := context.Background()
	owner := registerUser(t, db, "list-svc-owner3")
	stranger := registerUser(t, db, "list-svc-stranger3")

	device, err := deviceSvc.CreateDevice(ctx, owner, "Device", nil)
	if err != nil {
		t.Fatalf("CreateDevice: %v", err)
	}

	if _, err := svc.ListMessages(ctx, deviceSvc, stranger, device.ID, ListMessagesInput{Limit: 50}); err != storage.ErrDeviceNotFound {
		t.Errorf("got err=%v, want storage.ErrDeviceNotFound", err)
	}
}

func TestMessageService_ListMessages_InvalidDateRange(t *testing.T) {
	svc, db := newTestMessageService(t)
	deviceSvc := NewDeviceService(db)
	ctx := context.Background()
	owner := registerUser(t, db, "list-svc-owner4")

	device, err := deviceSvc.CreateDevice(ctx, owner, "Device", nil)
	if err != nil {
		t.Fatalf("CreateDevice: %v", err)
	}

	since := time.Now().UTC()
	until := since.Add(-time.Hour)
	if _, err := svc.ListMessages(ctx, deviceSvc, owner, device.ID, ListMessagesInput{Since: &since, Until: &until, Limit: 50}); err != ErrInvalidDateRange {
		t.Errorf("got err=%v, want ErrInvalidDateRange", err)
	}
}
