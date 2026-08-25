package handlers

import (
	"bytes"
	"database/sql"
	"io"
	"log/slog"
	"net/http"
	"time"

	"sms_forwarder/backend/internal/config"
	"sms_forwarder/backend/internal/logging"
	"sms_forwarder/backend/internal/realtime"
	"sms_forwarder/backend/internal/services"
)

// eventsHeartbeatInterval is how often GET /events sends a keep-alive SSE
// comment on an idle connection, per docs/specs/0006-realtime-delivery.md
// assumption 10.
const eventsHeartbeatInterval = 30 * time.Second

// NewRouter wires up all HTTP routes.
func NewRouter(db *sql.DB, cfg config.Config) http.Handler {
	return NewRouterWithLogger(db, cfg, logging.New(cfg.LogLevel))
}

// NewRouterWithLogger is like NewRouter but takes an explicit logger, so
// tests can capture log output instead of writing to stdout.
func NewRouterWithLogger(db *sql.DB, cfg config.Config, logger *slog.Logger) http.Handler {
	mux := http.NewServeMux()

	mux.HandleFunc("GET /healthz", healthCheck(db))

	authService := services.NewAuthService(db, cfg.JWTSecret, cfg.AccessTokenTTL, cfg.RefreshTokenTTL)
	mux.HandleFunc("POST /auth/register", registerHandler(authService))
	mux.HandleFunc("POST /auth/login", loginHandler(authService))
	mux.HandleFunc("POST /auth/refresh", refreshHandler(authService))
	mux.HandleFunc("POST /auth/logout", logoutHandler(authService))

	deviceService := services.NewDeviceService(db)
	requireAuth := RequireAuth(cfg.JWTSecret)
	mux.Handle("POST /devices", requireAuth(createDeviceHandler(deviceService)))
	mux.Handle("GET /devices", requireAuth(listDevicesHandler(deviceService)))
	mux.Handle("GET /devices/{id}", requireAuth(getDeviceHandler(deviceService)))
	mux.Handle("PATCH /devices/{id}", requireAuth(renameDeviceHandler(deviceService)))
	mux.Handle("DELETE /devices/{id}", requireAuth(deleteDeviceHandler(deviceService)))
	mux.Handle("POST /devices/{id}/upload_token", requireAuth(reissueUploadTokenHandler(deviceService)))
	mux.Handle("POST /devices/{id}/download_tokens", requireAuth(createDownloadTokenHandler(deviceService)))
	mux.Handle("GET /devices/{id}/download_tokens", requireAuth(listDownloadTokensHandler(deviceService)))
	mux.Handle("DELETE /devices/{id}/download_tokens/{token_id}", requireAuth(revokeDownloadTokenHandler(deviceService)))
	mux.Handle("POST /devices/bindings", requireAuth(addBindingHandler(deviceService)))

	hub := realtime.NewHub()
	messageService := services.NewMessageService(db, hub)
	mux.Handle("POST /webhook", withBodyLogging(logger, webhookHandler(messageService)))
	mux.Handle("GET /devices/{id}/messages", requireAuth(listMessagesHandler(messageService, deviceService)))
	mux.Handle("GET /events", eventsHandler(deviceService, hub, cfg.JWTSecret, eventsHeartbeatInterval))

	return requestLogger(logger, mux)
}

// requestLogger logs method, path, status, duration and (if authenticated)
// user_id for every request at info level. At debug level it additionally
// logs the redacted query string. See docs/specs/0004-request-logging.md.
func requestLogger(logger *slog.Logger, next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		r, holder := withUserIDHolder(r)
		sw := &statusRecorder{ResponseWriter: w, status: http.StatusOK}
		next.ServeHTTP(sw, r)

		attrs := []any{
			"method", r.Method,
			"path", r.URL.Path,
			"status", sw.status,
			"duration_ms", time.Since(start).Milliseconds(),
		}
		if holder.ok {
			attrs = append(attrs, "user_id", holder.id)
		}
		if logger.Enabled(r.Context(), slog.LevelDebug) && r.URL.RawQuery != "" {
			attrs = append(attrs, "query", logging.RedactQuery(r.URL.RawQuery))
		}
		logger.Info("request", attrs...)
	})
}

// withBodyLogging logs the (redacted) request body at debug level before
// delegating to next. Kept separate from requestLogger because only a few
// routes (currently the webhook) have request bodies worth logging.
func withBodyLogging(logger *slog.Logger, next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if logger.Enabled(r.Context(), slog.LevelDebug) {
			body, err := io.ReadAll(r.Body)
			if err == nil {
				logger.Debug("request body", "path", r.URL.Path, "body", logging.RedactJSONBody(body))
				r.Body = io.NopCloser(bytes.NewReader(body))
			}
		}
		next(w, r)
	}
}

type statusRecorder struct {
	http.ResponseWriter
	status int
}

func (r *statusRecorder) WriteHeader(status int) {
	r.status = status
	r.ResponseWriter.WriteHeader(status)
}

// Flush passes through to the underlying ResponseWriter's http.Flusher, so
// streaming responses (SSE, /events) work through this wrapper.
func (r *statusRecorder) Flush() {
	if f, ok := r.ResponseWriter.(http.Flusher); ok {
		f.Flush()
	}
}

func healthCheck(db *sql.DB) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if err := db.PingContext(r.Context()); err != nil {
			w.WriteHeader(http.StatusServiceUnavailable)
			w.Write([]byte("db unavailable"))
			return
		}
		w.WriteHeader(http.StatusOK)
		w.Write([]byte("ok"))
	}
}
