DROP INDEX unique_payload;
CREATE UNIQUE INDEX event_log_payload_key ON event_log (md5(payload::text));
