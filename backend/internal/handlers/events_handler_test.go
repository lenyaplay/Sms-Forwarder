package handlers

import (
	"bufio"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"sms_forwarder/backend/internal/realtime"
	"sms_forwarder/backend/internal/services"
)

// sseClient opens a GET request expected to return text/event-stream and
// exposes a channel of raw "event:"/"data:"/": ping" lines read as they
// arrive, for tests to assert on with a timeout.
type sseClient struct {
	resp *http.Response
	scan *bufio.Scanner
}

func openSSE(t *testing.T, url string) *sseClient {
	t.Helper()
	resp, err := http.Get(url)
	if err != nil {
		t.Fatalf("GET %s: %v", url, err)
	}
	return &sseClient{resp: resp, scan: bufio.NewScanner(resp.Body)}
}

func (c *sseClient) close() { c.resp.Body.Close() }

// nextDataLine reads lines until it finds one starting with "data: ",
// skipping blank lines, "event: message" lines and ": ping" heartbeats, or
// times out.
func (c *sseClient) nextDataLine(t *testing.T, timeout time.Duration) (string, bool) {
	t.Helper()
	type result struct {
		line string
		ok   bool
	}
	out := make(chan result, 1)
	go func() {
		for c.scan.Scan() {
			line := c.scan.Text()
			if strings.HasPrefix(line, "data: ") {
				out <- result{strings.TrimPrefix(line, "data: "), true}
				return
			}
		}
		out <- result{"", false}
	}()
	select {
	case r := <-out:
		return r.line, r.ok
	case <-time.After(timeout):
		return "", false
	}
}

