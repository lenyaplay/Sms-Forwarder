package handlers

import (
	"fmt"
	"net/http"
	"testing"
	"time"
)

// createViewerBindingForTest issues a download_token for device deviceID
// (owner-authenticated) and binds viewerToken's user to it. Returns nothing;
// fails the test on any error.
func createViewerBindingForTest(t *testing.T, serverURL, ownerToken string, deviceID int64, viewerToken string) {
	t.Helper()
	resp, body := doJSON(t, http.MethodPost, fmt.Sprintf("%s/devices/%d/download_tokens", serverURL, deviceID), ownerToken, map[string]interface{}{})
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("create download token: status = %d, body = %v", resp.StatusCode, body)
	}
	downloadToken := body["download_token"].(string)

	resp2, body2 := doJSON(t, http.MethodPost, serverURL+"/devices/bindings", viewerToken, map[string]interface{}{"download_token": downloadToken})
	if resp2.StatusCode != http.StatusCreated {
		t.Fatalf("add viewer binding: status = %d, body = %v", resp2.StatusCode, body2)
	}
}

func ingestTestMessage(t *testing.T, serverURL, uploadToken string, body []byte) int64 {
	t.Helper()
	resp, parsed := postWebhook(t, serverURL, uploadToken, body, "")
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("postWebhook: status = %d, body = %v", resp.StatusCode, parsed)
	}
	return int64(parsed["id"].(float64))
}

func TestListMessages_OwnerSeesMessages(t *testing.T) {
	server := newTestServer(t)
	ownerToken := registerAndGetAccessToken(t, server.URL, "lm-owner1")
	resp, body := doJSON(t, http.MethodPost, server.URL+"/devices", ownerToken, map[string]interface{}{"name": "Phone"})
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("create device: status = %d, body = %v", resp.StatusCode, body)
	}
	deviceID := int64(body["id"].(float64))
	uploadToken := body["upload_token"].(string)
	ingestTestMessage(t, server.URL, uploadToken, []byte(`{"from":"+1234","text":"hello"}`))

	resp2, body2 := doJSON(t, http.MethodGet, fmt.Sprintf("%s/devices/%d/messages", server.URL, deviceID), ownerToken, nil)
	if resp2.StatusCode != http.StatusOK {
		t.Fatalf("list messages: status = %d, body = %v", resp2.StatusCode, body2)
	}
	messages, ok := body2["messages"].([]interface{})
	if !ok || len(messages) != 1 {
		t.Fatalf("messages = %v, want 1 item", body2["messages"])
	}
	first := messages[0].(map[string]interface{})
	if first["sender"] != "+1234" || first["text"] != "hello" {
		t.Errorf("message = %v", first)
	}
	if _, present := first["body_hash"]; present {
		t.Errorf("response leaked body_hash: %v", first)
	}
}

func TestListMessages_ViewerWithBindingSeesMessages(t *testing.T) {
	server := newTestServer(t)
	ownerToken := registerAndGetAccessToken(t, server.URL, "lm-owner2")
	viewerToken := registerAndGetAccessToken(t, server.URL, "lm-viewer2")

	resp, body := doJSON(t, http.MethodPost, server.URL+"/devices", ownerToken, map[string]interface{}{"name": "Phone"})
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("create device: status = %d, body = %v", resp.StatusCode, body)
	}
	deviceID := int64(body["id"].(float64))
	uploadToken := body["upload_token"].(string)
	createViewerBindingForTest(t, server.URL, ownerToken, deviceID, viewerToken)
	ingestTestMessage(t, server.URL, uploadToken, []byte(`{"from":"+1234","text":"hello"}`))

	resp2, body2 := doJSON(t, http.MethodGet, fmt.Sprintf("%s/devices/%d/messages", server.URL, deviceID), viewerToken, nil)
	if resp2.StatusCode != http.StatusOK {
		t.Fatalf("list messages: status = %d, body = %v", resp2.StatusCode, body2)
	}
	messages, ok := body2["messages"].([]interface{})
	if !ok || len(messages) != 1 {
		t.Fatalf("messages = %v, want 1 item", body2["messages"])
	}
}

