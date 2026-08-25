package services

import (
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"crypto/subtle"
	"database/sql"
	"encoding/hex"
	"errors"
	"time"

	"sms_forwarder/backend/internal/storage"
)

var (
	ErrInvalidUploadToken = errors.New("invalid or expired upload token")
	ErrInvalidSignature   = errors.New("missing or invalid X-Signature")
	ErrMissingSender      = errors.New("from is required")
	ErrMissingText        = errors.New("text is required")
	ErrInvalidDateRange   = errors.New("since must not be after until")
)

// EventPublisher is the narrow interface MessageService needs to broadcast
// newly ingested messages for realtime delivery (see
// docs/specs/0006-realtime-delivery.md). Implemented by *realtime.Hub;
// declared here rather than importing realtime directly to keep the
// dependency direction one-way (services doesn't depend on handlers-adjacent
// transport packages).
type EventPublisher interface {
	Publish(deviceID int64, msg storage.Message)
}

type MessageService struct {
	db        *sql.DB
	publisher EventPublisher
}

func NewMessageService(db *sql.DB, publisher EventPublisher) *MessageService {
	return &MessageService{db: db, publisher: publisher}
}

type IncomingMessage struct {
	From          string
	Text          string
	SentStamp     string
	ReceivedStamp string
	SIM           string
}

// IngestWebhook validates the upload token and, if the device has an
// hmac_secret configured, the X-Signature header (hex HMAC-SHA256 over
// rawBody). It deduplicates by device_id + sha256(rawBody): a repeat of the
// exact same body is not re-inserted, and the original record is returned
// with duplicate=true.
func (s *MessageService) IngestWebhook(ctx context.Context, uploadToken string, rawBody []byte, signature string, msg IncomingMessage) (storage.Message, bool, error) {
	if msg.From == "" {
		return storage.Message{}, false, ErrMissingSender
	}
	if msg.Text == "" {
		return storage.Message{}, false, ErrMissingText
	}

	device, err := storage.GetDeviceByUploadToken(ctx, s.db, uploadToken)
	if errors.Is(err, storage.ErrDeviceNotFound) {
		return storage.Message{}, false, ErrInvalidUploadToken
	}
	if err != nil {
		return storage.Message{}, false, err
	}
	if device.UploadTokenExpiresAt.Valid && device.UploadTokenExpiresAt.Time.Before(time.Now().UTC()) {
		return storage.Message{}, false, ErrInvalidUploadToken
	}

	if device.HMACSecret.Valid && device.HMACSecret.String != "" {
		if !validSignature(device.HMACSecret.String, rawBody, signature) {
			return storage.Message{}, false, ErrInvalidSignature
		}
	}

	bodyHash := sha256.Sum256(rawBody)
	bodyHashHex := hex.EncodeToString(bodyHash[:])

	created, err := storage.CreateMessage(ctx, s.db, device.ID, msg.From, msg.Text,
		nullableString(msg.SentStamp), nullableString(msg.ReceivedStamp), nullableString(msg.SIM), bodyHashHex)
	if errors.Is(err, storage.ErrDuplicateMessage) {
		existing, err := storage.GetMessageByDeviceAndBodyHash(ctx, s.db, device.ID, bodyHashHex)
		if err != nil {
			return storage.Message{}, false, err
		}
		return existing, true, nil
	}
	if err != nil {
		return storage.Message{}, false, err
	}

	s.publisher.Publish(device.ID, created)
	return created, false, nil
}

type ListMessagesInput struct {
	Since    *time.Time
	Until    *time.Time
	BeforeID *int64
	Limit    int
}

// ListMessages returns messages for deviceID visible to userID (owner or
// viewer, checked via deviceSvc.GetDevice), applying pagination and optional
// date filters. Returns storage.ErrDeviceNotFound if the device doesn't
// exist or userID has no relation to it.
func (s *MessageService) ListMessages(ctx context.Context, deviceSvc *DeviceService, userID, deviceID int64, in ListMessagesInput) ([]storage.Message, error) {
	if in.Since != nil && in.Until != nil && in.Since.After(*in.Until) {
		return nil, ErrInvalidDateRange
	}

	if _, _, err := deviceSvc.GetDevice(ctx, userID, deviceID); err != nil {
		return nil, err
	}

	return storage.ListMessagesForDevice(ctx, s.db, storage.ListMessagesParams{
		DeviceID: deviceID,
		BeforeID: in.BeforeID,
		Since:    in.Since,
		Until:    in.Until,
		Limit:    in.Limit,
	})
}

func validSignature(secret string, rawBody []byte, signature string) bool {
	if signature == "" {
		return false
	}
	sig, err := hex.DecodeString(signature)
	if err != nil {
		return false
	}
	mac := hmac.New(sha256.New, []byte(secret))
	mac.Write(rawBody)
	expected := mac.Sum(nil)
	return subtle.ConstantTimeCompare(sig, expected) == 1
}

func nullableString(v string) sql.NullString {
	if v == "" {
		return sql.NullString{}
	}
	return sql.NullString{String: v, Valid: true}
}
