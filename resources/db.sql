-- name: select-all
SELECT * FROM event_log

-- name: last-timestamp
SELECT MAX((payload->'context'->>'timestamp')::numeric)
       AS timestamp
       FROM event_log

-- name: insert<!
INSERT INTO event_log ( payload ) VALUES ( :payload )
