package config

import "os"

type Config struct {
	Port      string
	DBPath    string
	JWTSecret string
}

func Load() Config {
	return Config{
		Port:      getEnv("PORT", "8080"),
		DBPath:    getEnv("DB_PATH", "./data/sms_forwarder.db"),
		JWTSecret: getEnv("JWT_SECRET", "dev-secret-change-me"),
	}
}

func getEnv(key, fallback string) string {
	if v, ok := os.LookupEnv(key); ok && v != "" {
		return v
	}
	return fallback
}
