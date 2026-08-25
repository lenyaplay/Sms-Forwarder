package storage

import (
	"context"
	"database/sql"
	"fmt"
	"testing"
	"time"
)

var setupDeviceForTokenTestsCounter int

func setupDeviceForTokenTests(t *testing.T, db *sql.DB) Device {
	t.Helper()
	ctx := context.Background()
	setupDeviceForTokenTestsCounter++
	login := fmt.Sprintf("tok-owner-%s-%d", t.Name(), setupDeviceForTokenTestsCounter)
	owner, err := CreateUser(ctx, db, login, "hash")
	if err != nil {
		t.Fatalf("CreateUser: %v", err)
	}
	device, err := CreateDevice(ctx, db, owner.ID, "Device", "upload-tok-"+login, sql.NullTime{})
	if err != nil {
		t.Fatalf("CreateDevice: %v", err)
	}
	return device
}

func TestCreateDownloadToken_MultiplePerDevice(t *testing.T) {
	db := newTestDB(t)
	ctx := context.Background()
	device := setupDeviceForTokenTests(t, db)

	label1 := "family"
	t1, err := CreateDownloadToken(ctx, db, device.ID, "dl-1", sql.NullString{String: label1, Valid: true}, sql.NullTime{})
	if err != nil {
		t.Fatalf("CreateDownloadToken 1: %v", err)
	}
	t2, err := CreateDownloadToken(ctx, db, device.ID, "dl-2", sql.NullString{}, sql.NullTime{})
	if err != nil {
		t.Fatalf("CreateDownloadToken 2: %v", err)
	}
	if t1.ID == t2.ID {
		t.Fatalf("expected distinct token IDs")
	}

	tokens, err := ListActiveDownloadTokens(ctx, db, device.ID)
	if err != nil {
		t.Fatalf("ListActiveDownloadTokens: %v", err)
	}
	if len(tokens) != 2 {
		t.Fatalf("expected 2 active tokens, got %d", len(tokens))
	}
}

func TestListActiveDownloadTokens_BindingsCount(t *testing.T) {
	db := newTestDB(t)
	ctx := context.Background()
	device := setupDeviceForTokenTests(t, db)

	token, err := CreateDownloadToken(ctx, db, device.ID, "dl-3", sql.NullString{}, sql.NullTime{})
	if err != nil {
		t.Fatalf("CreateDownloadToken: %v", err)
	}

	viewer1, _ := CreateUser(ctx, db, "viewer-a", "hash")
	viewer2, _ := CreateUser(ctx, db, "viewer-b", "hash")
	if _, err := CreateViewerBinding(ctx, db, device.ID, viewer1.ID, token.ID); err != nil {
		t.Fatalf("CreateViewerBinding 1: %v", err)
	}
	if _, err := CreateViewerBinding(ctx, db, device.ID, viewer2.ID, token.ID); err != nil {
		t.Fatalf("CreateViewerBinding 2: %v", err)
	}

	tokens, err := ListActiveDownloadTokens(ctx, db, device.ID)
	if err != nil {
		t.Fatalf("ListActiveDownloadTokens: %v", err)
	}
	if len(tokens) != 1 || tokens[0].BindingsCount != 2 {
		t.Fatalf("ListActiveDownloadTokens = %+v, want 1 token with BindingsCount=2", tokens)
	}
}

func TestListActiveDownloadTokens_ExcludesExpiredAndRevoked(t *testing.T) {
	db := newTestDB(t)
	ctx := context.Background()
	device := setupDeviceForTokenTests(t, db)

	past := sql.NullTime{Time: time.Now().Add(-time.Hour), Valid: true}
	if _, err := CreateDownloadToken(ctx, db, device.ID, "dl-expired", sql.NullString{}, past); err != nil {
		t.Fatalf("CreateDownloadToken expired: %v", err)
	}

	revoked, err := CreateDownloadToken(ctx, db, device.ID, "dl-revoked", sql.NullString{}, sql.NullTime{})
	if err != nil {
		t.Fatalf("CreateDownloadToken revoked: %v", err)
	}
	if _, err := RevokeDownloadToken(ctx, db, device.ID, revoked.ID); err != nil {
		t.Fatalf("RevokeDownloadToken: %v", err)
	}

	active, err := CreateDownloadToken(ctx, db, device.ID, "dl-active", sql.NullString{}, sql.NullTime{})
	if err != nil {
		t.Fatalf("CreateDownloadToken active: %v", err)
	}

	tokens, err := ListActiveDownloadTokens(ctx, db, device.ID)
	if err != nil {
		t.Fatalf("ListActiveDownloadTokens: %v", err)
	}
	if len(tokens) != 1 || tokens[0].ID != active.ID {
		t.Fatalf("ListActiveDownloadTokens = %+v, want only the active token", tokens)
	}
}

