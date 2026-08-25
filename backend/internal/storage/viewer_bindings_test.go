package storage

import (
	"context"
	"database/sql"
	"testing"
)

func TestCreateViewerBinding_DuplicateConflict(t *testing.T) {
	db := newTestDB(t)
	ctx := context.Background()

	owner, _ := CreateUser(ctx, db, "vb-owner", "hash")
	viewer, _ := CreateUser(ctx, db, "vb-viewer", "hash")
	device, err := CreateDevice(ctx, db, owner.ID, "Device", "tok", sql.NullTime{})
	if err != nil {
		t.Fatalf("CreateDevice: %v", err)
	}
	token, err := CreateDownloadToken(ctx, db, device.ID, "dl", sql.NullString{}, sql.NullTime{})
	if err != nil {
		t.Fatalf("CreateDownloadToken: %v", err)
	}

	binding, err := CreateViewerBinding(ctx, db, device.ID, viewer.ID, token.ID)
	if err != nil {
		t.Fatalf("CreateViewerBinding: %v", err)
	}
	if binding.DownloadTokenID != token.ID {
		t.Errorf("DownloadTokenID = %d, want %d", binding.DownloadTokenID, token.ID)
	}

	if _, err := CreateViewerBinding(ctx, db, device.ID, viewer.ID, token.ID); err != ErrViewerBindingExists {
		t.Errorf("duplicate binding: got err=%v, want ErrViewerBindingExists", err)
	}
}
