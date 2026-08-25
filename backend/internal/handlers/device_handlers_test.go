package handlers

import (
	"bytes"
	"encoding/json"
	"net/http"
	"strconv"
	"testing"
)

// doJSON issues an HTTP request with an optional JSON body and bearer token,
// decoding the response body into a generic map (handles nested JSON, unlike
// auth_handlers_test.go's postJSON which is typed to map[string]string).
func doJSON(t *testing.T, method, url, accessToken string, body interface{}) (*http.Response, map[string]interface{}) {
	t.Helper()

	var reader *bytes.Reader
	if body != nil {
		buf, err := json.Marshal(body)
		if err != nil {
			t.Fatalf("marshal request body: %v", err)
		}
		reader = bytes.NewReader(buf)
	} else {
		reader = bytes.NewReader(nil)
	}

	req, err := http.NewRequest(method, url, reader)
	if err != nil {
		t.Fatalf("NewRequest %s %s: %v", method, url, err)
	}
	req.Header.Set("Content-Type", "application/json")
	if accessToken != "" {
		req.Header.Set("Authorization", "Bearer "+accessToken)
	}

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("%s %s: %v", method, url, err)
	}
	defer resp.Body.Close()

	var parsed map[string]interface{}
	if resp.ContentLength != 0 {
		_ = json.NewDecoder(resp.Body).Decode(&parsed)
	}
	return resp, parsed
}

func registerAndGetAccessToken(t *testing.T, server string, login string) string {
	t.Helper()
	resp, body := postJSON(t, server+"/auth/register", map[string]string{"login": login, "password": "password123"})
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("register(%s): status = %d, body = %v", login, resp.StatusCode, body)
	}
	return body["access_token"]
}

func TestDevices_CreateAndList(t *testing.T) {
	server := newTestServer(t)
	ownerToken := registerAndGetAccessToken(t, server.URL, "dh-owner1")

	resp, body := doJSON(t, http.MethodPost, server.URL+"/devices", ownerToken,
		map[string]interface{}{"name": "My Phone"})
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("create device: status = %d, body = %v", resp.StatusCode, body)
	}
	if body["upload_token"] == "" || body["upload_token"] == nil {
		t.Errorf("expected non-empty upload_token, got %v", body)
	}
	if body["upload_token_expires_at"] != nil {
		t.Errorf("expected nil upload_token_expires_at, got %v", body["upload_token_expires_at"])
	}

	listResp, listBody := doJSON(t, http.MethodGet, server.URL+"/devices", ownerToken, nil)
	if listResp.StatusCode != http.StatusOK {
		t.Fatalf("list devices: status = %d, body = %v", listResp.StatusCode, listBody)
	}
	devices, ok := listBody["devices"].([]interface{})
	if !ok || len(devices) != 1 {
		t.Fatalf("list devices = %v, want 1 device", listBody)
	}
}

func TestDevices_Create_InvalidName(t *testing.T) {
	server := newTestServer(t)
	token := registerAndGetAccessToken(t, server.URL, "dh-owner2")

	resp, body := doJSON(t, http.MethodPost, server.URL+"/devices", token, map[string]interface{}{"name": ""})
	if resp.StatusCode != http.StatusBadRequest {
		t.Fatalf("status = %d, want 400; body = %v", resp.StatusCode, body)
	}
}

func TestDevices_Create_InvalidTTL(t *testing.T) {
	server := newTestServer(t)
	token := registerAndGetAccessToken(t, server.URL, "dh-ttl1")

	resp, body := doJSON(t, http.MethodPost, server.URL+"/devices", token,
		map[string]interface{}{"name": "Device", "upload_token_ttl_seconds": -5})
	if resp.StatusCode != http.StatusBadRequest {
		t.Fatalf("status = %d, want 400; body = %v", resp.StatusCode, body)
	}
}

