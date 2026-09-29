ALTER TABLE concert_packages
ADD COLUMN release_channel TEXT NOT NULL DEFAULT 'rehearsal'
CHECK (release_channel IN ('rehearsal', 'production', 'hotfix'));

ALTER TABLE concert_packages ADD COLUMN base_version INTEGER;
ALTER TABLE concert_packages ADD COLUMN change_summary TEXT NOT NULL DEFAULT '';
ALTER TABLE concert_packages ADD COLUMN approved_by TEXT;
ALTER TABLE concert_packages ADD COLUMN approved_at TEXT;

CREATE TABLE emergency_notices (
    id TEXT PRIMARY KEY,
    event_id TEXT NOT NULL REFERENCES concert_events(id) ON DELETE CASCADE,
    severity TEXT NOT NULL CHECK (severity IN ('Warning', 'Critical')),
    message_ko TEXT NOT NULL,
    message_en TEXT NOT NULL,
    created_by TEXT NOT NULL,
    created_at TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'superseded', 'expired')),
    superseded_at TEXT
);

CREATE INDEX emergency_notices_event_status_expiry
ON emergency_notices(event_id, status, expires_at DESC);