func TestEvents_MissingAccessToken(t *testing.T) {
	server := newTestServer(t)
	resp, err := http.Get(server.URL + "/events?device_ids=1")
	if err != nil {
		t.Fatalf("GET: %v", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusUnauthorized {
		t.Errorf("status = %d, want 401", resp.StatusCode)
	}
}

func TestEvents_InvalidAccessToken(t *testing.T) {
	server := newTestServer(t)
	resp, err := http.Get(server.URL + "/events?device_ids=1&access_token=garbage")
	if err != nil {
		t.Fatalf("GET: %v", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusUnauthorized {
		t.Errorf("status = %d, want 401", resp.StatusCode)
	}
}

func TestEvents_InvalidDeviceIDs(t *testing.T) {
	server := newTestServer(t)
	token := registerAndGetAccessToken(t, server.URL, "ev-owner-invalid")

	cases := []string{
		"/events?access_token=" + token,                          // missing device_ids
		"/events?device_ids=&access_token=" + token,               // empty
		"/events?device_ids=abc&access_token=" + token,            // non-numeric
		"/events?device_ids=" + strings.Repeat("1,", 21) + "1&access_token=" + token, // >20 elements
	}
	for _, path := range cases {
		resp, err := http.Get(server.URL + path)
		if err != nil {
			t.Fatalf("GET %s: %v", path, err)
		}
		resp.Body.Close()
		if resp.StatusCode != http.StatusBadRequest {
			t.Errorf("%s: status = %d, want 400", path, resp.StatusCode)
		}
	}
}

func TestEvents_InaccessibleDeviceGets404(t *testing.T) {
	server := newTestServer(t)
	ownerToken := registerAndGetAccessToken(t, server.URL, "ev-owner1")
	strangerToken := registerAndGetAccessToken(t, server.URL, "ev-stranger1")

	resp, body := doJSON(t, http.MethodPost, server.URL+"/devices", ownerToken, map[string]interface{}{"name": "Phone"})
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("create device: status = %d, body = %v", resp.StatusCode, body)
	}
	deviceID := int64(body["id"].(float64))

	resp2, err := http.Get(fmt.Sprintf("%s/events?device_ids=%d&access_token=%s", server.URL, deviceID, strangerToken))
	if err != nil {
		t.Fatalf("GET: %v", err)
	}
	defer resp2.Body.Close()
	if resp2.StatusCode != http.StatusNotFound {
		t.Errorf("status = %d, want 404", resp2.StatusCode)
	}
	if ct := resp2.Header.Get("Content-Type"); strings.Contains(ct, "text/event-stream") {
		t.Errorf("Content-Type = %q, must not be text/event-stream on a rejected connection", ct)
	}
}

func TestEvents_EndToEnd_ReceivesIngestedMessage(t *testing.T) {
	server := newTestServer(t)
	ownerToken := registerAndGetAccessToken(t, server.URL, "ev-owner2")
	resp, body := doJSON(t, http.MethodPost, server.URL+"/devices", ownerToken, map[string]interface{}{"name": "Phone"})
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("create device: status = %d, body = %v", resp.StatusCode, body)
	}
	deviceID := int64(body["id"].(float64))
	uploadToken := body["upload_token"].(string)

	client := openSSE(t, fmt.Sprintf("%s/events?device_ids=%d&access_token=%s", server.URL, deviceID, ownerToken))
	defer client.close()
	if client.resp.StatusCode != http.StatusOK {
		t.Fatalf("status = %d, want 200", client.resp.StatusCode)
	}
	if ct := client.resp.Header.Get("Content-Type"); !strings.HasPrefix(ct, "text/event-stream") {
		t.Fatalf("Content-Type = %q, want text/event-stream", ct)
	}

	ingestTestMessage(t, server.URL, uploadToken, []byte(`{"from":"+1234","text":"hello sse"}`))

	line, ok := client.nextDataLine(t, 3*time.Second)
	if !ok {
		t.Fatal("did not receive a data line within timeout")
	}
	var msg map[string]interface{}
	if err := json.Unmarshal([]byte(line), &msg); err != nil {
		t.Fatalf("unmarshal event data: %v (line=%q)", err, line)
	}
	if msg["sender"] != "+1234" || msg["text"] != "hello sse" {
		t.Errorf("event data = %v", msg)
	}
	if int64(msg["device_id"].(float64)) != deviceID {
		t.Errorf("event device_id = %v, want %d", msg["device_id"], deviceID)
	}
	if _, present := msg["body_hash"]; present {
		t.Errorf("event leaked body_hash: %v", msg)
	}
}

func TestEvents_MultiDeviceSubscription(t *testing.T) {
	server := newTestServer(t)
	ownerToken := registerAndGetAccessToken(t, server.URL, "ev-owner3")

	resp1, body1 := doJSON(t, http.MethodPost, server.URL+"/devices", ownerToken, map[string]interface{}{"name": "Phone A"})
	if resp1.StatusCode != http.StatusCreated {
		t.Fatalf("create deviceA: status = %d, body = %v", resp1.StatusCode, body1)
	}
	deviceA := int64(body1["id"].(float64))
	uploadA := body1["upload_token"].(string)

	resp2, body2 := doJSON(t, http.MethodPost, server.URL+"/devices", ownerToken, map[string]interface{}{"name": "Phone B"})
	if resp2.StatusCode != http.StatusCreated {
		t.Fatalf("create deviceB: status = %d, body = %v", resp2.StatusCode, body2)
	}
	deviceB := int64(body2["id"].(float64))
	uploadB := body2["upload_token"].(string)

	client := openSSE(t, fmt.Sprintf("%s/events?device_ids=%d,%d&access_token=%s", server.URL, deviceA, deviceB, ownerToken))
	defer client.close()
	if client.resp.StatusCode != http.StatusOK {
		t.Fatalf("status = %d, want 200", client.resp.StatusCode)
	}

	ingestTestMessage(t, server.URL, uploadA, []byte(`{"from":"+1","text":"from A"}`))
	ingestTestMessage(t, server.URL, uploadB, []byte(`{"from":"+2","text":"from B"}`))

	seen := map[int64]bool{}
	for i := 0; i < 2; i++ {
		line, ok := client.nextDataLine(t, 3*time.Second)
		if !ok {
			t.Fatalf("did not receive event %d within timeout", i)
		}
		var msg map[string]interface{}
		if err := json.Unmarshal([]byte(line), &msg); err != nil {
			t.Fatalf("unmarshal: %v", err)
		}
		seen[int64(msg["device_id"].(float64))] = true
	}
	if !seen[deviceA] || !seen[deviceB] {
		t.Errorf("seen = %v, want events from both devices", seen)
	}
}

func TestEvents_DuplicateWebhookDoesNotProduceSecondEvent(t *testing.T) {
	server := newTestServer(t)
	ownerToken := registerAndGetAccessToken(t, server.URL, "ev-owner4")
	resp, body := doJSON(t, http.MethodPost, server.URL+"/devices", ownerToken, map[string]interface{}{"name": "Phone"})
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("create device: status = %d, body = %v", resp.StatusCode, body)
	}
	deviceID := int64(body["id"].(float64))
	uploadToken := body["upload_token"].(string)

	client := openSSE(t, fmt.Sprintf("%s/events?device_ids=%d&access_token=%s", server.URL, deviceID, ownerToken))
	defer client.close()

	webhookBody := []byte(`{"from":"+1234","text":"dup-check"}`)
	ingestTestMessage(t, server.URL, uploadToken, webhookBody)
	postWebhook(t, server.URL, uploadToken, webhookBody, "") // retry, deduplicated

	if _, ok := client.nextDataLine(t, 2*time.Second); !ok {
		t.Fatal("expected one event for the first ingest")
	}
	if line, ok := client.nextDataLine(t, 500*time.Millisecond); ok {
		t.Errorf("received a second event for a deduplicated retry: %q", line)
	}
}

func TestEvents_RevokedAccessDoesNotCloseAlreadyOpenConnection(t *testing.T) {
	server := newTestServer(t)
	ownerToken := registerAndGetAccessToken(t, server.URL, "ev-owner-revoke")
	viewerToken := registerAndGetAccessToken(t, server.URL, "ev-viewer-revoke")

	resp, body := doJSON(t, http.MethodPost, server.URL+"/devices", ownerToken, map[string]interface{}{"name": "Phone"})
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("create device: status = %d, body = %v", resp.StatusCode, body)
	}
	deviceID := int64(body["id"].(float64))
	uploadToken := body["upload_token"].(string)

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

	client := openSSE(t, fmt.Sprintf("%s/events?device_ids=%d&access_token=%s", server.URL, deviceID, viewerToken))
	defer client.close()
	if client.resp.StatusCode != http.StatusOK {
		t.Fatalf("status = %d, want 200", client.resp.StatusCode)
	}

	// Revoke the download token the viewer connected through — per spec
	// assumption 6, this must NOT close the already-open SSE connection.
	resp4, body4 := doJSON(t, http.MethodDelete, fmt.Sprintf("%s/devices/%d/download_tokens/%d", server.URL, deviceID, tokenID), ownerToken, nil)
	if resp4.StatusCode != http.StatusOK {
		t.Fatalf("revoke token: status = %d, body = %v", resp4.StatusCode, body4)
	}

	// A subsequent REST read correctly reflects the revocation (404) —
	// confirming REST stays authoritative while push is best-effort.
	resp5, body5 := doJSON(t, http.MethodGet, fmt.Sprintf("%s/devices/%d/messages", server.URL, deviceID), viewerToken, nil)
	if resp5.StatusCode != http.StatusNotFound {
		t.Fatalf("REST after revoke: status = %d, body = %v, want 404", resp5.StatusCode, body5)
	}

	// But the already-open SSE connection is still alive and still
	// delivers events published after the revocation.
	ingestTestMessage(t, server.URL, uploadToken, []byte(`{"from":"+1234","text":"after revoke"}`))
	line, ok := client.nextDataLine(t, 3*time.Second)
	if !ok {
		t.Fatal("did not receive an event on the already-open connection after revocation; connection appears to have been closed")
	}
	var msg map[string]interface{}
	if err := json.Unmarshal([]byte(line), &msg); err != nil {
		t.Fatalf("unmarshal event data: %v (line=%q)", err, line)
	}
	if msg["text"] != "after revoke" {
		t.Errorf("event data = %v, want text=%q", msg, "after revoke")
	}
}