func TestDevices_ReissueUploadToken_InvalidTTL(t *testing.T) {
	server := newTestServer(t)
	token := registerAndGetAccessToken(t, server.URL, "dh-ttl2")

	_, createBody := doJSON(t, http.MethodPost, server.URL+"/devices", token, map[string]interface{}{"name": "Device"})
	deviceID := int64(createBody["id"].(float64))

	resp, body := doJSON(t, http.MethodPost, deviceURL(server.URL, deviceID)+"/upload_token", token,
		map[string]interface{}{"ttl_seconds": 0})
	if resp.StatusCode != http.StatusBadRequest {
		t.Fatalf("status = %d, want 400; body = %v", resp.StatusCode, body)
	}

	// Malformed JSON body must also be rejected, not silently ignored.
	req, _ := http.NewRequest(http.MethodPost, deviceURL(server.URL, deviceID)+"/upload_token", bytes.NewReader([]byte("not-json")))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	malformedResp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("malformed body request: %v", err)
	}
	defer malformedResp.Body.Close()
	if malformedResp.StatusCode != http.StatusBadRequest {
		t.Fatalf("malformed body: status = %d, want 400", malformedResp.StatusCode)
	}
}

func TestDevices_CreateDownloadToken_InvalidTTL(t *testing.T) {
	server := newTestServer(t)
	token := registerAndGetAccessToken(t, server.URL, "dh-ttl3")

	_, createBody := doJSON(t, http.MethodPost, server.URL+"/devices", token, map[string]interface{}{"name": "Device"})
	deviceID := int64(createBody["id"].(float64))

	resp, body := doJSON(t, http.MethodPost, deviceURL(server.URL, deviceID)+"/download_tokens", token,
		map[string]interface{}{"ttl_seconds": -100})
	if resp.StatusCode != http.StatusBadRequest {
		t.Fatalf("status = %d, want 400; body = %v", resp.StatusCode, body)
	}
}

func TestDevices_Rename_EmptyName(t *testing.T) {
	server := newTestServer(t)
	token := registerAndGetAccessToken(t, server.URL, "dh-ttl4")

	_, createBody := doJSON(t, http.MethodPost, server.URL+"/devices", token, map[string]interface{}{"name": "Device"})
	deviceID := int64(createBody["id"].(float64))

	resp, body := doJSON(t, http.MethodPatch, deviceURL(server.URL, deviceID), token, map[string]interface{}{"name": ""})
	if resp.StatusCode != http.StatusBadRequest {
		t.Fatalf("status = %d, want 400; body = %v", resp.StatusCode, body)
	}
}

func TestDevices_GetDevice_NotFoundForUnrelatedUser(t *testing.T) {
	server := newTestServer(t)
	ownerToken := registerAndGetAccessToken(t, server.URL, "dh-owner3")
	otherToken := registerAndGetAccessToken(t, server.URL, "dh-other3")

	createResp, createBody := doJSON(t, http.MethodPost, server.URL+"/devices", ownerToken,
		map[string]interface{}{"name": "Device"})
	if createResp.StatusCode != http.StatusCreated {
		t.Fatalf("create device: status = %d", createResp.StatusCode)
	}
	deviceID := int64(createBody["id"].(float64))

	resp, body := doJSON(t, http.MethodGet, deviceURL(server.URL, deviceID), otherToken, nil)
	if resp.StatusCode != http.StatusNotFound {
		t.Fatalf("status = %d, want 404; body = %v", resp.StatusCode, body)
	}

	ownResp, ownBody := doJSON(t, http.MethodGet, deviceURL(server.URL, deviceID), ownerToken, nil)
	if ownResp.StatusCode != http.StatusOK {
		t.Fatalf("owner get device: status = %d, body = %v", ownResp.StatusCode, ownBody)
	}
	if ownBody["upload_token"] == nil || ownBody["upload_token"] == "" {
		t.Errorf("expected owner to see upload_token, got %v", ownBody)
	}
}

