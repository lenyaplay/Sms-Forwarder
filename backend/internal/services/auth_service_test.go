package services

import (
	"context"
	"testing"
	"time"
)

func newTestAuthService(t *testing.T) *AuthService {
	db := newTestDB(t)
	return NewAuthService(db, "test-secret", 15*time.Minute, 30*24*time.Hour)
}

func TestAuthService_RegisterAndLogin(t *testing.T) {
	svc := newTestAuthService(t)
	ctx := context.Background()

	pair, err := svc.Register(ctx, "alice", "password123")
	if err != nil {
		t.Fatalf("Register: %v", err)
	}
	if pair.AccessToken == "" || pair.RefreshToken == "" {
		t.Fatalf("expected non-empty tokens, got %+v", pair)
	}

	loginPair, err := svc.Login(ctx, "alice", "password123")
	if err != nil {
		t.Fatalf("Login: %v", err)
	}
	if loginPair.AccessToken == "" || loginPair.RefreshToken == "" {
		t.Fatalf("expected non-empty tokens from login, got %+v", loginPair)
	}
}

func TestAuthService_Register_DuplicateLogin(t *testing.T) {
	svc := newTestAuthService(t)
	ctx := context.Background()

	if _, err := svc.Register(ctx, "bob", "password123"); err != nil {
		t.Fatalf("Register: %v", err)
	}
	if _, err := svc.Register(ctx, "bob", "other-password"); err != ErrLoginTaken {
		t.Errorf("got err=%v, want ErrLoginTaken", err)
	}
}

func TestAuthService_Login_WrongPassword(t *testing.T) {
	svc := newTestAuthService(t)
	ctx := context.Background()

	if _, err := svc.Register(ctx, "carol", "password123"); err != nil {
		t.Fatalf("Register: %v", err)
	}
	if _, err := svc.Login(ctx, "carol", "wrong-password"); err != ErrInvalidCredentials {
		t.Errorf("got err=%v, want ErrInvalidCredentials", err)
	}
}

func TestAuthService_Login_UnknownLogin(t *testing.T) {
	svc := newTestAuthService(t)
	ctx := context.Background()

	if _, err := svc.Login(ctx, "nobody", "password123"); err != ErrInvalidCredentials {
		t.Errorf("got err=%v, want ErrInvalidCredentials", err)
	}
}

func TestAuthService_Refresh_RotatesToken(t *testing.T) {
	svc := newTestAuthService(t)
	ctx := context.Background()

	pair, err := svc.Register(ctx, "dave", "password123")
	if err != nil {
		t.Fatalf("Register: %v", err)
	}

	newPair, err := svc.Refresh(ctx, pair.RefreshToken)
	if err != nil {
		t.Fatalf("Refresh: %v", err)
	}
	if newPair.RefreshToken == pair.RefreshToken {
		t.Errorf("expected a rotated refresh token, got the same one back")
	}

	// The old refresh token must no longer work.
	if _, err := svc.Refresh(ctx, pair.RefreshToken); err != ErrInvalidRefreshToken {
		t.Errorf("reusing rotated refresh token: got err=%v, want ErrInvalidRefreshToken", err)
	}

	// The new one should still work.
	if _, err := svc.Refresh(ctx, newPair.RefreshToken); err != nil {
		t.Errorf("Refresh with new token: %v", err)
	}
}

func TestAuthService_Refresh_InvalidToken(t *testing.T) {
	svc := newTestAuthService(t)
	ctx := context.Background()

	if _, err := svc.Refresh(ctx, "not-a-real-token"); err != ErrInvalidRefreshToken {
		t.Errorf("got err=%v, want ErrInvalidRefreshToken", err)
	}
}

func TestAuthService_Logout(t *testing.T) {
	svc := newTestAuthService(t)
	ctx := context.Background()

	pair, err := svc.Register(ctx, "erin", "password123")
	if err != nil {
		t.Fatalf("Register: %v", err)
	}

	if err := svc.Logout(ctx, pair.RefreshToken); err != nil {
		t.Fatalf("Logout: %v", err)
	}

	if _, err := svc.Refresh(ctx, pair.RefreshToken); err != ErrInvalidRefreshToken {
		t.Errorf("after logout, Refresh: got err=%v, want ErrInvalidRefreshToken", err)
	}

	// Logout is idempotent.
	if err := svc.Logout(ctx, pair.RefreshToken); err != nil {
		t.Errorf("second Logout call should not error, got %v", err)
	}
}
