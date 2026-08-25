package handlers

import (
	"database/sql"
	"net/http"
)

// NewRouter wires up all HTTP routes. At this stage only a health-check
// endpoint exists; auth, device, webhook and message routes are added
// in later milestones.
func NewRouter(db *sql.DB) http.Handler {
	mux := http.NewServeMux()

	mux.HandleFunc("GET /healthz", healthCheck(db))

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