func TestDevices_RenameAndDelete_ForbiddenForNonOwner(t *testing.T) {
	server := newTestServer(t)
	ownerToken := registerAndGetAccessToken(t, server.URL, "dh-owner4")
	otherToken := registerAndGetAccessToken(t, server.URL, "dh-other4")

	_, createBody := doJSON(t, http.MethodPost, server.URL+"/devices", ownerToken,
		map[string]interface{}{"name": "Device"})
	deviceID := int64(createBody["id"].(float64))

	renameResp, renameBody := doJSON(t, http.MethodPatch, deviceURL(server.URL, deviceID), otherToken,
		map[string]interface{}{"name": "Hijacked"})
	if renameResp.StatusCode != http.StatusForbidden {
		t.Fatalf("rename by non-owner: status = %d, want 403; body = %v", renameResp.StatusCode, renameBody)
	}

	deleteResp, deleteBody := doJSON(t, http.MethodDelete, deviceURL(server.URL, deviceID), otherToken, nil)
	if deleteResp.StatusCode != http.StatusForbidden {
		t.Fatalf("delete by non-owner: status = %d, want 403; body = %v", deleteResp.StatusCode, deleteBody)
	}

	okResp, okBody := doJSON(t, http.MethodPatch, deviceURL(server.URL, deviceID), ownerToken,
		map[string]interface{}{"name": "Renamed"})
	if okResp.StatusCode != http.StatusOK {
		t.Fatalf("rename by owner: status = %d, body = %v", okResp.StatusCode, okBody)
	}

	finalDeleteResp, _ := doJSON(t, http.MethodDelete, deviceURL(server.URL, deviceID), ownerToken, nil)
	if finalDeleteResp.StatusCode != http.StatusNoContent {
		t.Fatalf("delete by owner: status = %d, want 204", finalDeleteResp.StatusCode)
	}

	repeatDeleteResp, _ := doJSON(t, http.MethodDelete, deviceURL(server.URL, deviceID), ownerToken, nil)
	if repeatDeleteResp.StatusCode != http.StatusNotFound {
		t.Fatalf("repeat delete: status = %d, want 404", repeatDeleteResp.StatusCode)
	}
}

