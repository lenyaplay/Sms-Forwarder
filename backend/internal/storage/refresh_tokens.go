package storage

import (
	"context"
	"database/sql"
	"errors"
	"time"
)

var ErrRefreshTokenNotFound = errors.New("refresh token not found")

type RefreshToken struct {
	ID        int64
	UserID    int64
	TokenHash string
	ExpiresAt time.Time
	RevokedAt sql.NullTime
}

func SaveRefreshToken(ctx context.Context, db *sql.DB, userID int64, tokenHash string, expiresAt time.Time) error {
	_, err := db.ExecContext(ctx,
		"INSERT INTO refresh_tokens (user_id, token_hash, expires_at) VALUES (?, ?, ?)",
		userID, tokenHash, expiresAt)
	return err
}

func GetRefreshTokenByHash(ctx context.Context, db *sql.DB, tokenHash string) (RefreshToken, error) {
	var rt RefreshToken
	err := db.QueryRowContext(ctx,
		"SELECT id, user_id, token_hash, expires_at, revoked_at FROM refresh_tokens WHERE token_hash = ?", tokenHash).
		Scan(&rt.ID, &rt.UserID, &rt.TokenHash, &rt.ExpiresAt, &rt.RevokedAt)
	if errors.Is(err, sql.ErrNoRows) {
		return RefreshToken{}, ErrRefreshTokenNotFound
	}
	if err != nil {
		return RefreshToken{}, err
	}
	return rt, nil
}

func RevokeRefreshToken(ctx context.Context, db *sql.DB, id int64) error {
	_, err := db.ExecContext(ctx,
		"UPDATE refresh_tokens SET revoked_at = CURRENT_TIMESTAMP WHERE id = ? AND revoked_at IS NULL", id)
	return err
}
