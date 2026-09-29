CREATE TABLE concert_events (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    promoter TEXT NOT NULL,
    venue TEXT NOT NULL,
    show_date TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'published', 'archived')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE concert_packages (
    event_id TEXT NOT NULL REFERENCES concert_events(id) ON DELETE CASCADE,
    version INTEGER NOT NULL CHECK (version > 0),
    status TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'published', 'superseded')),
    payload_json TEXT NOT NULL,
    checksum_sha256 TEXT NOT NULL,
    validation_errors_json TEXT NOT NULL DEFAULT '[]',
    created_at TEXT NOT NULL,
    published_at TEXT,
    PRIMARY KEY (event_id, version)
);

CREATE UNIQUE INDEX one_published_package_per_event
ON concert_packages(event_id)
WHERE status = 'published';

CREATE TABLE tickets (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    provider TEXT NOT NULL,
    provider_ticket_id TEXT NOT NULL,
    event_id TEXT NOT NULL REFERENCES concert_events(id) ON DELETE CASCADE,
    checked_in INTEGER NOT NULL DEFAULT 0 CHECK (checked_in IN (0, 1)),
    external_subject_hash TEXT,
    verified_at TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE (provider, provider_ticket_id, event_id)
);

CREATE TABLE audience_sessions (
    id TEXT PRIMARY KEY,
    event_id TEXT NOT NULL REFERENCES concert_events(id) ON DELETE CASCADE,
    ticket_id INTEGER NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    access_token_hash TEXT NOT NULL UNIQUE,
    created_at TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    revoked_at TEXT
);

CREATE INDEX audience_sessions_active_token
ON audience_sessions(access_token_hash, expires_at)
WHERE revoked_at IS NULL;

CREATE TABLE device_dispatch_logs (
    id TEXT PRIMARY KEY,
    event_id TEXT NOT NULL REFERENCES concert_events(id) ON DELETE CASCADE,
    session_id TEXT NOT NULL REFERENCES audience_sessions(id) ON DELETE CASCADE,
    occurred_at TEXT NOT NULL,
    route TEXT NOT NULL CHECK (route IN ('PrimaryToolkit', 'MockDevice', 'FallbackPreview')),
    accepted INTEGER NOT NULL CHECK (accepted IN (0, 1)),
    renderer_name TEXT NOT NULL,
    availability TEXT NOT NULL CHECK (availability IN ('Ready', 'WaitingForOfficialSdk', 'Unsupported')),
    document_id TEXT NOT NULL,
    priority TEXT NOT NULL CHECK (priority IN ('Low', 'Normal', 'High')),
    error_code TEXT,
    metadata_json TEXT NOT NULL DEFAULT '{}'
);

CREATE INDEX device_dispatch_logs_event_time
ON device_dispatch_logs(event_id, occurred_at DESC);
