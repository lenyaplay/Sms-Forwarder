package services

import (
	"context"
	"database/sql"
	"errors"
	"time"

	"sms_forwarder/backend/internal/auth"
	"sms_forwarder/backend/internal/storage"
)

var (
	ErrDeviceNotFound      = storage.ErrDeviceNotFound
	ErrAccessDenied        = errors.New("no access to this device")
	ErrNotOwner            = errors.New("only the device owner can perform this action")
	ErrInvalidDownloadToken = errors.New("invalid, expired or revoked download token")
	ErrViewerBindingExists = storage.ErrViewerBindingExists
	ErrSelfBinding         = errors.New("device owner cannot bind as a viewer of their own device")
)

const deviceRoleOwner = "owner"
const deviceRoleViewer = "viewer"

type DeviceService struct {
	db *sql.DB
}

func NewDeviceService(db *sql.DB) *DeviceService {
	return &DeviceService{db: db}
}

func ttlToExpiry(ttl *time.Duration) sql.NullTime {
	if ttl == nil {
		return sql.NullTime{}
	}
	return sql.NullTime{Time: time.Now().UTC().Add(*ttl), Valid: true}
}

func (s *DeviceService) CreateDevice(ctx context.Context, ownerUserID int64, name string, uploadTokenTTL *time.Duration) (storage.Device, error) {
	token, err := auth.GenerateRefreshToken()
	if err != nil {
		return storage.Device{}, err
	}
	return storage.CreateDevice(ctx, s.db, ownerUserID, name, token, ttlToExpiry(uploadTokenTTL))
}

// ListDevices returns devices owned by the user and devices the user has viewer access to.
func (s *DeviceService) ListDevices(ctx context.Context, userID int64) (owned, viewer []storage.Device, err error) {
	owned, err = storage.ListOwnedDevices(ctx, s.db, userID)
	if err != nil {
		return nil, nil, err
	}
	viewer, err = storage.ListViewerDevices(ctx, s.db, userID)
	if err != nil {
		return nil, nil, err
	}
	return owned, viewer, nil
}

// GetDevice returns the device and the caller's role on it ("owner" or "viewer").
// Returns ErrDeviceNotFound both when the device doesn't exist and when the
// caller has no relation to it, so callers never leak existence of devices
// belonging to other users.
func (s *DeviceService) GetDevice(ctx context.Context, userID, deviceID int64) (storage.Device, string, error) {
	device, err := storage.GetDeviceByID(ctx, s.db, deviceID)
	if err != nil {
		return storage.Device{}, "", err
	}

	if device.OwnerUserID == userID {
		return device, deviceRoleOwner, nil
	}

	viewerDevices, err := storage.ListViewerDevices(ctx, s.db, userID)
	if err != nil {
		return storage.Device{}, "", err
	}
	for _, d := range viewerDevices {
		if d.ID == deviceID {
			return device, deviceRoleViewer, nil
		}
	}

	return storage.Device{}, "", ErrDeviceNotFound
}

func (s *DeviceService) RenameDevice(ctx context.Context, userID, deviceID int64, name string) (storage.Device, error) {
	device, err := s.requireOwner(ctx, userID, deviceID)
	if err != nil {
		return storage.Device{}, err
	}
	if err := storage.UpdateDeviceName(ctx, s.db, deviceID, name); err != nil {
		return storage.Device{}, err
	}
	device.Name = name
	return device, nil
}

func (s *DeviceService) DeleteDevice(ctx context.Context, userID, deviceID int64) error {
	if _, err := s.requireOwner(ctx, userID, deviceID); err != nil {
		return err
	}
	return storage.DeleteDevice(ctx, s.db, deviceID)
}

func (s *DeviceService) ReissueUploadToken(ctx context.Context, userID, deviceID int64, ttl *time.Duration) (storage.Device, error) {
	if _, err := s.requireOwner(ctx, userID, deviceID); err != nil {
		return storage.Device{}, err
	}

	token, err := auth.GenerateRefreshToken()
	if err != nil {
		return storage.Device{}, err
	}
	expiresAt := ttlToExpiry(ttl)
	if err := storage.ReissueUploadToken(ctx, s.db, deviceID, token, expiresAt); err != nil {
		return storage.Device{}, err
	}
	return storage.GetDeviceByID(ctx, s.db, deviceID)
}

func (s *DeviceService) CreateDownloadToken(ctx context.Context, userID, deviceID int64, label *string, ttl *time.Duration) (storage.DeviceDownloadToken, error) {
	if _, err := s.requireOwner(ctx, userID, deviceID); err != nil {
		return storage.DeviceDownloadToken{}, err
	}

	token, err := auth.GenerateRefreshToken()
	if err != nil {
		return storage.DeviceDownloadToken{}, err
	}

	var labelNull sql.NullString
	if label != nil {
		labelNull = sql.NullString{String: *label, Valid: true}
	}

	return storage.CreateDownloadToken(ctx, s.db, deviceID, token, labelNull, ttlToExpiry(ttl))
}

func (s *DeviceService) ListDownloadTokens(ctx context.Context, userID, deviceID int64) ([]storage.DeviceDownloadToken, error) {
	if _, err := s.requireOwner(ctx, userID, deviceID); err != nil {
		return nil, err
	}
	return storage.ListActiveDownloadTokens(ctx, s.db, deviceID)
}

func (s *DeviceService) RevokeDownloadToken(ctx context.Context, userID, deviceID, tokenID int64) (int, error) {
	if _, err := s.requireOwner(ctx, userID, deviceID); err != nil {
		return 0, err
	}

	count, err := storage.RevokeDownloadToken(ctx, s.db, deviceID, tokenID)
	if errors.Is(err, storage.ErrDownloadTokenNotFound) {
		return 0, ErrDeviceNotFound
	}
	return count, err
}

// AddViewerBinding lets the caller add themselves as a viewer of the device
// identified by downloadToken. Returns the device ID and name.
func (s *DeviceService) AddViewerBinding(ctx context.Context, userID int64, downloadToken string) (int64, string, error) {
	token, err := storage.GetActiveDownloadTokenByValue(ctx, s.db, downloadToken)
	if errors.Is(err, storage.ErrDownloadTokenNotFound) {
		return 0, "", ErrInvalidDownloadToken
	}
	if err != nil {
		return 0, "", err
	}

	device, err := storage.GetDeviceByID(ctx, s.db, token.DeviceID)
	if err != nil {
		return 0, "", err
	}

	if device.OwnerUserID == userID {
		return 0, "", ErrSelfBinding
	}

	if _, err := storage.CreateViewerBinding(ctx, s.db, device.ID, userID, token.ID); err != nil {
		return 0, "", err
	}

	return device.ID, device.Name, nil
}

// requireOwner fetches the device and verifies the caller owns it. Returns
// ErrDeviceNotFound if it doesn't exist, ErrNotOwner if it exists but belongs
// to someone else.
func (s *DeviceService) requireOwner(ctx context.Context, userID, deviceID int64) (storage.Device, error) {
	device, err := storage.GetDeviceByID(ctx, s.db, deviceID)
	if err != nil {
		return storage.Device{}, err
	}
	if device.OwnerUserID != userID {
		return storage.Device{}, ErrNotOwner
	}
	return device, nil
}
