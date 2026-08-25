package main

import (
	"log"
	"net/http"

	"sms_forwarder/backend/internal/config"
	"sms_forwarder/backend/internal/handlers"
	"sms_forwarder/backend/internal/storage"
)

func main() {
	cfg := config.Load()

	db, err := storage.Open(cfg.DBPath)
	if err != nil {
		log.Fatalf("open database: %v", err)
	}
	defer db.Close()

	if err := storage.Migrate(db); err != nil {
		log.Fatalf("run migrations: %v", err)
	}

	router := handlers.NewRouter(db, cfg)

	addr := ":" + cfg.Port
	log.Printf("listening on %s", addr)
	if err := http.ListenAndServe(addr, router); err != nil {
		log.Fatalf("server error: %v", err)
	}
}