func TestListMessages_UnrelatedUserGets404(t *testing.T) {
	server := newTestServer(t)
	ownerToken := registerAndGetAccessToken(t, server.URL, "lm-owner3")
	strangerToken := registerAndGetAccessToken(t, server.URL, "lm-stranger3")

	resp, body := doJSON(t, http.MethodPost, server.URL+"/devices", ownerToken, map[string]interface{}{"name": "Phone"})
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("create device: status = %d, body = %v", resp.StatusCode, body)
	}
	deviceID := int64(body["id"].(float64))

	resp2, body2 := doJSON(t, http.MethodGet, fmt.Sprintf("%s/devices/%d/messages", server.URL, deviceID), strangerToken, nil)
	if resp2.StatusCode != http.StatusNotFound {
		t.Errorf("status = %d, body = %v, want 404", resp2.StatusCode, body2)
	}
}

func TestListMessages_Pagination(t *testing.T) {
	server := newTestServer(t)
	ownerToken := registerAndGetAccessToken(t, server.URL, "lm-owner4")
	resp, body := doJSON(t, http.MethodPost, server.URL+"/devices", ownerToken, map[string]interface{}{"name": "Phone"})
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("create device: status = %d, body = %v", resp.StatusCode, body)
	}
	deviceID := int64(body["id"].(float64))
	uploadToken := body["upload_token"].(string)

	var ids []int64
	for i := 0; i < 5; i++ {
		id := ingestTestMessage(t, server.URL, uploadToken, []byte(fmt.Sprintf(`{"from":"+1234","text":"msg-%d","receivedStamp":"%d"}`, i, i)))
		ids = append(ids, id)
	}

	seen := map[int64]bool{}
	url := fmt.Sprintf("%s/devices/%d/messages?limit=2", server.URL, deviceID)
	for i := 0; i < 10; i++ {
		resp, body := doJSON(t, http.MethodGet, url, ownerToken, nil)
		if resp.StatusCode != http.StatusOK {
			t.Fatalf("list messages: status = %d, body = %v", resp.StatusCode, body)
		}
		messages := body["messages"].([]interface{})
		for _, m := range messages {
			id := int64(m.(map[string]interface{})["id"].(float64))
			if seen[id] {
				t.Fatalf("duplicate message id %d across pages", id)
			}
			seen[id] = true
		}
		next, hasNext := body["next_before_id"]
		if len(messages) < 2 {
			if hasNext && next != nil {
				t.Fatalf("next_before_id = %v on a short page (%d items < limit 2), want nil", next, len(messages))
			}
			break
		}
		if !hasNext || next == nil {
			t.Fatalf("next_before_id missing on a full page (limit reached), body = %v", body)
		}
		url = fmt.Sprintf("%s/devices/%d/messages?limit=2&before_id=%d", server.URL, deviceID, int64(next.(float64)))
	}

	if len(seen) != len(ids) {
		t.Errorf("saw %d distinct messages across pages, want %d", len(seen), len(ids))
	}
}

func TestListMessages_PaginationStableUnderConcurrentInsert(t *testing.T) {
	server := newTestServer(t)
	ownerToken := registerAndGetAccessToken(t, server.URL, "lm-owner-concurrent")
	resp, body := doJSON(t, http.MethodPost, server.URL+"/devices", ownerToken, map[string]interface{}{"name": "Phone"})
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("create device: status = %d, body = %v", resp.StatusCode, body)
	}
	deviceID := int64(body["id"].(float64))
	uploadToken := body["upload_token"].(string)

	var ids []int64
	for i := 0; i < 3; i++ {
		ids = append(ids, ingestTestMessage(t, server.URL, uploadToken, []byte(fmt.Sprintf(`{"from":"+1234","text":"pre-%d","receivedStamp":"%d"}`, i, i))))
	}

	resp1, body1 := doJSON(t, http.MethodGet, fmt.Sprintf("%s/devices/%d/messages?limit=2", server.URL, deviceID), ownerToken, nil)
	if resp1.StatusCode != http.StatusOK {
		t.Fatalf("page1: status = %d, body = %v", resp1.StatusCode, body1)
	}
	page1 := body1["messages"].([]interface{})
	if len(page1) != 2 {
		t.Fatalf("page1 = %v, want 2 items", page1)
	}
	nextBeforeID := int64(body1["next_before_id"].(float64))

	// Simulate a new message arriving between page requests (e.g. another
	// webhook delivery). It must not appear on the next page, nor cause
	// existing not-yet-seen messages to be skipped or duplicated.
	newID := ingestTestMessage(t, server.URL, uploadToken, []byte(`{"from":"+1234","text":"inserted-between-pages"}`))

	resp2, body2 := doJSON(t, http.MethodGet, fmt.Sprintf("%s/devices/%d/messages?limit=2&before_id=%d", server.URL, deviceID, nextBeforeID), ownerToken, nil)
	if resp2.StatusCode != http.StatusOK {
		t.Fatalf("page2: status = %d, body = %v", resp2.StatusCode, body2)
	}
	page2 := body2["messages"].([]interface{})
	if len(page2) != 1 {
		t.Fatalf("page2 = %v, want 1 item (the remaining pre-existing message)", page2)
	}
	page2ID := int64(page2[0].(map[string]interface{})["id"].(float64))
	if page2ID == newID {
		t.Errorf("page2 leaked the message inserted between page requests (id %d)", newID)
	}
	if page2ID != ids[0] {
		t.Errorf("page2 = %+v, want the oldest pre-existing message (id %d)", page2, ids[0])
	}
}

