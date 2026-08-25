package storage

import (
	"context"
	"database/sql"
	"errors"
	"time"
)

var ErrDeviceNotFound = errors.New("device not found")

type Device struct {
	ID                    int64
	OwnerUserID           int64
	Name                  string
	UploadToken           string
	UploadTokenExpiresAt  sql.NullTime
	HMACSecret            sql.NullString
	CreatedAt             time.Time
}

func CreateDevice(ctx context.Context, db *sql.DB, ownerUserID int64, name, uploadToken string, uploadTokenExpiresAt sql.NullTime) (Device, error) {
	res, err := db.ExecContext(ctx,
		"INSERT INTO devices (owner_user_id, name, upload_token, upload_token_expires_at) VALUES (?, ?, ?, ?)",
		ownerUserID, name, uploadToken, uploadTokenExpiresAt)
	if err != nil {
		return Device{}, err
	}

	id, err := res.LastInsertId()
	if err != nil {
		return Device{}, err
	}

	return GetDeviceByID(ctx, db, id)
}

func GetDeviceByID(ctx context.Context, db *sql.DB, id int64) (Device, error) {
	var d Device
	err := db.QueryRowContext(ctx,
		"SELECT id, owner_user_id, name, upload_token, upload_token_expires_at, hmac_secret, created_at FROM devices WHERE id = ?", id).
		Scan(&d.ID, &d.OwnerUserID, &d.Name, &d.UploadToken, &d.UploadTokenExpiresAt, &d.HMACSecret, &d.CreatedAt)
	if errors.Is(err, sql.ErrNoRows) {
		return Device{}, ErrDeviceNotFound
	}
	if err != nil {
		return Device{}, err
	}
	return d, nil
}

func GetDeviceByUploadToken(ctx context.Context, db *sql.DB, uploadToken string) (Device, error) {
	var d Device
	err := db.QueryRowContext(ctx,
		"SELECT id, owner_user_id, name, upload_token, upload_token_expires_at, hmac_secret, created_at FROM devices WHERE upload_token = ?", uploadToken).
		Scan(&d.ID, &d.OwnerUserID, &d.Name, &d.UploadToken, &d.UploadTokenExpiresAt, &d.HMACSecret, &d.CreatedAt)
	if errors.Is(err, sql.ErrNoRows) {
		return Device{}, ErrDeviceNotFound
	}
	if err != nil {
		return Device{}, err
	}
	return d, nil
}

func ListOwnedDevices(ctx context.Context, db *sql.DB, ownerUserID int64) ([]Device, error) {
	rows, err := db.QueryContext(ctx,
		"SELECT id, owner_user_id, name, upload_token, upload_token_expires_at, hmac_secret, created_at FROM devices WHERE owner_user_id = ? ORDER BY id", ownerUserID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanDevices(rows)
}

// ListViewerDevices returns devices the user has an active viewer_binding
// for. A binding is active only if its originating download_token is not
// expired (revocation is enforced separately: RevokeDownloadToken deletes the
// viewer_bindings row outright, so a revoked token's bindings never appear
// here regardless of this expiry check).
func ListViewerDevices(ctx context.Context, db *sql.DB, userID int64) ([]Device, error) {
	rows, err := db.QueryContext(ctx,
		`SELECT d.id, d.owner_user_id, d.name, d.upload_token, d.upload_token_expires_at, d.hmac_secret, d.created_at
		 FROM devices d
		 JOIN viewer_bindings vb ON vb.device_id = d.id
		 JOIN device_download_tokens t ON t.id = vb.download_token_id
		 WHERE vb.user_id = ? AND (t.expires_at IS NULL OR t.expires_at > CURRENT_TIMESTAMP)
		 ORDER BY d.id`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanDevices(rows)
}

func scanDevices(rows *sql.Rows) ([]Device, error) {
	var devices []Device
	for rows.Next() {
		var d Device
		if err := rows.Scan(&d.ID, &d.OwnerUserID, &d.Name, &d.UploadToken, &d.UploadTokenExpiresAt, &d.HMACSecret, &d.CreatedAt); err != nil {
			return nil, err
		}
		devices = append(devices, d)
	}
	return devices, rows.Err()
}

func UpdateDeviceName(ctx context.Context, db *sql.DB, id int64, name string) error {
	res, err := db.ExecContext(ctx, "UPDATE devices SET name = ? WHERE id = ?", name, id)
	if err != nil {
		return err
	}
	n, err := res.RowsAffected()
	if err != nil {
		return err
	}
	if n == 0 {
		return ErrDeviceNotFound
	}
	return nil
}

func DeleteDevice(ctx context.Context, db *sql.DB, id int64) error {
	res, err := db.ExecContext(ctx, "DELETE FROM devices WHERE id = ?", id)
	if err != nil {
		return err
	}
	n, err := res.RowsAffected()
	if err != nil {
		return err
	}
	if n == 0 {
		return ErrDeviceNotFound
	}
	return nil
}

func ReissueUploadToken(ctx context.Context, db *sql.DB, id int64, newToken string, expiresAt sql.NullTime) error {
	res, err := db.ExecContext(ctx,
		"UPDATE devices SET upload_token = ?, upload_token_expires_at = ? WHERE id = ?", newToken, expiresAt, id)
	if err != nil {
		return err
	}
	n, err := res.RowsAffected()
	if err != nil {
		return err
	}
	if n == 0 {
		return ErrDeviceNotFound
	}
	return nil
}
