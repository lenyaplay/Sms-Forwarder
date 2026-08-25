package storage

import (
	"context"
	"database/sql"
	"errors"
	"time"
)

var ErrDownloadTokenNotFound = errors.New("download token not found")

type DeviceDownloadToken struct {
	ID            int64
	DeviceID      int64
	Token         string
	Label         sql.NullString
	ExpiresAt     sql.NullTime
	RevokedAt     sql.NullTime
	CreatedAt     time.Time
	BindingsCount int
}

func CreateDownloadToken(ctx context.Context, db *sql.DB, deviceID int64, token string, label sql.NullString, expiresAt sql.NullTime) (DeviceDownloadToken, error) {
	res, err := db.ExecContext(ctx,
		"INSERT INTO device_download_tokens (device_id, token, label, expires_at) VALUES (?, ?, ?, ?)",
		deviceID, token, label, expiresAt)
	if err != nil {
		return DeviceDownloadToken{}, err
	}

	id, err := res.LastInsertId()
	if err != nil {
		return DeviceDownloadToken{}, err
	}

	var t DeviceDownloadToken
	err = db.QueryRowContext(ctx,
		"SELECT id, device_id, token, label, expires_at, revoked_at, created_at FROM device_download_tokens WHERE id = ?", id).
		Scan(&t.ID, &t.DeviceID, &t.Token, &t.Label, &t.ExpiresAt, &t.RevokedAt, &t.CreatedAt)
	if err != nil {
		return DeviceDownloadToken{}, err
	}
	return t, nil
}

// ListActiveDownloadTokens returns non-revoked, non-expired tokens for a device
// along with how many viewer_bindings currently exist per token.
func ListActiveDownloadTokens(ctx context.Context, db *sql.DB, deviceID int64) ([]DeviceDownloadToken, error) {
	rows, err := db.QueryContext(ctx,
		`SELECT t.id, t.device_id, t.token, t.label, t.expires_at, t.revoked_at, t.created_at,
		        COUNT(vb.id) AS bindings_count
		 FROM device_download_tokens t
		 LEFT JOIN viewer_bindings vb ON vb.download_token_id = t.id
		 WHERE t.device_id = ? AND t.revoked_at IS NULL
		   AND (t.expires_at IS NULL OR datetime(t.expires_at) > datetime('now'))
		 GROUP BY t.id
		 ORDER BY t.id`, deviceID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var tokens []DeviceDownloadToken
	for rows.Next() {
		var t DeviceDownloadToken
		if err := rows.Scan(&t.ID, &t.DeviceID, &t.Token, &t.Label, &t.ExpiresAt, &t.RevokedAt, &t.CreatedAt, &t.BindingsCount); err != nil {
			return nil, err
		}
		tokens = append(tokens, t)
	}
	return tokens, rows.Err()
}

// GetActiveDownloadTokenByValue looks up a download token that is neither
// revoked nor expired. Unknown, expired and revoked tokens are all reported
// as ErrDownloadTokenNotFound since callers treat them identically (401).
func GetActiveDownloadTokenByValue(ctx context.Context, db *sql.DB, token string) (DeviceDownloadToken, error) {
	var t DeviceDownloadToken
	err := db.QueryRowContext(ctx,
		`SELECT id, device_id, token, label, expires_at, revoked_at, created_at
		 FROM device_download_tokens
		 WHERE token = ? AND revoked_at IS NULL AND (expires_at IS NULL OR datetime(expires_at) > datetime('now'))`, token).
		Scan(&t.ID, &t.DeviceID, &t.Token, &t.Label, &t.ExpiresAt, &t.RevokedAt, &t.CreatedAt)
	if errors.Is(err, sql.ErrNoRows) {
		return DeviceDownloadToken{}, ErrDownloadTokenNotFound
	}
	if err != nil {
		return DeviceDownloadToken{}, err
	}
	return t, nil
}

// RevokeDownloadToken marks a device's download token revoked and deletes only
// the viewer_bindings created through that specific token, leaving bindings
// created via the device's other tokens untouched. Returns the number of
// bindings removed. ErrDownloadTokenNotFound if no such active token exists
// for the given device.
func RevokeDownloadToken(ctx context.Context, db *sql.DB, deviceID, tokenID int64) (int, error) {
	tx, err := db.BeginTx(ctx, nil)
	if err != nil {
		return 0, err
	}
	defer tx.Rollback()

	res, err := tx.ExecContext(ctx,
		"UPDATE device_download_tokens SET revoked_at = CURRENT_TIMESTAMP WHERE id = ? AND device_id = ? AND revoked_at IS NULL",
		tokenID, deviceID)
	if err != nil {
		return 0, err
	}
	n, err := res.RowsAffected()
	if err != nil {
		return 0, err
	}
	if n == 0 {
		return 0, ErrDownloadTokenNotFound
	}

	res, err = tx.ExecContext(ctx, "DELETE FROM viewer_bindings WHERE download_token_id = ?", tokenID)
	if err != nil {
		return 0, err
	}
	revoked, err := res.RowsAffected()
	if err != nil {
		return 0, err
	}

	if err := tx.Commit(); err != nil {
		return 0, err
	}

	return int(revoked), nil
}
