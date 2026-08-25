package handlers

import (
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"strconv"
	"time"

	"sms_forwarder/backend/internal/services"
	"sms_forwarder/backend/internal/storage"
)

type createDeviceRequest struct {
	Name                 string `json:"name"`
	UploadTokenTTLSeconds *int  `json:"upload_token_ttl_seconds"`
}

type renameDeviceRequest struct {
	Name string `json:"name"`
}

type reissueUploadTokenRequest struct {
	TTLSeconds *int `json:"ttl_seconds"`
}

type createDownloadTokenRequest struct {
	Label      *string `json:"label"`
	TTLSeconds *int    `json:"ttl_seconds"`
}

type addBindingRequest struct {
	DownloadToken string `json:"download_token"`
}

type deviceResponse struct {
	ID                   int64   `json:"id"`
	Name                 string  `json:"name"`
	Role                 string  `json:"role,omitempty"`
	UploadToken          *string `json:"upload_token,omitempty"`
	UploadTokenExpiresAt *string `json:"upload_token_expires_at,omitempty"`
	CreatedAt            string  `json:"created_at"`
}

type downloadTokenResponse struct {
	ID                     int64   `json:"id"`
	DownloadToken          string  `json:"download_token"`
	Label                  *string `json:"label"`
	DownloadTokenExpiresAt *string `json:"download_token_expires_at"`
	BindingsCount          *int    `json:"bindings_count,omitempty"`
	CreatedAt              string  `json:"created_at"`
}

func formatNullTime(valid bool, t time.Time) *string {
	if !valid {
		return nil
	}
	s := t.UTC().Format(time.RFC3339)
	return &s
}

func deviceToResponse(d storage.Device, role string, includeUploadToken bool) deviceResponse {
	resp := deviceResponse{
		ID:        d.ID,
		Name:      d.Name,
		Role:      role,
		CreatedAt: d.CreatedAt.UTC().Format(time.RFC3339),
	}
	if includeUploadToken {
		token := d.UploadToken
		resp.UploadToken = &token
		resp.UploadTokenExpiresAt = formatNullTime(d.UploadTokenExpiresAt.Valid, d.UploadTokenExpiresAt.Time)
	}
	return resp
}

func downloadTokenToResponse(t storage.DeviceDownloadToken, includeBindingsCount bool) downloadTokenResponse {
	resp := downloadTokenResponse{
		ID:                     t.ID,
		DownloadToken:          t.Token,
		DownloadTokenExpiresAt: formatNullTime(t.ExpiresAt.Valid, t.ExpiresAt.Time),
		CreatedAt:              t.CreatedAt.UTC().Format(time.RFC3339),
	}
	if t.Label.Valid {
		resp.Label = &t.Label.String
	}
	if includeBindingsCount {
		resp.BindingsCount = &t.BindingsCount
	}
	return resp
}

// parseTTLSeconds validates an optional TTL and converts it to a *time.Duration.
// Returns an error if the value is present but not a positive integer.
func parseTTLSeconds(seconds *int) (*time.Duration, bool) {
	if seconds == nil {
		return nil, true
	}
	if *seconds <= 0 {
		return nil, false
	}
	d := time.Duration(*seconds) * time.Second
	return &d, true
}

func createDeviceHandler(svc *services.DeviceService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		userID, _ := UserIDFromContext(r.Context())

		var req createDeviceRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			writeError(w, http.StatusBadRequest, "invalid request body")
			return
		}
		if req.Name == "" {
			writeError(w, http.StatusBadRequest, "name is required")
			return
		}
		ttl, ok := parseTTLSeconds(req.UploadTokenTTLSeconds)
		if !ok {
			writeError(w, http.StatusBadRequest, "upload_token_ttl_seconds must be a positive integer")
			return
		}

		device, err := svc.CreateDevice(r.Context(), userID, req.Name, ttl)
		if err != nil {
			writeError(w, http.StatusInternalServerError, "internal error")
			return
		}
		writeJSON(w, http.StatusCreated, deviceToResponse(device, "", true))
	}
}

