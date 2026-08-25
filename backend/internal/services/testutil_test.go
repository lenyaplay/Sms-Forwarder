package services

import (
	"database/sql"
	"testing"

	"sms_forwarder/backend/internal/storage"
)

func newTestDB(t *testing.T) *sql.DB {
	t.Helper()

	db, err := storage.Open("file::memory:?cache=shared")
	if err != nil {
		t.Fatalf("Open: %v", err)
	}
	t.Cleanup(func() { db.Close() })

	if err := storage.Migrate(db); err != nil {
		t.Fatalf("Migrate: %v", err)
	}

	return db
}
