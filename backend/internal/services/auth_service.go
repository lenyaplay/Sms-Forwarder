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
	ErrLoginTaken         = storage.ErrLoginTaken
	ErrInvalidCredentials = errors.New("invalid login or password")
	ErrInvalidRefreshToken = errors.New("invalid refresh token")
)

type TokenPair struct {
	AccessToken  string
	RefreshToken string
}

type AuthService struct {
	db              *sql.DB
	jwtSecret       string
	accessTokenTTL  time.Duration
	refreshTokenTTL time.Duration
}

func NewAuthService(db *sql.DB, jwtSecret string, accessTokenTTL, refreshTokenTTL time.Duration) *AuthService {
	return &AuthService{
		db:              db,
		jwtSecret:       jwtSecret,
		accessTokenTTL:  accessTokenTTL,
		refreshTokenTTL: refreshTokenTTL,
	}
}

func (s *AuthService) Register(ctx context.Context, login, password string) (TokenPair, error) {
	hash, err := auth.HashPassword(password)
	if err != nil {
		return TokenPair{}, err
	}

	user, err := storage.CreateUser(ctx, s.db, login, hash)
	if err != nil {
		return TokenPair{}, err
	}

	return s.issueTokenPair(ctx, user.ID)
}

func (s *AuthService) Login(ctx context.Context, login, password string) (TokenPair, error) {
	user, err := storage.GetUserByLogin(ctx, s.db, login)
	if errors.Is(err, storage.ErrUserNotFound) {
		return TokenPair{}, ErrInvalidCredentials
	}
	if err != nil {
		return TokenPair{}, err
	}

	if !auth.CheckPassword(user.PasswordHash, password) {
		return TokenPair{}, ErrInvalidCredentials
	}

	return s.issueTokenPair(ctx, user.ID)
}

func (s *AuthService) Refresh(ctx context.Context, refreshToken string) (TokenPair, error) {
	hash := auth.HashToken(refreshToken)

	rt, err := storage.GetRefreshTokenByHash(ctx, s.db, hash)
	if errors.Is(err, storage.ErrRefreshTokenNotFound) {
		return TokenPair{}, ErrInvalidRefreshToken
	}
	if err != nil {
		return TokenPair{}, err
	}

	if rt.RevokedAt.Valid || time.Now().After(rt.ExpiresAt) {
		return TokenPair{}, ErrInvalidRefreshToken
	}

	if err := storage.RevokeRefreshToken(ctx, s.db, rt.ID); err != nil {
		return TokenPair{}, err
	}

	return s.issueTokenPair(ctx, rt.UserID)
}

func (s *AuthService) Logout(ctx context.Context, refreshToken string) error {
	hash := auth.HashToken(refreshToken)

	rt, err := storage.GetRefreshTokenByHash(ctx, s.db, hash)
	if errors.Is(err, storage.ErrRefreshTokenNotFound) {
		return nil // idempotent
	}
	if err != nil {
		return err
	}

	return storage.RevokeRefreshToken(ctx, s.db, rt.ID)
}

func (s *AuthService) issueTokenPair(ctx context.Context, userID int64) (TokenPair, error) {
	accessToken, err := auth.GenerateAccessToken(userID, s.jwtSecret, s.accessTokenTTL)
	if err != nil {
		return TokenPair{}, err
	}

	refreshToken, err := auth.GenerateRefreshToken()
	if err != nil {
		return TokenPair{}, err
	}

	expiresAt := time.Now().Add(s.refreshTokenTTL)
	if err := storage.SaveRefreshToken(ctx, s.db, userID, auth.HashToken(refreshToken), expiresAt); err != nil {
		return TokenPair{}, err
	}

	return TokenPair{AccessToken: accessToken, RefreshToken: refreshToken}, nil
}
