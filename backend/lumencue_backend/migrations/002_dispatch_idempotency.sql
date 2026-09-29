ALTER TABLE device_dispatch_logs ADD COLUMN client_record_id TEXT;

CREATE UNIQUE INDEX device_dispatch_logs_event_client_record
ON device_dispatch_logs(event_id, client_record_id)
WHERE client_record_id IS NOT NULL;
