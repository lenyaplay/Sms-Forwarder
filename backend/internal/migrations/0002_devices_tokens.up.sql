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

-- viewer_bindings.download_token is column-level UNIQUE, so plain
-- ALTER TABLE ... DROP COLUMN is rejected by SQLite. Rebuild the table instead.
CREATE TABLE viewer_bindings_new (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id          INTEGER NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    user_id            INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    download_token_id  INTEGER REFERENCES device_download_tokens (id) ON DELETE CASCADE,
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (device_id, user_id)
);

INSERT INTO viewer_bindings_new (id, device_id, user_id, created_at)
SELECT id, device_id, user_id, created_at FROM viewer_bindings;

DROP TABLE viewer_bindings;
ALTER TABLE viewer_bindings_new RENAME TO viewer_bindings;

CREATE INDEX idx_viewer_bindings_user_id ON viewer_bindings (user_id);
CREATE INDEX idx_viewer_bindings_download_token_id ON viewer_bindings (download_token_id);
