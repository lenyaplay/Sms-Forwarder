ALTER TABLE messages ADD COLUMN body_hash TEXT NOT NULL DEFAULT '';

CREATE UNIQUE INDEX idx_messages_device_body_hash ON messages (device_id, body_hash);
