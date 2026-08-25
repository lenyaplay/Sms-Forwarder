package storage

import (
	"context"
	"database/sql"
	"errors"
	"time"
)

var ErrViewerBindingExists = errors.New("viewer binding already exists")

type ViewerBinding struct {
	ID              int64
	DeviceID        int64
	UserID          int64
	DownloadTokenID int64
	CreatedAt       time.Time
}

func CreateViewerBinding(ctx context.Context, db *sql.DB, deviceID, userID, downloadTokenID int64) (ViewerBinding, error) {
	res, err := db.ExecContext(ctx,
		"INSERT INTO viewer_bindings (device_id, user_id, download_token_id) VALUES (?, ?, ?)",
		deviceID, userID, downloadTokenID)
	if err != nil {
		if isUniqueConstraintErr(err) {
			return ViewerBinding{}, ErrViewerBindingExists
		}
		return ViewerBinding{}, err
	}

	id, err := res.LastInsertId()
	if err != nil {
		return ViewerBinding{}, err
	}

	var vb ViewerBinding
	err = db.QueryRowContext(ctx,
		"SELECT id, device_id, user_id, download_token_id, created_at FROM viewer_bindings WHERE id = ?", id).
		Scan(&vb.ID, &vb.DeviceID, &vb.UserID, &vb.DownloadTokenID, &vb.CreatedAt)
	if err != nil {
		return ViewerBinding{}, err
	}
	return vb, nil
}
