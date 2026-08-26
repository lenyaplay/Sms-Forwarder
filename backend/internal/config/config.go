package config

import (
	"os"
	"strconv"
	"strings"
	"time"
)

type Config struct {
	Port            string
	DBPath          string
	JWTSecret       string
	AccessTokenTTL  time.Duration
	RefreshTokenTTL time.Duration
	LogLevel        string
	RateLimitRPS    float64
	RateLimitBurst  int
	// TrustedProxyCIDRs lists CIDR ranges allowed to set X-Forwarded-For for
	// client-IP-based rate limiting. Empty means trust nobody and always use
	// the TCP connection's address, so a client can't spoof its way around
	// IP-based limits by forging the header itself.
	TrustedProxyCIDRs []string
}

func Load() Config {
	return Config{
		Port:              getEnv("PORT", "8080"),
		DBPath:            getEnv("DB_PATH", "./data/sms_forwarder.db"),
		JWTSecret:         getEnv("JWT_SECRET", "dev-secret-change-me"),
		AccessTokenTTL:    15 * time.Minute,
		RefreshTokenTTL:   30 * 24 * time.Hour,
		LogLevel:          getEnv("LOG_LEVEL", "info"),
		RateLimitRPS:      getEnvFloat("RATE_LIMIT_RPS", 1),
		RateLimitBurst:    getEnvInt("RATE_LIMIT_BURST", 10),
		TrustedProxyCIDRs: getEnvList("TRUSTED_PROXY_CIDRS"),
	}
}

func getEnvFloat(key string, fallback float64) float64 {
	if v, ok := os.LookupEnv(key); ok && v != "" {
		if f, err := strconv.ParseFloat(v, 64); err == nil {
			return f
		}
	}
	return fallback
}

func getEnvInt(key string, fallback int) int {
	if v, ok := os.LookupEnv(key); ok && v != "" {
		if i, err := strconv.Atoi(v); err == nil {
			return i
		}
	}
	return fallback
}

func getEnvList(key string) []string {
	v, ok := os.LookupEnv(key)
	if !ok || v == "" {
		return nil
	}
	parts := strings.Split(v, ",")
	result := make([]string, 0, len(parts))
	for _, p := range parts {
		if p = strings.TrimSpace(p); p != "" {
			result = append(result, p)
		}
	}
	return result
}

func getEnv(key, fallback string) string {
	if v, ok := os.LookupEnv(key); ok && v != "" {
		return v
	}
	return fallback
}