func TestListMessages_SinceUntilFilter(t *testing.T) {
	server, db := newTestServerWithDB(t)
	ownerToken := registerAndGetAccessToken(t, server.URL, "lm-owner5")
	resp, body := doJSON(t, http.MethodPost, server.URL+"/devices", ownerToken, map[string]interface{}{"name": "Phone"})
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("create device: status = %d, body = %v", resp.StatusCode, body)
	}
	deviceID := int64(body["id"].(float64))
	uploadToken := body["upload_token"].(string)

	oldID := ingestTestMessage(t, server.URL, uploadToken, []byte(`{"from":"+1","text":"old"}`))
	newID := ingestTestMessage(t, server.URL, uploadToken, []byte(`{"from":"+1","text":"new"}`))

	cutoff := time.Now().UTC()
	if _, err := db.Exec("UPDATE messages SET created_at = ? WHERE id = ?", cutoff.Add(-48*time.Hour), oldID); err != nil {
		t.Fatalf("backdate old message: %v", err)
	}
	if _, err := db.Exec("UPDATE messages SET created_at = ? WHERE id = ?", cutoff, newID); err != nil {
		t.Fatalf("set new message time: %v", err)
	}

	since := cutoff.Add(-time.Hour).Format(time.RFC3339)
	resp2, body2 := doJSON(t, http.MethodGet, fmt.Sprintf("%s/devices/%d/messages?since=%s", server.URL, deviceID, since), ownerToken, nil)
	if resp2.StatusCode != http.StatusOK {
		t.Fatalf("list messages: status = %d, body = %v", resp2.StatusCode, body2)
	}
	messages := body2["messages"].([]interface{})
	if len(messages) != 1 || int64(messages[0].(map[string]interface{})["id"].(float64)) != newID {
		t.Errorf("since filter = %v, want only newID=%d", messages, newID)
	}
}

func TestListMessages_InvalidQueryParams(t *testing.T) {
	server := newTestServer(t)
	ownerToken := registerAndGetAccessToken(t, server.URL, "lm-owner6")
	resp, body := doJSON(t, http.MethodPost, server.URL+"/devices", ownerToken, map[string]interface{}{"name": "Phone"})
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("create device: status = %d, body = %v", resp.StatusCode, body)
	}
	deviceID := int64(body["id"].(float64))
	base := fmt.Sprintf("%s/devices/%d/messages", server.URL, deviceID)

	cases := []string{
		base + "?limit=0",
		base + "?limit=101",
		base + "?limit=abc",
		base + "?before_id=abc",
		base + "?since=not-a-date",
		base + "?until=not-a-date",
		base + "?since=2026-01-02T00:00:00Z&until=2026-01-01T00:00:00Z",
	}
	for _, url := range cases {
		resp, body := doJSON(t, http.MethodGet, url, ownerToken, nil)
		if resp.StatusCode != http.StatusBadRequest {
			t.Errorf("%s: status = %d, body = %v, want 400", url, resp.StatusCode, body)
		}
	}
}