func TestEvents_Heartbeat(t *testing.T) {
	// Uses the full router (default 30s heartbeat) to create a device and
	// mint an owner token, then stands up a second, standalone httptest
	// server wrapping only eventsHandler — sharing the same DeviceService
	// construction against the same in-memory DB — with a short heartbeat
	// interval, since 30s isn't practical to wait for in a test.
	server, db := newTestServerWithDB(t)
	ownerToken := registerAndGetAccessToken(t, server.URL, "ev-owner-hb")
	resp, body := doJSON(t, http.MethodPost, server.URL+"/devices", ownerToken, map[string]interface{}{"name": "Phone"})
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("create device: status = %d, body = %v", resp.StatusCode, body)
	}
	deviceID := int64(body["id"].(float64))

	deviceSvc := services.NewDeviceService(db)
	hub := realtime.NewHub()
	heartbeatServer := httptest.NewServer(eventsHandler(deviceSvc, hub, "test-secret", 50*time.Millisecond))
	t.Cleanup(heartbeatServer.Close)

	client := openSSE(t, fmt.Sprintf("%s?device_ids=%d&access_token=%s", heartbeatServer.URL, deviceID, ownerToken))
	defer client.close()
	if client.resp.StatusCode != http.StatusOK {
		t.Fatalf("status = %d, want 200", client.resp.StatusCode)
	}

	found := make(chan bool, 1)
	go func() {
		for client.scan.Scan() {
			if strings.Contains(client.scan.Text(), "ping") {
				found <- true
				return
			}
		}
		found <- false
	}()

	select {
	case ok := <-found:
		if !ok {
			t.Error("stream ended before a heartbeat was seen")
		}
	case <-time.After(2 * time.Second):
		t.Fatal("did not receive heartbeat within timeout")
	}
}
