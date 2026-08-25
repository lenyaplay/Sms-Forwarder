ALTER TABLE devices ADD COLUMN upload_token_expires_at TIMESTAMP;

CREATE TABLE device_download_tokens (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id  INTEGER NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    token      TEXT NOT NULL UNIQUE,
    label      TEXT,
    expires_at TIMESTAMP,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_device_download_tokens_device_id ON device_download_tokens (device_id);

ALTER TABLE viewer_bindings ADD COLUMN download_token_id INTEGER REFERENCES device_download_tokens (id) ON DELETE CASCADE;

DROP INDEX IF EXISTS idx_viewer_bindings_user_id;
ALTER TABLE viewer_bindings DROP COLUMN download_token;

CREATE INDEX idx_viewer_bindings_user_id ON viewer_bindings (user_id);
CREATE INDEX idx_viewer_bindings_download_token_id ON viewer_bindings (download_token_id);
