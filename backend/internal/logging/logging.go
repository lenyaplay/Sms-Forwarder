// Package logging provides a structured (slog-based) logger and helpers to
// redact secrets/PII before they reach the log, per docs/specs/0004-request-logging.md.
package logging

import (
	"encoding/json"
	"log/slog"
	"net/url"
	"os"
	"sort"
	"strings"
)

// secretQueryParams are query parameter names never written to logs in the clear.
var secretQueryParams = map[string]bool{
	"upload_token":   true,
	"download_token": true,
}

// secretJSONFields are JSON object field names never written to logs in the clear.
var secretJSONFields = map[string]bool{
	"password":       true,
	"upload_token":   true,
	"download_token": true,
}

const redacted = "[REDACTED]"

// New builds a slog.Logger writing structured JSON to stdout. level is
// "debug" or "info" (case-insensitive); anything else defaults to "info".
func New(level string) *slog.Logger {
	var lvl slog.Level
	switch strings.ToLower(level) {
	case "debug":
		lvl = slog.LevelDebug
	default:
		lvl = slog.LevelInfo
	}
	handler := slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: lvl})
	return slog.New(handler)
}

// RedactQuery parses rawQuery and replaces the value of any known-secret
// parameter with "[REDACTED]", leaving other parameters untouched. The result
// is a plain "key=value&..." string, not percent-encoded — it's meant for
// human-readable logs, not for reuse as a URL.
func RedactQuery(rawQuery string) string {
	values, err := url.ParseQuery(rawQuery)
	if err != nil {
		return redacted
	}

	keys := make([]string, 0, len(values))
	for key := range values {
		keys = append(keys, key)
	}
	sort.Strings(keys)

	parts := make([]string, 0, len(keys))
	for _, key := range keys {
		value := values.Get(key)
		if secretQueryParams[key] {
			value = redacted
		}
		parts = append(parts, key+"="+value)
	}
	return strings.Join(parts, "&")
}

// RedactJSONBody attempts to parse body as a JSON object and replace the
// value of any known-secret field with "[REDACTED]". If body is empty or not
// a JSON object, it returns "[REDACTED]" wholesale rather than risk leaking
// unstructured content.
func RedactJSONBody(body []byte) string {
	if len(body) == 0 {
		return redacted
	}

	var obj map[string]json.RawMessage
	if err := json.Unmarshal(body, &obj); err != nil {
		return redacted
	}

	redactedValue, _ := json.Marshal(redacted)
	for key := range obj {
		if secretJSONFields[key] {
			obj[key] = redactedValue
		}
	}

	out, err := json.Marshal(obj)
	if err != nil {
		return redacted
	}
	return string(out)
}
