package config

import (
	"os"
	"time"
)

type Config struct {
	Port            string
	DBPath          string
	JWTSecret       string
	AccessTokenTTL  time.Duration
	RefreshTokenTTL time.Duration
	LogLevel        string
}

func Load() Config {
	return Config{
		Port:            getEnv("PORT", "8080"),
		DBPath:          getEnv("DB_PATH", "./data/sms_forwarder.db"),
		JWTSecret:       getEnv("JWT_SECRET", "dev-secret-change-me"),
		AccessTokenTTL:  15 * time.Minute,
		RefreshTokenTTL: 30 * 24 * time.Hour,
		LogLevel:        getEnv("LOG_LEVEL", "info"),
	}
}

func getEnv(key, fallback string) string {
	if v, ok := os.LookupEnv(key); ok && v != "" {
		return v
	}
	return fallback
}
