package handlers

import (
	"database/sql"
	"net/http"

	"sms_forwarder/backend/internal/config"
	"sms_forwarder/backend/internal/services"
)

// NewRouter wires up all HTTP routes.
func NewRouter(db *sql.DB, cfg config.Config) http.Handler {
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

	return mux
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
