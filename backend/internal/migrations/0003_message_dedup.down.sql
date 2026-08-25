DROP INDEX idx_messages_device_body_hash;

ALTER TABLE messages DROP COLUMN body_hash;
