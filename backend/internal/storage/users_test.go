package storage

import (
	"context"
	"testing"
)

func TestCreateAndGetUser(t *testing.T) {
	db := newTestDB(t)
	ctx := context.Background()

	created, err := CreateUser(ctx, db, "alice", "hashed-password")
	if err != nil {
		t.Fatalf("CreateUser: %v", err)
	}
	if created.ID == 0 {
		t.Fatalf("expected non-zero ID")
	}

	byLogin, err := GetUserByLogin(ctx, db, "alice")
	if err != nil {
		t.Fatalf("GetUserByLogin: %v", err)
	}
	if byLogin.ID != created.ID || byLogin.PasswordHash != "hashed-password" {
		t.Errorf("GetUserByLogin = %+v, want matching created user", byLogin)
	}

	byID, err := GetUserByID(ctx, db, created.ID)
	if err != nil {
		t.Fatalf("GetUserByID: %v", err)
	}
	if byID.Login != "alice" {
		t.Errorf("GetUserByID.Login = %q, want alice", byID.Login)
	}
}

func TestCreateUser_DuplicateLogin(t *testing.T) {
	db := newTestDB(t)
	ctx := context.Background()

	if _, err := CreateUser(ctx, db, "bob", "hash1"); err != nil {
		t.Fatalf("CreateUser: %v", err)
	}

	if _, err := CreateUser(ctx, db, "bob", "hash2"); err != ErrLoginTaken {
		t.Errorf("CreateUser duplicate login: got err=%v, want ErrLoginTaken", err)
	}
}

func TestGetUserByLogin_NotFound(t *testing.T) {
	db := newTestDB(t)
	ctx := context.Background()

	if _, err := GetUserByLogin(ctx, db, "nobody"); err != ErrUserNotFound {
		t.Errorf("got err=%v, want ErrUserNotFound", err)
	}
}
