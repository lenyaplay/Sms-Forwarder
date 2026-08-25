package storage

import (
	"context"
	"testing"
	"time"
)

func TestSaveAndGetRefreshToken(t *testing.T) {
	db := newTestDB(t)
	ctx := context.Background()

	user, err := CreateUser(ctx, db, "carol", "hash")
	if err != nil {
		t.Fatalf("CreateUser: %v", err)
	}

	expires := time.Now().Add(30 * 24 * time.Hour)
	if err := SaveRefreshToken(ctx, db, user.ID, "hash-of-token", expires); err != nil {
		t.Fatalf("SaveRefreshToken: %v", err)
	}

	rt, err := GetRefreshTokenByHash(ctx, db, "hash-of-token")
	if err != nil {
		t.Fatalf("GetRefreshTokenByHash: %v", err)
	}
	if rt.UserID != user.ID {
		t.Errorf("rt.UserID = %d, want %d", rt.UserID, user.ID)
	}
	if rt.RevokedAt.Valid {
		t.Errorf("expected RevokedAt to be unset for a fresh token")
	}
}

func TestGetRefreshTokenByHash_NotFound(t *testing.T) {
	db := newTestDB(t)
	ctx := context.Background()

	if _, err := GetRefreshTokenByHash(ctx, db, "does-not-exist"); err != ErrRefreshTokenNotFound {
		t.Errorf("got err=%v, want ErrRefreshTokenNotFound", err)
	}
}

func TestRevokeRefreshToken(t *testing.T) {
	db := newTestDB(t)
	ctx := context.Background()

	user, err := CreateUser(ctx, db, "dave", "hash")
	if err != nil {
		t.Fatalf("CreateUser: %v", err)
	}
	if err := SaveRefreshToken(ctx, db, user.ID, "some-hash", time.Now().Add(time.Hour)); err != nil {
		t.Fatalf("SaveRefreshToken: %v", err)
	}

	rt, err := GetRefreshTokenByHash(ctx, db, "some-hash")
	if err != nil {
		t.Fatalf("GetRefreshTokenByHash: %v", err)
	}

	if err := RevokeRefreshToken(ctx, db, rt.ID); err != nil {
		t.Fatalf("RevokeRefreshToken: %v", err)
	}

	after, err := GetRefreshTokenByHash(ctx, db, "some-hash")
	if err != nil {
		t.Fatalf("GetRefreshTokenByHash after revoke: %v", err)
	}
	if !after.RevokedAt.Valid {
		t.Errorf("expected RevokedAt to be set after revocation")
	}
}
