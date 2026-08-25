package storage

import (
	"context"
	"database/sql"
	"testing"
	"time"
)

// setMessageCreatedAt backdates a message's created_at for deterministic
// since/until filter tests (CreateMessage always uses CURRENT_TIMESTAMP).
func setMessageCreatedAt(t *testing.T, db *sql.DB, id int64, ts time.Time) {
	t.Helper()
	if _, err := db.Exec("UPDATE messages SET created_at = ? WHERE id = ?", ts.UTC(), id); err != nil {
		t.Fatalf("setMessageCreatedAt: %v", err)
	}
}

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

func TestListMessagesForDevice_OrderAndPagination(t *testing.T) {
	db := newTestDB(t)
	ctx := context.Background()

	owner, _ := CreateUser(ctx, db, "list-owner1", "hash")
	device, _ := CreateDevice(ctx, db, owner.ID, "Device", "tok-list1", sql.NullTime{})

	var ids []int64
	for i := 0; i < 5; i++ {
		msg, err := CreateMessage(ctx, db, device.ID, "+1234", "hello",
			sql.NullString{}, sql.NullString{}, sql.NullString{}, "list-hash-"+string(rune('a'+i)))
		if err != nil {
			t.Fatalf("CreateMessage %d: %v", i, err)
		}
		ids = append(ids, msg.ID)
	}
	// ids[0..4] created in ascending id order; expect DESC order id[4]..id[0].

	page1, err := ListMessagesForDevice(ctx, db, ListMessagesParams{DeviceID: device.ID, Limit: 2})
	if err != nil {
		t.Fatalf("ListMessagesForDevice page1: %v", err)
	}
	if len(page1) != 2 || page1[0].ID != ids[4] || page1[1].ID != ids[3] {
		t.Fatalf("page1 = %+v, want [%d, %d]", page1, ids[4], ids[3])
	}

	before := page1[len(page1)-1].ID
	page2, err := ListMessagesForDevice(ctx, db, ListMessagesParams{DeviceID: device.ID, BeforeID: &before, Limit: 2})
	if err != nil {
		t.Fatalf("ListMessagesForDevice page2: %v", err)
	}
	if len(page2) != 2 || page2[0].ID != ids[2] || page2[1].ID != ids[1] {
		t.Fatalf("page2 = %+v, want [%d, %d]", page2, ids[2], ids[1])
	}

	before2 := page2[len(page2)-1].ID
	page3, err := ListMessagesForDevice(ctx, db, ListMessagesParams{DeviceID: device.ID, BeforeID: &before2, Limit: 2})
	if err != nil {
		t.Fatalf("ListMessagesForDevice page3: %v", err)
	}
	if len(page3) != 1 || page3[0].ID != ids[0] {
		t.Fatalf("page3 = %+v, want [%d]", page3, ids[0])
	}

	before3 := page3[len(page3)-1].ID
	page4, err := ListMessagesForDevice(ctx, db, ListMessagesParams{DeviceID: device.ID, BeforeID: &before3, Limit: 2})
	if err != nil {
		t.Fatalf("ListMessagesForDevice page4: %v", err)
	}
	if len(page4) != 0 {
		t.Fatalf("page4 = %+v, want empty", page4)
	}
}

func TestListMessagesForDevice_DeviceIsolation(t *testing.T) {
	db := newTestDB(t)
	ctx := context.Background()

	owner, _ := CreateUser(ctx, db, "list-owner2", "hash")
	deviceA, _ := CreateDevice(ctx, db, owner.ID, "Device A", "tok-list2a", sql.NullTime{})
	deviceB, _ := CreateDevice(ctx, db, owner.ID, "Device B", "tok-list2b", sql.NullTime{})

	if _, err := CreateMessage(ctx, db, deviceA.ID, "+1234", "hello", sql.NullString{}, sql.NullString{}, sql.NullString{}, "iso-a"); err != nil {
		t.Fatalf("CreateMessage deviceA: %v", err)
	}
	if _, err := CreateMessage(ctx, db, deviceB.ID, "+1234", "hello", sql.NullString{}, sql.NullString{}, sql.NullString{}, "iso-b"); err != nil {
		t.Fatalf("CreateMessage deviceB: %v", err)
	}

	messages, err := ListMessagesForDevice(ctx, db, ListMessagesParams{DeviceID: deviceA.ID, Limit: 50})
	if err != nil {
		t.Fatalf("ListMessagesForDevice: %v", err)
	}
	if len(messages) != 1 || messages[0].BodyHash != "iso-a" {
		t.Errorf("messages = %+v, want only deviceA's message", messages)
	}
}

