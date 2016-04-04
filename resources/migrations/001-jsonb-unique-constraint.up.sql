ALTER TABLE event_log DROP CONSTRAINT IF EXISTS event_log_payload_key;
CREATE UNIQUE INDEX unique_payload ON event_log (md5(payload::text));