func listDevicesHandler(svc *services.DeviceService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		userID, _ := UserIDFromContext(r.Context())

		owned, viewer, err := svc.ListDevices(r.Context(), userID)
		if err != nil {
			writeError(w, http.StatusInternalServerError, "internal error")
			return
		}

		devices := make([]deviceResponse, 0, len(owned)+len(viewer))
		for _, d := range owned {
			devices = append(devices, deviceToResponse(d, "owner", true))
		}
		for _, d := range viewer {
			devices = append(devices, deviceToResponse(d, "viewer", false))
		}

		writeJSON(w, http.StatusOK, map[string]interface{}{"devices": devices})
	}
}

func getDeviceHandler(svc *services.DeviceService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		userID, _ := UserIDFromContext(r.Context())
		deviceID, ok := parsePathID(w, r, "id")
		if !ok {
			return
		}

		device, role, err := svc.GetDevice(r.Context(), userID, deviceID)
		switch {
		case errors.Is(err, services.ErrDeviceNotFound):
			writeError(w, http.StatusNotFound, "device not found")
		case err != nil:
			writeError(w, http.StatusInternalServerError, "internal error")
		default:
			writeJSON(w, http.StatusOK, deviceToResponse(device, role, role == "owner"))
		}
	}
}

func renameDeviceHandler(svc *services.DeviceService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		userID, _ := UserIDFromContext(r.Context())
		deviceID, ok := parsePathID(w, r, "id")
		if !ok {
			return
		}

		var req renameDeviceRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.Name == "" {
			writeError(w, http.StatusBadRequest, "name is required")
			return
		}

		device, err := svc.RenameDevice(r.Context(), userID, deviceID, req.Name)
		switch {
		case errors.Is(err, services.ErrNotOwner):
			writeError(w, http.StatusForbidden, "only the device owner can rename it")
		case errors.Is(err, services.ErrDeviceNotFound):
			writeError(w, http.StatusNotFound, "device not found")
		case err != nil:
			writeError(w, http.StatusInternalServerError, "internal error")
		default:
			writeJSON(w, http.StatusOK, deviceToResponse(device, "owner", true))
		}
	}
}

func deleteDeviceHandler(svc *services.DeviceService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		userID, _ := UserIDFromContext(r.Context())
		deviceID, ok := parsePathID(w, r, "id")
		if !ok {
			return
		}

		err := svc.DeleteDevice(r.Context(), userID, deviceID)
		switch {
		case errors.Is(err, services.ErrNotOwner):
			writeError(w, http.StatusForbidden, "only the device owner can delete it")
		case errors.Is(err, services.ErrDeviceNotFound):
			writeError(w, http.StatusNotFound, "device not found")
		case err != nil:
			writeError(w, http.StatusInternalServerError, "internal error")
		default:
			w.WriteHeader(http.StatusNoContent)
		}
	}
}

func reissueUploadTokenHandler(svc *services.DeviceService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		userID, _ := UserIDFromContext(r.Context())
		deviceID, ok := parsePathID(w, r, "id")
		if !ok {
			return
		}

		var req reissueUploadTokenRequest
		if !decodeOptionalJSONBody(w, r, &req) {
			return
		}
		ttl, ok := parseTTLSeconds(req.TTLSeconds)
		if !ok {
			writeError(w, http.StatusBadRequest, "ttl_seconds must be a positive integer")
			return
		}

		device, err := svc.ReissueUploadToken(r.Context(), userID, deviceID, ttl)
		switch {
		case errors.Is(err, services.ErrNotOwner):
			writeError(w, http.StatusForbidden, "only the device owner can reissue the upload token")
		case errors.Is(err, services.ErrDeviceNotFound):
			writeError(w, http.StatusNotFound, "device not found")
		case err != nil:
			writeError(w, http.StatusInternalServerError, "internal error")
		default:
			resp := deviceToResponse(device, "owner", true)
			writeJSON(w, http.StatusOK, map[string]interface{}{
				"upload_token":            *resp.UploadToken,
				"upload_token_expires_at": resp.UploadTokenExpiresAt,
			})
		}
	}
}