func TestGetActiveDownloadTokenByValue(t *testing.T) {
	db := newTestDB(t)
	ctx := context.Background()
	device := setupDeviceForTokenTests(t, db)

	if _, err := CreateDownloadToken(ctx, db, device.ID, "dl-valid", sql.NullString{}, sql.NullTime{}); err != nil {
		t.Fatalf("CreateDownloadToken: %v", err)
	}

	got, err := GetActiveDownloadTokenByValue(ctx, db, "dl-valid")
	if err != nil {
		t.Fatalf("GetActiveDownloadTokenByValue: %v", err)
	}
	if got.DeviceID != device.ID {
		t.Errorf("DeviceID = %d, want %d", got.DeviceID, device.ID)
	}

	if _, err := GetActiveDownloadTokenByValue(ctx, db, "does-not-exist"); err != ErrDownloadTokenNotFound {
		t.Errorf("unknown token: got err=%v, want ErrDownloadTokenNotFound", err)
	}

	past := sql.NullTime{Time: time.Now().Add(-time.Hour), Valid: true}
	if _, err := CreateDownloadToken(ctx, db, device.ID, "dl-expired-2", sql.NullString{}, past); err != nil {
		t.Fatalf("CreateDownloadToken expired: %v", err)
	}
	if _, err := GetActiveDownloadTokenByValue(ctx, db, "dl-expired-2"); err != ErrDownloadTokenNotFound {
		t.Errorf("expired token: got err=%v, want ErrDownloadTokenNotFound", err)
	}

	revoked, err := CreateDownloadToken(ctx, db, device.ID, "dl-revoked-2", sql.NullString{}, sql.NullTime{})
	if err != nil {
		t.Fatalf("CreateDownloadToken revoked: %v", err)
	}
	if _, err := RevokeDownloadToken(ctx, db, device.ID, revoked.ID); err != nil {
		t.Fatalf("RevokeDownloadToken: %v", err)
	}
	if _, err := GetActiveDownloadTokenByValue(ctx, db, "dl-revoked-2"); err != ErrDownloadTokenNotFound {
		t.Errorf("revoked token: got err=%v, want ErrDownloadTokenNotFound", err)
	}
}

func TestRevokeDownloadToken_OnlyRevokesItsOwnBindings(t *testing.T) {
	db := newTestDB(t)
	ctx := context.Background()
	device := setupDeviceForTokenTests(t, db)

	tokenA, err := CreateDownloadToken(ctx, db, device.ID, "dl-a", sql.NullString{}, sql.NullTime{})
	if err != nil {
		t.Fatalf("CreateDownloadToken A: %v", err)
	}
	tokenB, err := CreateDownloadToken(ctx, db, device.ID, "dl-b", sql.NullString{}, sql.NullTime{})
	if err != nil {
		t.Fatalf("CreateDownloadToken B: %v", err)
	}

	viewerA, _ := CreateUser(ctx, db, "viewer-via-a", "hash")
	viewerB, _ := CreateUser(ctx, db, "viewer-via-b", "hash")
	if _, err := CreateViewerBinding(ctx, db, device.ID, viewerA.ID, tokenA.ID); err != nil {
		t.Fatalf("CreateViewerBinding A: %v", err)
	}
	if _, err := CreateViewerBinding(ctx, db, device.ID, viewerB.ID, tokenB.ID); err != nil {
		t.Fatalf("CreateViewerBinding B: %v", err)
	}

	revokedCount, err := RevokeDownloadToken(ctx, db, device.ID, tokenA.ID)
	if err != nil {
		t.Fatalf("RevokeDownloadToken: %v", err)
	}
	if revokedCount != 1 {
		t.Errorf("revokedCount = %d, want 1", revokedCount)
	}

	var remaining int
	db.QueryRowContext(ctx, "SELECT COUNT(1) FROM viewer_bindings WHERE download_token_id = ?", tokenA.ID).Scan(&remaining)
	if remaining != 0 {
		t.Errorf("expected token A's bindings gone, got %d remaining", remaining)
	}
	db.QueryRowContext(ctx, "SELECT COUNT(1) FROM viewer_bindings WHERE download_token_id = ?", tokenB.ID).Scan(&remaining)
	if remaining != 1 {
		t.Errorf("expected token B's binding untouched, got %d remaining", remaining)
	}

	if _, err := RevokeDownloadToken(ctx, db, device.ID, tokenA.ID); err != ErrDownloadTokenNotFound {
		t.Errorf("repeat revoke: got err=%v, want ErrDownloadTokenNotFound", err)
	}

	if _, err := RevokeDownloadToken(ctx, db, device.ID, 99999); err != ErrDownloadTokenNotFound {
		t.Errorf("unknown token: got err=%v, want ErrDownloadTokenNotFound", err)
	}
}

func TestRevokeDownloadToken_RejectsTokenFromAnotherDevice(t *testing.T) {
	db := newTestDB(t)
	ctx := context.Background()
	deviceOne := setupDeviceForTokenTests(t, db)
	deviceTwo := setupDeviceForTokenTests(t, db)

	tokenOnDeviceTwo, err := CreateDownloadToken(ctx, db, deviceTwo.ID, "dl-cross", sql.NullString{}, sql.NullTime{})
	if err != nil {
		t.Fatalf("CreateDownloadToken: %v", err)
	}

	// Attempting to revoke deviceTwo's token while scoped to deviceOne must fail,
	// not silently revoke a token belonging to a different device/owner.
	if _, err := RevokeDownloadToken(ctx, db, deviceOne.ID, tokenOnDeviceTwo.ID); err != ErrDownloadTokenNotFound {
		t.Errorf("cross-device revoke: got err=%v, want ErrDownloadTokenNotFound", err)
	}

	tokens, err := ListActiveDownloadTokens(ctx, db, deviceTwo.ID)
	if err != nil {
		t.Fatalf("ListActiveDownloadTokens: %v", err)
	}
	if len(tokens) != 1 {
		t.Errorf("expected deviceTwo's token to remain active, got %+v", tokens)
	}
}
