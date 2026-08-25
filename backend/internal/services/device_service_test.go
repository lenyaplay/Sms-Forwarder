package services

import (
	"context"
	"database/sql"
	"testing"
	"time"

	"sms_forwarder/backend/internal/storage"
)

func newTestDeviceService(t *testing.T) (*DeviceService, *sql.DB) {
	db := newTestDB(t)
	return NewDeviceService(db), db
}

func registerUser(t *testing.T, db *sql.DB, login string) int64 {
	t.Helper()
	user, err := storage.CreateUser(context.Background(), db, login, "hash")
	if err != nil {
		t.Fatalf("CreateUser(%s): %v", login, err)
	}
	return user.ID
}

func TestDeviceService_CreateDevice_WithAndWithoutTTL(t *testing.T) {
	svc, db := newTestDeviceService(t)
	ctx := context.Background()
	owner := registerUser(t, db, "owner-no-ttl")

	device, err := svc.CreateDevice(ctx, owner, "No TTL", nil)
	if err != nil {
		t.Fatalf("CreateDevice: %v", err)
	}
	if device.UploadTokenExpiresAt.Valid {
		t.Errorf("expected no expiry, got %v", device.UploadTokenExpiresAt)
	}

	ttl := time.Hour
	device2, err := svc.CreateDevice(ctx, owner, "With TTL", &ttl)
	if err != nil {
		t.Fatalf("CreateDevice with TTL: %v", err)
	}
	if !device2.UploadTokenExpiresAt.Valid {
		t.Errorf("expected an expiry to be set")
	}
	if device.UploadToken == device2.UploadToken {
		t.Errorf("expected distinct upload tokens")
	}
}

func TestDeviceService_RenameDevice_NotOwner(t *testing.T) {
	svc, db := newTestDeviceService(t)
	ctx := context.Background()
	owner := registerUser(t, db, "rename-owner")
	other := registerUser(t, db, "rename-other")

	device, err := svc.CreateDevice(ctx, owner, "Device", nil)
	if err != nil {
		t.Fatalf("CreateDevice: %v", err)
	}

	if _, err := svc.RenameDevice(ctx, other, device.ID, "Hijacked"); err != ErrNotOwner {
		t.Errorf("got err=%v, want ErrNotOwner", err)
	}

	renamed, err := svc.RenameDevice(ctx, owner, device.ID, "New Name")
	if err != nil {
		t.Fatalf("RenameDevice by owner: %v", err)
	}
	if renamed.Name != "New Name" {
		t.Errorf("Name = %q, want %q", renamed.Name, "New Name")
	}
}

func TestDeviceService_DeleteDevice_NotOwnerAndRepeat(t *testing.T) {
	svc, db := newTestDeviceService(t)
	ctx := context.Background()
	owner := registerUser(t, db, "delete-owner")
	other := registerUser(t, db, "delete-other")

	device, err := svc.CreateDevice(ctx, owner, "Device", nil)
	if err != nil {
		t.Fatalf("CreateDevice: %v", err)
	}

	if err := svc.DeleteDevice(ctx, other, device.ID); err != ErrNotOwner {
		t.Errorf("got err=%v, want ErrNotOwner", err)
	}

	if err := svc.DeleteDevice(ctx, owner, device.ID); err != nil {
		t.Fatalf("DeleteDevice: %v", err)
	}

	if err := svc.DeleteDevice(ctx, owner, device.ID); err != ErrDeviceNotFound {
		t.Errorf("repeat delete: got err=%v, want ErrDeviceNotFound", err)
	}
}

func TestDeviceService_ReissueUploadToken(t *testing.T) {
	svc, db := newTestDeviceService(t)
	ctx := context.Background()
	owner := registerUser(t, db, "reissue-owner")

	device, err := svc.CreateDevice(ctx, owner, "Device", nil)
	if err != nil {
		t.Fatalf("CreateDevice: %v", err)
	}

	updated, err := svc.ReissueUploadToken(ctx, owner, device.ID, nil)
	if err != nil {
		t.Fatalf("ReissueUploadToken: %v", err)
	}
	if updated.UploadToken == device.UploadToken {
		t.Errorf("expected a new upload token")
	}
}

