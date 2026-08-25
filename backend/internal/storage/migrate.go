package storage

import (
	"database/sql"
	"fmt"
	"io/fs"
	"sort"
	"strings"

	"sms_forwarder/backend/internal/migrations"
)

// Migrate applies all pending "*.up.sql" migrations in filename order,
// tracking applied versions in a schema_migrations table.
func Migrate(db *sql.DB) error {
	if _, err := db.Exec(`CREATE TABLE IF NOT EXISTS schema_migrations (
		version TEXT PRIMARY KEY,
		applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
	)`); err != nil {
		return fmt.Errorf("create schema_migrations table: %w", err)
	}

	entries, err := fs.ReadDir(migrations.FS, ".")
	if err != nil {
		return fmt.Errorf("read migrations dir: %w", err)
	}

	var versions []string
	for _, e := range entries {
		if !e.IsDir() && strings.HasSuffix(e.Name(), ".up.sql") {
			versions = append(versions, e.Name())
		}
	}
	sort.Strings(versions)

	for _, name := range versions {
		version := strings.TrimSuffix(name, ".up.sql")

		var exists int
		if err := db.QueryRow("SELECT COUNT(1) FROM schema_migrations WHERE version = ?", version).Scan(&exists); err != nil {
			return fmt.Errorf("check migration %s: %w", version, err)
		}
		if exists > 0 {
			continue
		}

		content, err := fs.ReadFile(migrations.FS, name)
		if err != nil {
			return fmt.Errorf("read migration %s: %w", name, err)
		}

		tx, err := db.Begin()
		if err != nil {
			return fmt.Errorf("begin tx for migration %s: %w", version, err)
		}

		if _, err := tx.Exec(string(content)); err != nil {
			tx.Rollback()
			return fmt.Errorf("apply migration %s: %w", version, err)
		}

		if _, err := tx.Exec("INSERT INTO schema_migrations (version) VALUES (?)", version); err != nil {
			tx.Rollback()
			return fmt.Errorf("record migration %s: %w", version, err)
		}

		if err := tx.Commit(); err != nil {
			return fmt.Errorf("commit migration %s: %w", version, err)
		}
	}

	return nil
}