func TestDevices_DownloadTokensAndBindings_FullFlow(t *testing.T) {
	server := newTestServer(t)
	ownerToken := registerAndGetAccessToken(t, server.URL, "dh-owner5")
	viewerToken := registerAndGetAccessToken(t, server.URL, "dh-viewer5")

	_, createBody := doJSON(t, http.MethodPost, server.URL+"/devices", ownerToken,
		map[string]interface{}{"name": "Device"})
	deviceID := int64(createBody["id"].(float64))

	dtResp, dtBody := doJSON(t, http.MethodPost, deviceURL(server.URL, deviceID)+"/download_tokens", ownerToken,
		map[string]interface{}{"label": "family"})
	if dtResp.StatusCode != http.StatusCreated {
		t.Fatalf("create download token: status = %d, body = %v", dtResp.StatusCode, dtBody)
	}
	downloadToken := dtBody["download_token"].(string)

	bindResp, bindBody := doJSON(t, http.MethodPost, server.URL+"/devices/bindings", viewerToken,
		map[string]interface{}{"download_token": downloadToken})
	if bindResp.StatusCode != http.StatusCreated {
		t.Fatalf("add binding: status = %d, body = %v", bindResp.StatusCode, bindBody)
	}
	if int64(bindBody["device_id"].(float64)) != deviceID {
		t.Errorf("device_id = %v, want %d", bindBody["device_id"], deviceID)
	}

	// Duplicate binding conflicts.
	dupResp, dupBody := doJSON(t, http.MethodPost, server.URL+"/devices/bindings", viewerToken,
		map[string]interface{}{"download_token": downloadToken})
	if dupResp.StatusCode != http.StatusConflict {
		t.Fatalf("duplicate binding: status = %d, want 409; body = %v", dupResp.StatusCode, dupBody)
	}

	// Owner binding to their own device conflicts.
	selfResp, selfBody := doJSON(t, http.MethodPost, server.URL+"/devices/bindings", ownerToken,
		map[string]interface{}{"download_token": downloadToken})
	if selfResp.StatusCode != http.StatusConflict {
		t.Fatalf("self binding: status = %d, want 409; body = %v", selfResp.StatusCode, selfBody)
	}

	// Viewer sees the device but not its upload_token.
	listResp, listBody := doJSON(t, http.MethodGet, server.URL+"/devices", viewerToken, nil)
	if listResp.StatusCode != http.StatusOK {
		t.Fatalf("list devices: status = %d", listResp.StatusCode)
	}
	devices := listBody["devices"].([]interface{})
	if len(devices) != 1 {
		t.Fatalf("expected 1 device for viewer, got %d", len(devices))
	}
	viewerDevice := devices[0].(map[string]interface{})
	if _, hasToken := viewerDevice["upload_token"]; hasToken {
		t.Errorf("viewer response should not include upload_token, got %v", viewerDevice)
	}

	// List download tokens shows the token with bindings_count 1.
	listTokensResp, listTokensBody := doJSON(t, http.MethodGet, deviceURL(server.URL, deviceID)+"/download_tokens", ownerToken, nil)
	if listTokensResp.StatusCode != http.StatusOK {
		t.Fatalf("list download tokens: status = %d", listTokensResp.StatusCode)
	}
	tokens := listTokensBody["tokens"].([]interface{})
	if len(tokens) != 1 {
		t.Fatalf("expected 1 active download token, got %d", len(tokens))
	}
	tokenEntry := tokens[0].(map[string]interface{})
	if int(tokenEntry["bindings_count"].(float64)) != 1 {
		t.Errorf("bindings_count = %v, want 1", tokenEntry["bindings_count"])
	}
	tokenID := int64(tokenEntry["id"].(float64))

	// Revoke the token: bindings_count is removed and the viewer loses access.
	revokeResp, revokeBody := doJSON(t, http.MethodDelete,
		deviceURL(server.URL, deviceID)+"/download_tokens/"+strconv.FormatInt(tokenID, 10), ownerToken, nil)
	if revokeResp.StatusCode != http.StatusOK {
		t.Fatalf("revoke download token: status = %d, body = %v", revokeResp.StatusCode, revokeBody)
	}
	if int(revokeBody["revoked_bindings_count"].(float64)) != 1 {
		t.Errorf("revoked_bindings_count = %v, want 1", revokeBody["revoked_bindings_count"])
	}

	reuseResp, reuseBody := doJSON(t, http.MethodPost, server.URL+"/devices/bindings", registerAndGetAccessToken(t, server.URL, "dh-late5"),
		map[string]interface{}{"download_token": downloadToken})
	if reuseResp.StatusCode != http.StatusUnauthorized {
		t.Fatalf("bind with revoked token: status = %d, want 401; body = %v", reuseResp.StatusCode, reuseBody)
	}
}

