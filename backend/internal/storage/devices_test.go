package storage

import (
	"context"
	"database/sql"
	"testing"
	"time"
)

func TestCreateAndGetDevice(t *testing.T) {
	db := newTestDB(t)
	ctx := context.Background()

	owner, err := CreateUser(ctx, db, "owner1", "hash")
	if err != nil {
		t.Fatalf("CreateUser: %v", err)
	}

	device, err := CreateDevice(ctx, db, owner.ID, "My Phone", "upload-tok-1", sql.NullTime{})
	if err != nil {
		t.Fatalf("CreateDevice: %v", err)
	}
	if device.ID == 0 {
		t.Fatalf("expected non-zero device ID")
	}
	if device.UploadTokenExpiresAt.Valid {
		t.Errorf("expected UploadTokenExpiresAt to be unset")
	}

	fetched, err := GetDeviceByID(ctx, db, device.ID)
	if err != nil {
		t.Fatalf("GetDeviceByID: %v", err)
	}
	if fetched.Name != "My Phone" || fetched.UploadToken != "upload-tok-1" {
		t.Errorf("GetDeviceByID = %+v, want matching created device", fetched)
	}
}

func TestGetDeviceByUploadToken(t *testing.T) {
	db := newTestDB(t)
	ctx := context.Background()

	owner, _ := CreateUser(ctx, db, "owner-tok1", "hash")
	device, err := CreateDevice(ctx, db, owner.ID, "Device", "upload-tok-lookup", sql.NullTime{})
	if err != nil {
		t.Fatalf("CreateDevice: %v", err)
	}

	found, err := GetDeviceByUploadToken(ctx, db, "upload-tok-lookup")
	if err != nil {
		t.Fatalf("GetDeviceByUploadToken: %v", err)
	}
	if found.ID != device.ID {
		t.Errorf("GetDeviceByUploadToken = %+v, want device %d", found, device.ID)
	}

	if _, err := GetDeviceByUploadToken(ctx, db, "unknown-token"); err != ErrDeviceNotFound {
		t.Errorf("got err=%v, want ErrDeviceNotFound", err)
	}
}

func TestGetDeviceByID_NotFound(t *testing.T) {
	db := newTestDB(t)
	ctx := context.Background()

	if _, err := GetDeviceByID(ctx, db, 9999); err != ErrDeviceNotFound {
		t.Errorf("got err=%v, want ErrDeviceNotFound", err)
	}
}

func TestListOwnedAndViewerDevices(t *testing.T) {
	db := newTestDB(t)
	ctx := context.Background()

	owner, _ := CreateUser(ctx, db, "owner2", "hash")
	viewer, _ := CreateUser(ctx, db, "viewer2", "hash")

	device, err := CreateDevice(ctx, db, owner.ID, "Device A", "tok-a", sql.NullTime{})
	if err != nil {
		t.Fatalf("CreateDevice: %v", err)
	}

	owned, err := ListOwnedDevices(ctx, db, owner.ID)
	if err != nil {
		t.Fatalf("ListOwnedDevices: %v", err)
	}
	if len(owned) != 1 || owned[0].ID != device.ID {
		t.Errorf("ListOwnedDevices = %+v, want [device]", owned)
	}

	emptyViewer, err := ListViewerDevices(ctx, db, viewer.ID)
	if err != nil {
		t.Fatalf("ListViewerDevices: %v", err)
	}
	if len(emptyViewer) != 0 {
		t.Errorf("expected no viewer devices yet, got %+v", emptyViewer)
	}

	token, err := CreateDownloadToken(ctx, db, device.ID, "dl-tok", sql.NullString{}, sql.NullTime{})
	if err != nil {
		t.Fatalf("CreateDownloadToken: %v", err)
	}
	if _, err := CreateViewerBinding(ctx, db, device.ID, viewer.ID, token.ID); err != nil {
		t.Fatalf("CreateViewerBinding: %v", err)
	}

	viewerDevices, err := ListViewerDevices(ctx, db, viewer.ID)
	if err != nil {
		t.Fatalf("ListViewerDevices: %v", err)
	}
	if len(viewerDevices) != 1 || viewerDevices[0].ID != device.ID {
		t.Errorf("ListViewerDevices = %+v, want [device]", viewerDevices)
	}
}

func TestListViewerDevices_ExcludesExpiredTokenBinding(t *testing.T) {
	db := newTestDB(t)
	ctx := context.Background()

	owner, _ := CreateUser(ctx, db, "owner-exp-viewer", "hash")
	viewer, _ := CreateUser(ctx, db, "viewer-exp", "hash")

	device, err := CreateDevice(ctx, db, owner.ID, "Device", "tok-exp-viewer", sql.NullTime{})
	if err != nil {
		t.Fatalf("CreateDevice: %v", err)
	}

	expired := sql.NullTime{Time: time.Now().UTC().Add(-time.Hour), Valid: true}
	token, err := CreateDownloadToken(ctx, db, device.ID, "dl-tok-exp", sql.NullString{}, expired)
	if err != nil {
		t.Fatalf("CreateDownloadToken: %v", err)
	}
	if _, err := CreateViewerBinding(ctx, db, device.ID, viewer.ID, token.ID); err != nil {
		t.Fatalf("CreateViewerBinding: %v", err)
	}

	viewerDevices, err := ListViewerDevices(ctx, db, viewer.ID)
	if err != nil {
		t.Fatalf("ListViewerDevices: %v", err)
	}
	if len(viewerDevices) != 0 {
		t.Errorf("ListViewerDevices = %+v, want empty (binding's token expired)", viewerDevices)
	}
}