func TestListMessagesForDevice_SinceUntilFilter(t *testing.T) {
	db := newTestDB(t)
	ctx := context.Background()

	owner, _ := CreateUser(ctx, db, "list-owner3", "hash")
	device, _ := CreateDevice(ctx, db, owner.ID, "Device", "tok-list3", sql.NullTime{})

	base := time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC)
	var ids []int64
	for i := 0; i < 3; i++ {
		msg, err := CreateMessage(ctx, db, device.ID, "+1234", "hello", sql.NullString{}, sql.NullString{}, sql.NullString{}, "since-hash-"+string(rune('a'+i)))
		if err != nil {
			t.Fatalf("CreateMessage %d: %v", i, err)
		}
		setMessageCreatedAt(t, db, msg.ID, base.Add(time.Duration(i)*24*time.Hour))
		ids = append(ids, msg.ID)
	}
	// ids[0] = day0, ids[1] = day1, ids[2] = day2.

	since := base.Add(24 * time.Hour)
	messages, err := ListMessagesForDevice(ctx, db, ListMessagesParams{DeviceID: device.ID, Since: &since, Limit: 50})
	if err != nil {
		t.Fatalf("ListMessagesForDevice since: %v", err)
	}
	if len(messages) != 2 || messages[0].ID != ids[2] || messages[1].ID != ids[1] {
		t.Fatalf("since filter = %+v, want [%d, %d]", messages, ids[2], ids[1])
	}

	until := base.Add(24 * time.Hour)
	messages, err = ListMessagesForDevice(ctx, db, ListMessagesParams{DeviceID: device.ID, Until: &until, Limit: 50})
	if err != nil {
		t.Fatalf("ListMessagesForDevice until: %v", err)
	}
	if len(messages) != 2 || messages[0].ID != ids[1] || messages[1].ID != ids[0] {
		t.Fatalf("until filter = %+v, want [%d, %d]", messages, ids[1], ids[0])
	}

	messages, err = ListMessagesForDevice(ctx, db, ListMessagesParams{DeviceID: device.ID, Since: &since, Until: &until, Limit: 50})
	if err != nil {
		t.Fatalf("ListMessagesForDevice since+until: %v", err)
	}
	if len(messages) != 1 || messages[0].ID != ids[1] {
		t.Fatalf("since+until filter = %+v, want [%d]", messages, ids[1])
	}
}

// TestListMessagesForDevice_SinceUntilFilter_NaturallyInsertedRow guards
// against a format mismatch between SQLite's CURRENT_TIMESTAMP (used by
// CreateMessage) and the driver's encoding of a bound time.Time parameter
// (used by Since/Until) — if they ever disagreed, this string comparison
// (SQLite has no native date type) would silently exclude rows that should
// match, unlike the other since/until test which only exercises rows
// backdated via a direct UPDATE using the same driver encoding on both ends.
func TestListMessagesForDevice_SinceUntilFilter_NaturallyInsertedRow(t *testing.T) {
	db := newTestDB(t)
	ctx := context.Background()

	owner, _ := CreateUser(ctx, db, "list-owner5", "hash")
	device, _ := CreateDevice(ctx, db, owner.ID, "Device", "tok-list5", sql.NullTime{})

	if _, err := CreateMessage(ctx, db, device.ID, "+1234", "hello", sql.NullString{}, sql.NullString{}, sql.NullString{}, "natural-hash"); err != nil {
		t.Fatalf("CreateMessage: %v", err)
	}

	since := time.Now().UTC().Add(-time.Minute)
	until := time.Now().UTC().Add(time.Minute)
	messages, err := ListMessagesForDevice(ctx, db, ListMessagesParams{DeviceID: device.ID, Since: &since, Until: &until, Limit: 10})
	if err != nil {
		t.Fatalf("ListMessagesForDevice: %v", err)
	}
	if len(messages) != 1 {
		t.Fatalf("messages = %+v, want the naturally-inserted (CURRENT_TIMESTAMP) row to match since/until", messages)
	}
}

func TestListMessagesForDevice_Empty(t *testing.T) {
	db := newTestDB(t)
	ctx := context.Background()

	owner, _ := CreateUser(ctx, db, "list-owner4", "hash")
	device, _ := CreateDevice(ctx, db, owner.ID, "Device", "tok-list4", sql.NullTime{})

	messages, err := ListMessagesForDevice(ctx, db, ListMessagesParams{DeviceID: device.ID, Limit: 50})
	if err != nil {
		t.Fatalf("ListMessagesForDevice: %v", err)
	}
	if len(messages) != 0 {
		t.Errorf("messages = %+v, want empty", messages)
	}
}
