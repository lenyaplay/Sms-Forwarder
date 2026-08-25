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

// ListMessagesParams configures ListMessagesForDevice. BeforeID, Since and
// Until are optional (nil = no bound). Limit is not validated here; callers
// must pass a value already checked to be in range.
type ListMessagesParams struct {
	DeviceID int64
	BeforeID *int64
	Since    *time.Time
	Until    *time.Time
	Limit    int
}

// ListMessagesForDevice returns up to p.Limit messages for p.DeviceID, newest
// first (ORDER BY id DESC), using idx_messages_device_id_id (device_id, id)
// for the device_id + keyset (BeforeID) portion of the query.
func ListMessagesForDevice(ctx context.Context, db *sql.DB, p ListMessagesParams) ([]Message, error) {
	query := `SELECT id, device_id, sender, text, sent_stamp, received_stamp, sim, body_hash, created_at
		FROM messages WHERE device_id = ?`
	args := []interface{}{p.DeviceID}

	if p.BeforeID != nil {
		query += " AND id < ?"
		args = append(args, *p.BeforeID)
	}
	if p.Since != nil {
		query += " AND created_at >= ?"
		args = append(args, *p.Since)
	}
	if p.Until != nil {
		query += " AND created_at <= ?"
		args = append(args, *p.Until)
	}
	query += " ORDER BY id DESC LIMIT ?"
	args = append(args, p.Limit)

	rows, err := db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	messages := make([]Message, 0)
	for rows.Next() {
		var m Message
		if err := rows.Scan(&m.ID, &m.DeviceID, &m.Sender, &m.Text, &m.SentStamp, &m.ReceivedStamp, &m.SIM, &m.BodyHash, &m.CreatedAt); err != nil {
			return nil, err
		}
		messages = append(messages, m)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	return messages, nil
}
