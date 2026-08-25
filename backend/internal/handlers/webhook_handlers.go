package handlers

import (
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"time"

	"sms_forwarder/backend/internal/services"
)

// flexibleString unmarshals a JSON string or number into a string. The Gateway
// App template embeds sentStamp/receivedStamp unquoted by default, so they
// arrive as JSON numbers (Unix ms), not strings.
type flexibleString string

func (s *flexibleString) UnmarshalJSON(data []byte) error {
	if string(data) == "null" {
		*s = ""
		return nil
	}
	if len(data) > 0 && data[0] == '"' {
		var str string
		if err := json.Unmarshal(data, &str); err != nil {
			return err
		}
		*s = flexibleString(str)
		return nil
	}
	*s = flexibleString(data)
	return nil
}

type webhookMessageRequest struct {
	From          string         `json:"from"`
	Text          string         `json:"text"`
	SentStamp     flexibleString `json:"sentStamp"`
	ReceivedStamp flexibleString `json:"receivedStamp"`
	SIM           flexibleString `json:"sim"`
}

type webhookResponse struct {
	ID        int64  `json:"id"`
	CreatedAt string `json:"created_at"`
	Duplicate bool   `json:"duplicate,omitempty"`
}

func webhookHandler(svc *services.MessageService) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		uploadToken := r.URL.Query().Get("upload_token")
		if uploadToken == "" {
			writeError(w, http.StatusBadRequest, "upload_token is required")
			return
		}

		rawBody, err := io.ReadAll(r.Body)
		if err != nil {
			writeError(w, http.StatusBadRequest, "invalid request body")
			return
		}
		var req webhookMessageRequest
		if err := json.Unmarshal(rawBody, &req); err != nil {
			writeError(w, http.StatusBadRequest, "invalid request body")
			return
		}

		msg, duplicate, err := svc.IngestWebhook(r.Context(), uploadToken, rawBody, r.Header.Get("X-Signature"), services.IncomingMessage{
			From:          req.From,
			Text:          req.Text,
			SentStamp:     string(req.SentStamp),
			ReceivedStamp: string(req.ReceivedStamp),
			SIM:           string(req.SIM),
		})
		if err != nil {
			switch {
			case errors.Is(err, services.ErrMissingSender), errors.Is(err, services.ErrMissingText):
				writeError(w, http.StatusBadRequest, err.Error())
			case errors.Is(err, services.ErrInvalidUploadToken), errors.Is(err, services.ErrInvalidSignature):
				writeError(w, http.StatusUnauthorized, err.Error())
			default:
				writeError(w, http.StatusInternalServerError, "internal error")
			}
			return
		}

		status := http.StatusCreated
		if duplicate {
			status = http.StatusOK
		}
		writeJSON(w, status, webhookResponse{
			ID:        msg.ID,
			CreatedAt: msg.CreatedAt.UTC().Format(time.RFC3339),
			Duplicate: duplicate,
		})
	}
}
