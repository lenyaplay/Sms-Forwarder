package handlers

import (
	"bytes"
	"encoding/json"
	"net/http"
	"testing"
)

func postJSON(t *testing.T, url string, body map[string]string) (*http.Response, map[string]string) {
	t.Helper()

	buf, err := json.Marshal(body)
	if err != nil {
		t.Fatalf("marshal request body: %v", err)
	}

	resp, err := http.Post(url, "application/json", bytes.NewReader(buf))
	if err != nil {
		t.Fatalf("POST %s: %v", url, err)
	}
	defer resp.Body.Close()

	var parsed map[string]string
	if err := json.NewDecoder(resp.Body).Decode(&parsed); err != nil {
		t.Fatalf("decode response body: %v", err)
	}
	return resp, parsed
}

func TestRegister_Success(t *testing.T) {
	server := newTestServer(t)

	resp, body := postJSON(t, server.URL+"/auth/register", map[string]string{
		"login": "alice", "password": "password123",
	})

	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("status = %d, want 201; body = %v", resp.StatusCode, body)
	}
	if body["access_token"] == "" || body["refresh_token"] == "" {
		t.Errorf("expected non-empty tokens, got %v", body)
	}
}

func TestRegister_DuplicateLogin(t *testing.T) {
	server := newTestServer(t)

	postJSON(t, server.URL+"/auth/register", map[string]string{"login": "bob", "password": "password123"})
	resp, body := postJSON(t, server.URL+"/auth/register", map[string]string{"login": "bob", "password": "password123"})

	if resp.StatusCode != http.StatusConflict {
		t.Fatalf("status = %d, want 409; body = %v", resp.StatusCode, body)
	}
}

func TestLogin_WrongPassword(t *testing.T) {
	server := newTestServer(t)

	postJSON(t, server.URL+"/auth/register", map[string]string{"login": "carol", "password": "password123"})
	resp, body := postJSON(t, server.URL+"/auth/login", map[string]string{"login": "carol", "password": "wrong-password"})

	if resp.StatusCode != http.StatusUnauthorized {
		t.Fatalf("status = %d, want 401; body = %v", resp.StatusCode, body)
	}
}

func TestLogin_Success(t *testing.T) {
	server := newTestServer(t)

	postJSON(t, server.URL+"/auth/register", map[string]string{"login": "dave", "password": "password123"})
	resp, body := postJSON(t, server.URL+"/auth/login", map[string]string{"login": "dave", "password": "password123"})

	if resp.StatusCode != http.StatusOK {
		t.Fatalf("status = %d, want 200; body = %v", resp.StatusCode, body)
	}
	if body["access_token"] == "" || body["refresh_token"] == "" {
		t.Errorf("expected non-empty tokens, got %v", body)
	}
}

func TestRefresh_RotatesAndInvalidatesOldToken(t *testing.T) {
	server := newTestServer(t)

	_, reg := postJSON(t, server.URL+"/auth/register", map[string]string{"login": "erin", "password": "password123"})

	refreshResp, refreshBody := postJSON(t, server.URL+"/auth/refresh", map[string]string{"refresh_token": reg["refresh_token"]})
	if refreshResp.StatusCode != http.StatusOK {
		t.Fatalf("refresh status = %d, want 200; body = %v", refreshResp.StatusCode, refreshBody)
	}
	if refreshBody["refresh_token"] == reg["refresh_token"] {
		t.Errorf("expected a rotated refresh token")
	}

	reuseResp, reuseBody := postJSON(t, server.URL+"/auth/refresh", map[string]string{"refresh_token": reg["refresh_token"]})
	if reuseResp.StatusCode != http.StatusUnauthorized {
		t.Fatalf("reusing old refresh token: status = %d, want 401; body = %v", reuseResp.StatusCode, reuseBody)
	}
}

func TestRefresh_InvalidToken(t *testing.T) {
	server := newTestServer(t)

	resp, body := postJSON(t, server.URL+"/auth/refresh", map[string]string{"refresh_token": "garbage"})
	if resp.StatusCode != http.StatusUnauthorized {
		t.Fatalf("status = %d, want 401; body = %v", resp.StatusCode, body)
	}
}

func TestLogout_InvalidatesRefreshToken(t *testing.T) {
	server := newTestServer(t)

	_, reg := postJSON(t, server.URL+"/auth/register", map[string]string{"login": "frank", "password": "password123"})

	logoutResp, _ := postJSON(t, server.URL+"/auth/logout", map[string]string{"refresh_token": reg["refresh_token"]})
	if logoutResp.StatusCode != http.StatusOK {
		t.Fatalf("logout status = %d, want 200", logoutResp.StatusCode)
	}

	refreshResp, refreshBody := postJSON(t, server.URL+"/auth/refresh", map[string]string{"refresh_token": reg["refresh_token"]})
	if refreshResp.StatusCode != http.StatusUnauthorized {
		t.Fatalf("refresh after logout: status = %d, want 401; body = %v", refreshResp.StatusCode, refreshBody)
	}
}
