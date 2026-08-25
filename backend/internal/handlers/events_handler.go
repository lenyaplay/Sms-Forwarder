package handlers

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"time"

	"sms_forwarder/backend/internal/auth"
	"sms_forwarder/backend/internal/realtime"
	"sms_forwarder/backend/internal/services"
)

const maxEventsDeviceIDs = 20

// eventsHandler serves GET /events?device_ids=1,2,3&access_token=... — an SSE
// stream of newly ingested messages for the given devices, per
// docs/specs/0006-realtime-delivery.md. Auth is via query param (not
// requireAuth's Authorization header) because browser EventSource cannot set
// custom headers at connect time.
func eventsHandler(deviceSvc *services.DeviceService, hub *realtime.Hub, jwtSecret string, heartbeatInterval time.Duration) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		token := r.URL.Query().Get("access_token")
		if token == "" {
			writeError(w, http.StatusUnauthorized, "access_token is required")
			return
		}
		userID, err := auth.ParseAccessToken(token, jwtSecret)
		if err != nil {
			writeError(w, http.StatusUnauthorized, "invalid access token")
			return
		}

		rawIDs := r.URL.Query().Get("device_ids")
		if rawIDs == "" {
			writeError(w, http.StatusBadRequest, "device_ids is required")
			return
		}
		parts := strings.Split(rawIDs, ",")
		if len(parts) > maxEventsDeviceIDs {
			writeError(w, http.StatusBadRequest, fmt.Sprintf("device_ids must not have more than %d elements", maxEventsDeviceIDs))
			return
		}
		deviceIDs := make([]int64, 0, len(parts))
		for _, p := range parts {
			id, err := strconv.ParseInt(strings.TrimSpace(p), 10, 64)
			if err != nil {
				writeError(w, http.StatusBadRequest, "device_ids must be a comma-separated list of integers")
				return
			}
			deviceIDs = append(deviceIDs, id)
		}

		owned, viewer, err := deviceSvc.ListDevices(r.Context(), userID)
		if err != nil {
			writeError(w, http.StatusInternalServerError, "internal error")
			return
		}
		accessible := make(map[int64]bool, len(owned)+len(viewer))
		for _, d := range owned {
			accessible[d.ID] = true
		}
		for _, d := range viewer {
			accessible[d.ID] = true
		}
		for _, id := range deviceIDs {
			if !accessible[id] {
				writeError(w, http.StatusNotFound, "device not found")
				return
			}
		}

		flusher, ok := w.(http.Flusher)
		if !ok {
			writeError(w, http.StatusInternalServerError, "streaming unsupported")
			return
		}

		w.Header().Set("Content-Type", "text/event-stream")
		w.Header().Set("Cache-Control", "no-cache")
		w.Header().Set("Connection", "keep-alive")
		w.WriteHeader(http.StatusOK)
		flusher.Flush()

		ch, unsubscribe := hub.Subscribe(deviceIDs)
		defer unsubscribe()

		ticker := time.NewTicker(heartbeatInterval)
		defer ticker.Stop()

		for {
			select {
			case msg := <-ch:
				payload, err := json.Marshal(messageToResponse(msg))
				if err != nil {
					continue
				}
				fmt.Fprintf(w, "event: message\ndata: %s\n\n", payload)
				flusher.Flush()
			case <-ticker.C:
				fmt.Fprint(w, ": ping\n\n")
				flusher.Flush()
			case <-r.Context().Done():
				return
			}
		}
	}
}