func TestUpdateDeviceName(t *testing.T) {
	db := newTestDB(t)
	ctx := context.Background()

	owner, _ := CreateUser(ctx, db, "owner3", "hash")
	device, _ := CreateDevice(ctx, db, owner.ID, "Old Name", "tok-b", sql.NullTime{})

	if err := UpdateDeviceName(ctx, db, device.ID, "New Name"); err != nil {
		t.Fatalf("UpdateDeviceName: %v", err)
	}

	fetched, err := GetDeviceByID(ctx, db, device.ID)
	if err != nil {
		t.Fatalf("GetDeviceByID: %v", err)
	}
	if fetched.Name != "New Name" {
		t.Errorf("Name = %q, want %q", fetched.Name, "New Name")
	}

	if err := UpdateDeviceName(ctx, db, 9999, "X"); err != ErrDeviceNotFound {
		t.Errorf("got err=%v, want ErrDeviceNotFound", err)
	}
}

func TestReissueUploadToken(t *testing.T) {
	db := newTestDB(t)
	ctx := context.Background()

	owner, _ := CreateUser(ctx, db, "owner4", "hash")
	device, _ := CreateDevice(ctx, db, owner.ID, "Device", "old-token", sql.NullTime{})

	expiry := sql.NullTime{Time: time.Now().Add(time.Hour), Valid: true}
	if err := ReissueUploadToken(ctx, db, device.ID, "new-token", expiry); err != nil {
		t.Fatalf("ReissueUploadToken: %v", err)
	}

	fetched, err := GetDeviceByID(ctx, db, device.ID)
	if err != nil {
		t.Fatalf("GetDeviceByID: %v", err)
	}
	if fetched.UploadToken != "new-token" {
		t.Errorf("UploadToken = %q, want new-token", fetched.UploadToken)
	}
	if !fetched.UploadTokenExpiresAt.Valid {
		t.Errorf("expected UploadTokenExpiresAt to be set")
	}

	if err := ReissueUploadToken(ctx, db, 9999, "x", sql.NullTime{}); err != ErrDeviceNotFound {
		t.Errorf("got err=%v, want ErrDeviceNotFound", err)
	}
}

func TestDeleteDevice_CascadesMessagesAndBindingsAndTokens(t *testing.T) {
	db := newTestDB(t)
	ctx := context.Background()

	owner, _ := CreateUser(ctx, db, "owner5", "hash")
	viewer, _ := CreateUser(ctx, db, "viewer5", "hash")
	device, _ := CreateDevice(ctx, db, owner.ID, "Device", "tok-c", sql.NullTime{})

	token, err := CreateDownloadToken(ctx, db, device.ID, "dl-tok-c", sql.NullString{}, sql.NullTime{})
	if err != nil {
		t.Fatalf("CreateDownloadToken: %v", err)
	}
	if _, err := CreateViewerBinding(ctx, db, device.ID, viewer.ID, token.ID); err != nil {
		t.Fatalf("CreateViewerBinding: %v", err)
	}
	if _, err := db.ExecContext(ctx,
		"INSERT INTO messages (device_id, sender, text) VALUES (?, ?, ?)", device.ID, "+1234", "hi"); err != nil {
		t.Fatalf("insert message: %v", err)
	}

	if err := DeleteDevice(ctx, db, device.ID); err != nil {
		t.Fatalf("DeleteDevice: %v", err)
	}

	var count int
	db.QueryRowContext(ctx, "SELECT COUNT(1) FROM messages WHERE device_id = ?", device.ID).Scan(&count)
	if count != 0 {
		t.Errorf("expected messages cascaded, got %d remaining", count)
	}
	db.QueryRowContext(ctx, "SELECT COUNT(1) FROM viewer_bindings WHERE device_id = ?", device.ID).Scan(&count)
	if count != 0 {
		t.Errorf("expected viewer_bindings cascaded, got %d remaining", count)
	}
	db.QueryRowContext(ctx, "SELECT COUNT(1) FROM device_download_tokens WHERE device_id = ?", device.ID).Scan(&count)
	if count != 0 {
		t.Errorf("expected device_download_tokens cascaded, got %d remaining", count)
	}

	if err := DeleteDevice(ctx, db, device.ID); err != ErrDeviceNotFound {
		t.Errorf("repeat DeleteDevice: got err=%v, want ErrDeviceNotFound", err)
	}
}