func TestDeviceService_DownloadTokens_MultipleAndPointRevoke(t *testing.T) {
	svc, db := newTestDeviceService(t)
	ctx := context.Background()
	owner := registerUser(t, db, "dl-owner")
	viewerFamily := registerUser(t, db, "dl-viewer-family")
	viewerColleague := registerUser(t, db, "dl-viewer-colleague")

	device, err := svc.CreateDevice(ctx, owner, "Device", nil)
	if err != nil {
		t.Fatalf("CreateDevice: %v", err)
	}

	familyLabel := "family"
	familyToken, err := svc.CreateDownloadToken(ctx, owner, device.ID, &familyLabel, nil)
	if err != nil {
		t.Fatalf("CreateDownloadToken family: %v", err)
	}
	colleagueLabel := "colleagues"
	colleagueToken, err := svc.CreateDownloadToken(ctx, owner, device.ID, &colleagueLabel, nil)
	if err != nil {
		t.Fatalf("CreateDownloadToken colleagues: %v", err)
	}

	if _, _, err := svc.AddViewerBinding(ctx, viewerFamily, familyToken.Token); err != nil {
		t.Fatalf("AddViewerBinding family: %v", err)
	}
	if _, _, err := svc.AddViewerBinding(ctx, viewerColleague, colleagueToken.Token); err != nil {
		t.Fatalf("AddViewerBinding colleague: %v", err)
	}

	revokedCount, err := svc.RevokeDownloadToken(ctx, owner, device.ID, colleagueToken.ID)
	if err != nil {
		t.Fatalf("RevokeDownloadToken: %v", err)
	}
	if revokedCount != 1 {
		t.Errorf("revokedCount = %d, want 1", revokedCount)
	}

	// The colleague token no longer works for new bindings.
	if _, _, err := svc.AddViewerBinding(ctx, registerUser(t, db, "dl-viewer-late"), colleagueToken.Token); err != ErrInvalidDownloadToken {
		t.Errorf("got err=%v, want ErrInvalidDownloadToken", err)
	}

	// The family token and its viewer are untouched.
	tokens, err := svc.ListDownloadTokens(ctx, owner, device.ID)
	if err != nil {
		t.Fatalf("ListDownloadTokens: %v", err)
	}
	if len(tokens) != 1 || tokens[0].ID != familyToken.ID {
		t.Fatalf("ListDownloadTokens = %+v, want only the family token", tokens)
	}

	_, role, err := svc.GetDevice(ctx, viewerFamily, device.ID)
	if err != nil {
		t.Fatalf("GetDevice for family viewer: %v", err)
	}
	if role != deviceRoleViewer {
		t.Errorf("role = %q, want %q", role, deviceRoleViewer)
	}
}

func TestDeviceService_AddViewerBinding_Scenarios(t *testing.T) {
	svc, db := newTestDeviceService(t)
	ctx := context.Background()
	owner := registerUser(t, db, "bind-owner")
	viewer := registerUser(t, db, "bind-viewer")

	device, err := svc.CreateDevice(ctx, owner, "Device", nil)
	if err != nil {
		t.Fatalf("CreateDevice: %v", err)
	}

	if _, _, err := svc.AddViewerBinding(ctx, viewer, "unknown-token"); err != ErrInvalidDownloadToken {
		t.Errorf("unknown token: got err=%v, want ErrInvalidDownloadToken", err)
	}

	expiredTTL := -time.Hour
	expiredToken, err := svc.CreateDownloadToken(ctx, owner, device.ID, nil, &expiredTTL)
	if err != nil {
		t.Fatalf("CreateDownloadToken expired: %v", err)
	}
	if _, _, err := svc.AddViewerBinding(ctx, viewer, expiredToken.Token); err != ErrInvalidDownloadToken {
		t.Errorf("expired token: got err=%v, want ErrInvalidDownloadToken", err)
	}

	token, err := svc.CreateDownloadToken(ctx, owner, device.ID, nil, nil)
	if err != nil {
		t.Fatalf("CreateDownloadToken: %v", err)
	}

	if _, _, err := svc.AddViewerBinding(ctx, owner, token.Token); err != ErrSelfBinding {
		t.Errorf("owner self-binding: got err=%v, want ErrSelfBinding", err)
	}

	deviceID, deviceName, err := svc.AddViewerBinding(ctx, viewer, token.Token)
	if err != nil {
		t.Fatalf("AddViewerBinding: %v", err)
	}
	if deviceID != device.ID || deviceName != device.Name {
		t.Errorf("AddViewerBinding = (%d, %q), want (%d, %q)", deviceID, deviceName, device.ID, device.Name)
	}

	if _, _, err := svc.AddViewerBinding(ctx, viewer, token.Token); err != storage.ErrViewerBindingExists {
		t.Errorf("duplicate binding: got err=%v, want ErrViewerBindingExists", err)
	}
}
