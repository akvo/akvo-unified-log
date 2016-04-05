ALTER TABLE event_log DROP CONSTRAINT IF EXISTS event_log_payload_key;

-- Drop duplicates
DELETE FROM event_log
  WHERE id IN (
    SELECT id FROM (SELECT id, row_number() OVER (PARTITION BY payload ORDER BY id) AS rnum FROM event_log) t
      WHERE t.rnum > 1
  );

CREATE UNIQUE INDEX unique_payload ON event_log (md5(payload::text));
