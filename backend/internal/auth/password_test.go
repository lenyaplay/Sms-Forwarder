package auth

import "testing"

func TestHashAndCheckPassword(t *testing.T) {
	hash, err := HashPassword("correct-horse")
	if err != nil {
		t.Fatalf("HashPassword: %v", err)
	}
	if hash == "correct-horse" {
		t.Fatalf("hash must not equal plaintext password")
	}

	if !CheckPassword(hash, "correct-horse") {
		t.Errorf("CheckPassword should succeed for the correct password")
	}
	if CheckPassword(hash, "wrong-password") {
		t.Errorf("CheckPassword should fail for the wrong password")
	}
}
