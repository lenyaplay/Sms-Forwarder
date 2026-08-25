package auth

import "testing"

func TestGenerateRefreshToken_Unique(t *testing.T) {
	a, err := GenerateRefreshToken()
	if err != nil {
		t.Fatalf("GenerateRefreshToken: %v", err)
	}
	b, err := GenerateRefreshToken()
	if err != nil {
		t.Fatalf("GenerateRefreshToken: %v", err)
	}
	if a == b {
		t.Errorf("expected two distinct tokens, got the same value twice")
	}
	if len(a) == 0 {
		t.Errorf("expected non-empty token")
	}
}

func TestHashToken_Deterministic(t *testing.T) {
	h1 := HashToken("some-token")
	h2 := HashToken("some-token")
	if h1 != h2 {
		t.Errorf("HashToken should be deterministic: %q != %q", h1, h2)
	}

	h3 := HashToken("other-token")
	if h1 == h3 {
		t.Errorf("HashToken should differ for different inputs")
	}
}
