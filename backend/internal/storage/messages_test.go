package storage

import (
	"context"
	"database/sql"
	"testing"
)

func TestCreateAndGetMessage(t *testing.T) {
	db := newTestDB(t)
	ctx := context.Background()

	owner, _ := CreateUser(ctx, db, "msg-owner1", "hash")
	device, _ := CreateDevice(ctx, db, owner.ID, "Device", "tok-msg1", sql.NullTime{})

	msg, err := CreateMessage(ctx, db, device.ID, "+1234", "hello",
		sql.NullString{String: "111", Valid: true}, sql.NullString{String: "222", Valid: true}, sql.NullString{String: "1", Valid: true},
		"hash-1")
	if err != nil {
		t.Fatalf("CreateMessage: %v", err)
	}
	if msg.ID == 0 {
		t.Fatalf("expected non-zero message ID")
	}

	fetched, err := GetMessageByID(ctx, db, msg.ID)
	if err != nil {
		t.Fatalf("GetMessageByID: %v", err)
	}
	if fetched.Sender != "+1234" || fetched.Text != "hello" || fetched.BodyHash != "hash-1" {
		t.Errorf("GetMessageByID = %+v, want matching created message", fetched)
	}
}

func TestCreateMessage_OptionalFieldsAbsentStoreAsNull(t *testing.T) {
	db := newTestDB(t)
	ctx := context.Background()

	owner, _ := CreateUser(ctx, db, "msg-owner-null", "hash")
	device, _ := CreateDevice(ctx, db, owner.ID, "Device", "tok-msg-null", sql.NullTime{})

	msg, err := CreateMessage(ctx, db, device.ID, "+1234", "hello",
		sql.NullString{}, sql.NullString{}, sql.NullString{}, "null-fields-hash")
	if err != nil {
		t.Fatalf("CreateMessage: %v", err)
	}

	fetched, err := GetMessageByID(ctx, db, msg.ID)
	if err != nil {
		t.Fatalf("GetMessageByID: %v", err)
	}
	if fetched.SentStamp.Valid || fetched.ReceivedStamp.Valid || fetched.SIM.Valid {
		t.Errorf("expected SentStamp/ReceivedStamp/SIM to be NULL, got %+v", fetched)
	}
}

func TestGetMessageByID_NotFound(t *testing.T) {
	db := newTestDB(t)
	ctx := context.Background()

	if _, err := GetMessageByID(ctx, db, 9999); err != ErrMessageNotFound {
		t.Errorf("got err=%v, want ErrMessageNotFound", err)
	}
}

func TestCreateMessage_DuplicateBodyHashSameDevice(t *testing.T) {
	db := newTestDB(t)
	ctx := context.Background()

	owner, _ := CreateUser(ctx, db, "msg-owner2", "hash")
	device, _ := CreateDevice(ctx, db, owner.ID, "Device", "tok-msg2", sql.NullTime{})

	if _, err := CreateMessage(ctx, db, device.ID, "+1234", "hello", sql.NullString{}, sql.NullString{}, sql.NullString{}, "dup-hash"); err != nil {
		t.Fatalf("CreateMessage first: %v", err)
	}

	if _, err := CreateMessage(ctx, db, device.ID, "+1234", "hello", sql.NullString{}, sql.NullString{}, sql.NullString{}, "dup-hash"); err != ErrDuplicateMessage {
		t.Errorf("CreateMessage duplicate: got err=%v, want ErrDuplicateMessage", err)
	}

	var count int
	if err := db.QueryRowContext(ctx, "SELECT COUNT(1) FROM messages WHERE device_id = ?", device.ID).Scan(&count); err != nil {
		t.Fatalf("count messages: %v", err)
	}
	if count != 1 {
		t.Errorf("expected exactly 1 row for device after duplicate insert attempt, got %d", count)
	}
}

func TestCreateMessage_SameBodyHashDifferentDevice(t *testing.T) {
	db := newTestDB(t)
	ctx := context.Background()

	owner, _ := CreateUser(ctx, db, "msg-owner3", "hash")
	deviceA, _ := CreateDevice(ctx, db, owner.ID, "Device A", "tok-msg3a", sql.NullTime{})
	deviceB, _ := CreateDevice(ctx, db, owner.ID, "Device B", "tok-msg3b", sql.NullTime{})

	if _, err := CreateMessage(ctx, db, deviceA.ID, "+1234", "hello", sql.NullString{}, sql.NullString{}, sql.NullString{}, "shared-hash"); err != nil {
		t.Fatalf("CreateMessage deviceA: %v", err)
	}
	if _, err := CreateMessage(ctx, db, deviceB.ID, "+1234", "hello", sql.NullString{}, sql.NullString{}, sql.NullString{}, "shared-hash"); err != nil {
		t.Errorf("CreateMessage deviceB: expected success (dedup scoped per device), got %v", err)
	}
}

func TestGetMessageByDeviceAndBodyHash(t *testing.T) {
	db := newTestDB(t)
	ctx := context.Background()

	owner, _ := CreateUser(ctx, db, "msg-owner4", "hash")
	device, _ := CreateDevice(ctx, db, owner.ID, "Device", "tok-msg4", sql.NullTime{})

	created, err := CreateMessage(ctx, db, device.ID, "+1234", "hello", sql.NullString{}, sql.NullString{}, sql.NullString{}, "lookup-hash")
	if err != nil {
		t.Fatalf("CreateMessage: %v", err)
	}

	found, err := GetMessageByDeviceAndBodyHash(ctx, db, device.ID, "lookup-hash")
	if err != nil {
		t.Fatalf("GetMessageByDeviceAndBodyHash: %v", err)
	}
	if found.ID != created.ID {
		t.Errorf("GetMessageByDeviceAndBodyHash = %+v, want ID %d", found, created.ID)
	}

	if _, err := GetMessageByDeviceAndBodyHash(ctx, db, device.ID, "missing-hash"); err != ErrMessageNotFound {
		t.Errorf("got err=%v, want ErrMessageNotFound", err)
	}
}
