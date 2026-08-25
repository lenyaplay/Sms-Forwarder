CREATE TABLE users (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    login         TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE devices (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_user_id INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name          TEXT NOT NULL,
    upload_token  TEXT NOT NULL UNIQUE,
    hmac_secret   TEXT,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE viewer_bindings (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id      INTEGER NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    user_id        INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    download_token TEXT NOT NULL UNIQUE,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (device_id, user_id)
);

CREATE TABLE messages (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id      INTEGER NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    sender         TEXT NOT NULL,
    text           TEXT NOT NULL,
    sent_stamp     TEXT,
    received_stamp TEXT,
    sim            TEXT,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE refresh_tokens (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id    INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_devices_owner_user_id ON devices (owner_user_id);
CREATE INDEX idx_viewer_bindings_user_id ON viewer_bindings (user_id);
CREATE INDEX idx_messages_device_id ON messages (device_id);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
