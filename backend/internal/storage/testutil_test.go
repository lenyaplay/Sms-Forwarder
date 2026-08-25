package storage

import (
	"database/sql"
	"testing"
)

// newTestDB opens an in-memory SQLite database with migrations applied, for use in tests.
func newTestDB(t *testing.T) *sql.DB {
	t.Helper()

	db, err := Open("file::memory:?cache=shared")
	if err != nil {
		t.Fatalf("Open: %v", err)
	}
	t.Cleanup(func() { db.Close() })

	if err := Migrate(db); err != nil {
		t.Fatalf("Migrate: %v", err)
	}

	return db
}
