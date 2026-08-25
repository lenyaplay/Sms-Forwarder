-- download_token_id is named by idx_viewer_bindings_download_token_id, so it
-- must be dropped via rebuild too (SQLite forbids DROP COLUMN on an indexed
-- column, and ADD COLUMN cannot restore a UNIQUE constraint directly).
CREATE TABLE viewer_bindings_old (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id      INTEGER NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    user_id        INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    download_token TEXT UNIQUE,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (device_id, user_id)
);

INSERT INTO viewer_bindings_old (id, device_id, user_id, created_at)
SELECT id, device_id, user_id, created_at FROM viewer_bindings;

DROP TABLE viewer_bindings;
ALTER TABLE viewer_bindings_old RENAME TO viewer_bindings;

CREATE INDEX idx_viewer_bindings_user_id ON viewer_bindings (user_id);

DROP INDEX IF EXISTS idx_device_download_tokens_device_id;
DROP TABLE IF EXISTS device_download_tokens;

ALTER TABLE devices DROP COLUMN upload_token_expires_at;