func TestDevices_DownloadTokenRoutes_ForbiddenForNonOwner(t *testing.T) {
	server := newTestServer(t)
	ownerToken := registerAndGetAccessToken(t, server.URL, "dh-dtroutes-owner")
	otherToken := registerAndGetAccessToken(t, server.URL, "dh-dtroutes-other")

	_, createBody := doJSON(t, http.MethodPost, server.URL+"/devices", ownerToken, map[string]interface{}{"name": "Device"})
	deviceID := int64(createBody["id"].(float64))

	createResp, createTokBody := doJSON(t, http.MethodPost, deviceURL(server.URL, deviceID)+"/download_tokens", otherToken, nil)
	if createResp.StatusCode != http.StatusForbidden {
		t.Fatalf("create download token by non-owner: status = %d, want 403; body = %v", createResp.StatusCode, createTokBody)
	}

	listResp, listBody := doJSON(t, http.MethodGet, deviceURL(server.URL, deviceID)+"/download_tokens", otherToken, nil)
	if listResp.StatusCode != http.StatusForbidden {
		t.Fatalf("list download tokens by non-owner: status = %d, want 403; body = %v", listResp.StatusCode, listBody)
	}

	_, ownedTok := doJSON(t, http.MethodPost, deviceURL(server.URL, deviceID)+"/download_tokens", ownerToken, nil)
	tokenID := int64(ownedTok["id"].(float64))

	revokeResp, revokeBody := doJSON(t, http.MethodDelete,
		deviceURL(server.URL, deviceID)+"/download_tokens/"+strconv.FormatInt(tokenID, 10), otherToken, nil)
	if revokeResp.StatusCode != http.StatusForbidden {
		t.Fatalf("revoke download token by non-owner: status = %d, want 403; body = %v", revokeResp.StatusCode, revokeBody)
	}
}

func TestDevices_RevokeDownloadToken_WrongDevice404(t *testing.T) {
	server := newTestServer(t)
	ownerToken := registerAndGetAccessToken(t, server.URL, "dh-dtroutes-owner2")

	_, deviceOneBody := doJSON(t, http.MethodPost, server.URL+"/devices", ownerToken, map[string]interface{}{"name": "Device One"})
	deviceOneID := int64(deviceOneBody["id"].(float64))
	_, deviceTwoBody := doJSON(t, http.MethodPost, server.URL+"/devices", ownerToken, map[string]interface{}{"name": "Device Two"})
	deviceTwoID := int64(deviceTwoBody["id"].(float64))

	_, tokenBody := doJSON(t, http.MethodPost, deviceURL(server.URL, deviceTwoID)+"/download_tokens", ownerToken, nil)
	tokenID := int64(tokenBody["id"].(float64))

	// tokenID belongs to deviceTwo; revoking it via deviceOne's route must 404, not succeed.
	resp, body := doJSON(t, http.MethodDelete,
		deviceURL(server.URL, deviceOneID)+"/download_tokens/"+strconv.FormatInt(tokenID, 10), ownerToken, nil)
	if resp.StatusCode != http.StatusNotFound {
		t.Fatalf("status = %d, want 404; body = %v", resp.StatusCode, body)
	}
}

func TestDevices_AddBinding_EmptyToken(t *testing.T) {
	server := newTestServer(t)
	token := registerAndGetAccessToken(t, server.URL, "dh-bind-empty")

	resp, body := doJSON(t, http.MethodPost, server.URL+"/devices/bindings", token, map[string]interface{}{"download_token": ""})
	if resp.StatusCode != http.StatusBadRequest {
		t.Fatalf("status = %d, want 400; body = %v", resp.StatusCode, body)
	}
}

func TestDevices_UploadTokenReissue(t *testing.T) {
	server := newTestServer(t)
	ownerToken := registerAndGetAccessToken(t, server.URL, "dh-owner6")

	_, createBody := doJSON(t, http.MethodPost, server.URL+"/devices", ownerToken,
		map[string]interface{}{"name": "Device"})
	deviceID := int64(createBody["id"].(float64))
	oldToken := createBody["upload_token"]

	reissueResp, reissueBody := doJSON(t, http.MethodPost, deviceURL(server.URL, deviceID)+"/upload_token", ownerToken, nil)
	if reissueResp.StatusCode != http.StatusOK {
		t.Fatalf("reissue upload token: status = %d, body = %v", reissueResp.StatusCode, reissueBody)
	}
	if reissueBody["upload_token"] == oldToken {
		t.Errorf("expected a new upload token")
	}
}

func deviceURL(base string, id int64) string {
	return base + "/devices/" + strconv.FormatInt(id, 10)
}
