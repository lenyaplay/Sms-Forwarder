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
