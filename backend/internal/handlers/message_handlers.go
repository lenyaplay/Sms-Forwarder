package handlers

import (
	"database/sql"
	"errors"
	"net/http"
	"strconv"
	"time"

	"sms_forwarder/backend/internal/services"
	"sms_forwarder/backend/internal/storage"
)

type messageResponse struct {
	ID            int64   `json:"id"`
	DeviceID      int64   `json:"device_id"`
	Sender        string  `json:"sender"`
	Text          string  `json:"text"`
	SentStamp     *string `json:"sent_stamp"`
	ReceivedStamp *string `json:"received_stamp"`
	SIM           *string `json:"sim"`
	CreatedAt     string  `json:"created_at"`
}

// nullStringPtr converts a sql.NullString to *string (nil when not valid),
// paralleling formatNullTime for sql.NullTime.
func nullStringPtr(s sql.NullString) *string {
	if !s.Valid {
		return nil
	}
	v := s.String
	return &v
}

func messageToResponse(m storage.Message) messageResponse {
	return messageResponse{
		ID:            m.ID,
		DeviceID:      m.DeviceID,
		Sender:        m.Sender,
		Text:          m.Text,
		SentStamp:     nullStringPtr(m.SentStamp),
		ReceivedStamp: nullStringPtr(m.ReceivedStamp),
		SIM:           nullStringPtr(m.SIM),
		CreatedAt:     m.CreatedAt.UTC().Format(time.RFC3339),
	}
}

const (
	defaultMessagesLimit = 50
	maxMessagesLimit     = 100
)

func listMessagesHandler(messageSvc *services.MessageService, deviceSvc *services.DeviceService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		userID, _ := UserIDFromContext(r.Context())
		deviceID, ok := parsePathID(w, r, "id")
		if !ok {
			return
		}

		query := r.URL.Query()

		limit := defaultMessagesLimit
		if raw := query.Get("limit"); raw != "" {
			parsed, err := strconv.Atoi(raw)
			if err != nil || parsed < 1 || parsed > maxMessagesLimit {
				writeError(w, http.StatusBadRequest, "limit must be an integer between 1 and 100")
				return
			}
			limit = parsed
		}

		var beforeID *int64
		if raw := query.Get("before_id"); raw != "" {
			parsed, err := strconv.ParseInt(raw, 10, 64)
			if err != nil {
				writeError(w, http.StatusBadRequest, "before_id must be an integer")
				return
			}
			beforeID = &parsed
		}

		var since, until *time.Time
		if raw := query.Get("since"); raw != "" {
			parsed, err := time.Parse(time.RFC3339, raw)
			if err != nil {
				writeError(w, http.StatusBadRequest, "since must be an RFC3339 timestamp")
				return
			}
			since = &parsed
		}
		if raw := query.Get("until"); raw != "" {
			parsed, err := time.Parse(time.RFC3339, raw)
			if err != nil {
				writeError(w, http.StatusBadRequest, "until must be an RFC3339 timestamp")
				return
			}
			until = &parsed
		}
		if since != nil && until != nil && since.After(*until) {
			writeError(w, http.StatusBadRequest, "since must not be after until")
			return
		}

		messages, err := messageSvc.ListMessages(r.Context(), deviceSvc, userID, deviceID, services.ListMessagesInput{
			Since:    since,
			Until:    until,
			BeforeID: beforeID,
			Limit:    limit,
		})
		switch {
		case errors.Is(err, storage.ErrDeviceNotFound):
			writeError(w, http.StatusNotFound, "device not found")
			return
		case errors.Is(err, services.ErrInvalidDateRange):
			writeError(w, http.StatusBadRequest, "since must not be after until")
			return
		case err != nil:
			writeError(w, http.StatusInternalServerError, "internal error")
			return
		}

		resp := make([]messageResponse, 0, len(messages))
		for _, m := range messages {
			resp = append(resp, messageToResponse(m))
		}

		var nextBeforeID *int64
		if len(messages) == limit {
			id := messages[len(messages)-1].ID
			nextBeforeID = &id
		}

		writeJSON(w, http.StatusOK, map[string]interface{}{
			"messages":       resp,
			"next_before_id": nextBeforeID,
		})
	}
}
