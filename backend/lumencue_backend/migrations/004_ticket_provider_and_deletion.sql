ALTER TABLE tickets ADD COLUMN provider_updated_at TEXT;

CREATE TABLE ticket_provider_events (
    provider TEXT NOT NULL,
    provider_event_id TEXT NOT NULL,
    event_type TEXT NOT NULL CHECK (event_type IN ('ticket.checked_in', 'ticket.revoked')),
    payload_sha256 TEXT NOT NULL,
    occurred_at TEXT NOT NULL,
    received_at TEXT NOT NULL,
    PRIMARY KEY (provider, provider_event_id)
);

CREATE INDEX ticket_provider_events_received_at
ON ticket_provider_events(received_at);

CREATE TABLE data_deletion_receipts (
    id TEXT PRIMARY KEY,
    requested_at TEXT NOT NULL,
    completed_at TEXT NOT NULL,
    subject_scope TEXT NOT NULL CHECK (subject_scope IN ('ticket', 'subject')),
    tickets_deleted INTEGER NOT NULL,
    sessions_deleted INTEGER NOT NULL,
    dispatch_logs_deleted INTEGER NOT NULL
);

CREATE INDEX data_deletion_receipts_completed_at
ON data_deletion_receipts(completed_at);
