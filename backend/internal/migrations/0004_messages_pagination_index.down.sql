DROP INDEX idx_messages_device_id_id;
CREATE INDEX idx_messages_device_id ON messages (device_id);
