package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os/signal"
	"syscall"
	"time"

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
	srv := &http.Server{Addr: addr, Handler: router}

	go func() {
		log.Printf("listening on %s", addr)
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatalf("server error: %v", err)
		}
	}()

	// On shutdown, canceling ctx propagates to every in-flight request's
	// r.Context() (including open GET /events SSE streams — see
	// docs/specs/0006-realtime-delivery.md), letting them exit their read
	// loop instead of being killed abruptly by process exit.
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()
	<-ctx.Done()
	stop()
	log.Println("shutting down")

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := srv.Shutdown(shutdownCtx); err != nil {
		log.Printf("graceful shutdown error: %v", err)
	}
}