func createDownloadTokenHandler(svc *services.DeviceService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		userID, _ := UserIDFromContext(r.Context())
		deviceID, ok := parsePathID(w, r, "id")
		if !ok {
			return
		}

		var req createDownloadTokenRequest
		if !decodeOptionalJSONBody(w, r, &req) {
			return
		}
		ttl, ok := parseTTLSeconds(req.TTLSeconds)
		if !ok {
			writeError(w, http.StatusBadRequest, "ttl_seconds must be a positive integer")
			return
		}

		token, err := svc.CreateDownloadToken(r.Context(), userID, deviceID, req.Label, ttl)
		switch {
		case errors.Is(err, services.ErrNotOwner):
			writeError(w, http.StatusForbidden, "only the device owner can create download tokens")
		case errors.Is(err, services.ErrDeviceNotFound):
			writeError(w, http.StatusNotFound, "device not found")
		case err != nil:
			writeError(w, http.StatusInternalServerError, "internal error")
		default:
			writeJSON(w, http.StatusCreated, downloadTokenToResponse(token, false))
		}
	}
}

func listDownloadTokensHandler(svc *services.DeviceService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		userID, _ := UserIDFromContext(r.Context())
		deviceID, ok := parsePathID(w, r, "id")
		if !ok {
			return
		}

		tokens, err := svc.ListDownloadTokens(r.Context(), userID, deviceID)
		switch {
		case errors.Is(err, services.ErrNotOwner):
			writeError(w, http.StatusForbidden, "only the device owner can list download tokens")
		case errors.Is(err, services.ErrDeviceNotFound):
			writeError(w, http.StatusNotFound, "device not found")
		case err != nil:
			writeError(w, http.StatusInternalServerError, "internal error")
		default:
			resp := make([]downloadTokenResponse, 0, len(tokens))
			for _, t := range tokens {
				resp = append(resp, downloadTokenToResponse(t, true))
			}
			writeJSON(w, http.StatusOK, map[string]interface{}{"tokens": resp})
		}
	}
}

func revokeDownloadTokenHandler(svc *services.DeviceService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		userID, _ := UserIDFromContext(r.Context())
		deviceID, ok := parsePathID(w, r, "id")
		if !ok {
			return
		}
		tokenID, ok := parsePathID(w, r, "token_id")
		if !ok {
			return
		}

		count, err := svc.RevokeDownloadToken(r.Context(), userID, deviceID, tokenID)
		switch {
		case errors.Is(err, services.ErrNotOwner):
			writeError(w, http.StatusForbidden, "only the device owner can revoke download tokens")
		case errors.Is(err, services.ErrDeviceNotFound):
			writeError(w, http.StatusNotFound, "device or token not found")
		case err != nil:
			writeError(w, http.StatusInternalServerError, "internal error")
		default:
			writeJSON(w, http.StatusOK, map[string]interface{}{"revoked_bindings_count": count})
		}
	}
}

func addBindingHandler(svc *services.DeviceService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		userID, _ := UserIDFromContext(r.Context())

		var req addBindingRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil || req.DownloadToken == "" {
			writeError(w, http.StatusBadRequest, "download_token is required")
			return
		}

		deviceID, deviceName, err := svc.AddViewerBinding(r.Context(), userID, req.DownloadToken)
		switch {
		case errors.Is(err, services.ErrInvalidDownloadToken):
			writeError(w, http.StatusUnauthorized, "invalid, expired or revoked download token")
		case errors.Is(err, services.ErrSelfBinding), errors.Is(err, services.ErrViewerBindingExists):
			writeError(w, http.StatusConflict, "already bound to this device")
		case err != nil:
			writeError(w, http.StatusInternalServerError, "internal error")
		default:
			writeJSON(w, http.StatusCreated, map[string]interface{}{
				"device_id":   deviceID,
				"device_name": deviceName,
			})
		}
	}
}

// decodeOptionalJSONBody decodes a request body that's allowed to be empty
// (e.g. `{}` or no body at all). A missing body is fine; malformed JSON is
// not and writes a 400, matching the behavior of endpoints with a required body.
func decodeOptionalJSONBody(w http.ResponseWriter, r *http.Request, v interface{}) bool {
	if err := json.NewDecoder(r.Body).Decode(v); err != nil && err != io.EOF {
		writeError(w, http.StatusBadRequest, "invalid request body")
		return false
	}
	return true
}

func parsePathID(w http.ResponseWriter, r *http.Request, name string) (int64, bool) {
	id, err := strconv.ParseInt(r.PathValue(name), 10, 64)
	if err != nil {
		writeError(w, http.StatusNotFound, "not found")
		return 0, false
	}
	return id, true
}
