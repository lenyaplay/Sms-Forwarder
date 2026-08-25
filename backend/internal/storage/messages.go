package storage

import (
	"context"
	"database/sql"
	"errors"
	"time"
)

var ErrMessageNotFound = errors.New("message not found")
var ErrDuplicateMessage = errors.New("message already recorded for this device")

type Message struct {
	ID            int64
	DeviceID      int64
	Sender        string
	Text          string
	SentStamp     sql.NullString
	ReceivedStamp sql.NullString
	SIM           sql.NullString
	BodyHash      string
	CreatedAt     time.Time
}

// CreateMessage inserts a message. Returns ErrDuplicateMessage if device_id+bodyHash
// was already recorded; callers should look it up with GetMessageByDeviceAndBodyHash
// in that case.
func CreateMessage(ctx context.Context, db *sql.DB, deviceID int64, sender, text string, sentStamp, receivedStamp, sim sql.NullString, bodyHash string) (Message, error) {
	res, err := db.ExecContext(ctx,
		`INSERT INTO messages (device_id, sender, text, sent_stamp, received_stamp, sim, body_hash)
		 VALUES (?, ?, ?, ?, ?, ?, ?)`,
		deviceID, sender, text, sentStamp, receivedStamp, sim, bodyHash)
	if err != nil {
		if isUniqueConstraintErr(err) {
			return Message{}, ErrDuplicateMessage
		}
		return Message{}, err
	}

	id, err := res.LastInsertId()
	if err != nil {
		return Message{}, err
	}

	return GetMessageByID(ctx, db, id)
}

func GetMessageByID(ctx context.Context, db *sql.DB, id int64) (Message, error) {
	var m Message
	err := db.QueryRowContext(ctx,
		`SELECT id, device_id, sender, text, sent_stamp, received_stamp, sim, body_hash, created_at
		 FROM messages WHERE id = ?`, id).
		Scan(&m.ID, &m.DeviceID, &m.Sender, &m.Text, &m.SentStamp, &m.ReceivedStamp, &m.SIM, &m.BodyHash, &m.CreatedAt)
	if errors.Is(err, sql.ErrNoRows) {
		return Message{}, ErrMessageNotFound
	}
	if err != nil {
		return Message{}, err
	}
	return m, nil
}

func GetMessageByDeviceAndBodyHash(ctx context.Context, db *sql.DB, deviceID int64, bodyHash string) (Message, error) {
	var m Message
	err := db.QueryRowContext(ctx,
		`SELECT id, device_id, sender, text, sent_stamp, received_stamp, sim, body_hash, created_at
		 FROM messages WHERE device_id = ? AND body_hash = ?`, deviceID, bodyHash).
		Scan(&m.ID, &m.DeviceID, &m.Sender, &m.Text, &m.SentStamp, &m.ReceivedStamp, &m.SIM, &m.BodyHash, &m.CreatedAt)
	if errors.Is(err, sql.ErrNoRows) {
		return Message{}, ErrMessageNotFound
	}
	if err != nil {
		return Message{}, err
	}
	return m, nil
}