func TestListMessages_RevokedTokenRevokesViewerAccess(t *testing.T) {
	server := newTestServer(t)
	ownerToken := registerAndGetAccessToken(t, server.URL, "lm-owner7")
	viewerToken := registerAndGetAccessToken(t, server.URL, "lm-viewer7")

	resp, body := doJSON(t, http.MethodPost, server.URL+"/devices", ownerToken, map[string]interface{}{"name": "Phone"})
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("create device: status = %d, body = %v", resp.StatusCode, body)
	}
	deviceID := int64(body["id"].(float64))

	resp2, body2 := doJSON(t, http.MethodPost, fmt.Sprintf("%s/devices/%d/download_tokens", server.URL, deviceID), ownerToken, map[string]interface{}{})
	if resp2.StatusCode != http.StatusCreated {
		t.Fatalf("create download token: status = %d, body = %v", resp2.StatusCode, body2)
	}
	downloadToken := body2["download_token"].(string)
	tokenID := int64(body2["id"].(float64))

	resp3, body3 := doJSON(t, http.MethodPost, server.URL+"/devices/bindings", viewerToken, map[string]interface{}{"download_token": downloadToken})
	if resp3.StatusCode != http.StatusCreated {
		t.Fatalf("add viewer binding: status = %d, body = %v", resp3.StatusCode, body3)
	}

	resp4, body4 := doJSON(t, http.MethodGet, fmt.Sprintf("%s/devices/%d/messages", server.URL, deviceID), viewerToken, nil)
	if resp4.StatusCode != http.StatusOK {
		t.Fatalf("before revoke: status = %d, body = %v, want 200", resp4.StatusCode, body4)
	}

	resp5, body5 := doJSON(t, http.MethodDelete, fmt.Sprintf("%s/devices/%d/download_tokens/%d", server.URL, deviceID, tokenID), ownerToken, nil)
	if resp5.StatusCode != http.StatusOK {
		t.Fatalf("revoke token: status = %d, body = %v", resp5.StatusCode, body5)
	}

	resp6, body6 := doJSON(t, http.MethodGet, fmt.Sprintf("%s/devices/%d/messages", server.URL, deviceID), viewerToken, nil)
	if resp6.StatusCode != http.StatusNotFound {
		t.Errorf("after revoke: status = %d, body = %v, want 404", resp6.StatusCode, body6)
	}
}

func TestListMessages_ExpiredTokenRevokesViewerAccess(t *testing.T) {
	server, db := newTestServerWithDB(t)
	ownerToken := registerAndGetAccessToken(t, server.URL, "lm-owner8")
	viewerToken := registerAndGetAccessToken(t, server.URL, "lm-viewer8")

	resp, body := doJSON(t, http.MethodPost, server.URL+"/devices", ownerToken, map[string]interface{}{"name": "Phone"})
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("create device: status = %d, body = %v", resp.StatusCode, body)
	}
	deviceID := int64(body["id"].(float64))

	resp2, body2 := doJSON(t, http.MethodPost, fmt.Sprintf("%s/devices/%d/download_tokens", server.URL, deviceID), ownerToken, map[string]interface{}{})
	if resp2.StatusCode != http.StatusCreated {
		t.Fatalf("create download token: status = %d, body = %v", resp2.StatusCode, body2)
	}
	downloadToken := body2["download_token"].(string)
	tokenID := int64(body2["id"].(float64))

	resp3, body3 := doJSON(t, http.MethodPost, server.URL+"/devices/bindings", viewerToken, map[string]interface{}{"download_token": downloadToken})
	if resp3.StatusCode != http.StatusCreated {
		t.Fatalf("add viewer binding: status = %d, body = %v", resp3.StatusCode, body3)
	}

	resp4, body4 := doJSON(t, http.MethodGet, fmt.Sprintf("%s/devices/%d/messages", server.URL, deviceID), viewerToken, nil)
	if resp4.StatusCode != http.StatusOK {
		t.Fatalf("before expiry: status = %d, body = %v, want 200", resp4.StatusCode, body4)
	}

	if _, err := db.Exec("UPDATE device_download_tokens SET expires_at = ? WHERE id = ?", time.Now().UTC().Add(-time.Hour), tokenID); err != nil {
		t.Fatalf("backdate token: %v", err)
	}

	resp5, body5 := doJSON(t, http.MethodGet, fmt.Sprintf("%s/devices/%d/messages", server.URL, deviceID), viewerToken, nil)
	if resp5.StatusCode != http.StatusNotFound {
		t.Errorf("after TTL expiry (no explicit revoke): status = %d, body = %v, want 404", resp5.StatusCode, body5)
	}
}
