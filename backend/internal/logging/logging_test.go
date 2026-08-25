package logging

import (
	"context"
	"log/slog"
	"strings"
	"testing"
)

func TestNew_Levels(t *testing.T) {
	cases := []struct {
		level string
		want  slog.Level
	}{
		{"debug", slog.LevelDebug},
		{"DEBUG", slog.LevelDebug},
		{"info", slog.LevelInfo},
		{"", slog.LevelInfo},
		{"garbage", slog.LevelInfo},
	}
	for _, c := range cases {
		logger := New(c.level)
		if !logger.Enabled(context.Background(), c.want) {
			t.Errorf("New(%q): expected level %v to be enabled", c.level, c.want)
		}
		if c.want == slog.LevelInfo && logger.Enabled(context.Background(), slog.LevelDebug) {
			t.Errorf("New(%q): expected debug to be disabled at info level", c.level)
		}
	}
}

func TestRedactQuery(t *testing.T) {
	cases := []struct {
		name  string
		query string
		want  string
	}{
		{"upload_token redacted", "upload_token=secret123", "upload_token=[REDACTED]"},
		{"download_token redacted", "download_token=secret456", "download_token=[REDACTED]"},
		{"non-secret untouched", "name=Phone&label=family", "label=family&name=Phone"},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			got := RedactQuery(c.query)
			if got != c.want {
				t.Errorf("RedactQuery(%q) = %q, want %q", c.query, got, c.want)
			}
		})
	}

	// mixed: secret + non-secret in the same query, non-secret must survive, secret must not leak
	got := RedactQuery("upload_token=leak-me&sim=1")
	if got == "" {
		t.Fatalf("RedactQuery returned empty for mixed query")
	}
	if strings.Contains(got, "leak-me") {
		t.Errorf("RedactQuery leaked secret value: %q", got)
	}
	if !strings.Contains(got, "sim=1") {
		t.Errorf("RedactQuery dropped non-secret param: %q", got)
	}
}

func TestRedactJSONBody(t *testing.T) {
	cases := []struct {
		name string
		body string
	}{
		{"empty body", ""},
		{"invalid JSON", "not json"},
		{"JSON array not object", `["a","b"]`},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			got := RedactJSONBody([]byte(c.body))
			if got != "[REDACTED]" {
				t.Errorf("RedactJSONBody(%q) = %q, want [REDACTED]", c.body, got)
			}
		})
	}

	withSecret := RedactJSONBody([]byte(`{"from":"+1234","text":"hello","upload_token":"leak-me"}`))
	if strings.Contains(withSecret, "leak-me") {
		t.Errorf("RedactJSONBody leaked secret: %q", withSecret)
	}
	if !strings.Contains(withSecret, "+1234") || !strings.Contains(withSecret, "hello") {
		t.Errorf("RedactJSONBody dropped non-secret fields: %q", withSecret)
	}

	withPassword := RedactJSONBody([]byte(`{"login":"bob","password":"hunter2"}`))
	if strings.Contains(withPassword, "hunter2") {
		t.Errorf("RedactJSONBody leaked password: %q", withPassword)
	}
}
