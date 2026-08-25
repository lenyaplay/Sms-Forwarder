package auth

import (
	"testing"
	"time"
)

const testSecret = "test-secret"

func TestGenerateAndParseAccessToken(t *testing.T) {
	token, err := GenerateAccessToken(42, testSecret, time.Minute)
	if err != nil {
		t.Fatalf("GenerateAccessToken: %v", err)
	}

	userID, err := ParseAccessToken(token, testSecret)
	if err != nil {
		t.Fatalf("ParseAccessToken: %v", err)
	}
	if userID != 42 {
		t.Errorf("userID = %d, want 42", userID)
	}
}

func TestParseAccessToken_Expired(t *testing.T) {
	token, err := GenerateAccessToken(1, testSecret, -time.Minute)
	if err != nil {
		t.Fatalf("GenerateAccessToken: %v", err)
	}

	if _, err := ParseAccessToken(token, testSecret); err == nil {
		t.Errorf("expected error for expired token, got nil")
	}
}

func TestParseAccessToken_WrongSecret(t *testing.T) {
	token, err := GenerateAccessToken(1, testSecret, time.Minute)
	if err != nil {
		t.Fatalf("GenerateAccessToken: %v", err)
	}

	if _, err := ParseAccessToken(token, "other-secret"); err == nil {
		t.Errorf("expected error for wrong secret, got nil")
	}
}

func TestParseAccessToken_Garbage(t *testing.T) {
	if _, err := ParseAccessToken("not-a-jwt", testSecret); err == nil {
		t.Errorf("expected error for garbage token, got nil")
	}
}
