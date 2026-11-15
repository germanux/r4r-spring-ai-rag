-- Aggregate usage only: deliberately no question, answer, IP or client identifier.
CREATE TABLE assistant_usage (
    id UUID PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    origin VARCHAR(16) NOT NULL CHECK (origin IN ('ATHAGON', 'RIANSARES4R', 'UNKNOWN')),
    event_type VARCHAR(8) NOT NULL CHECK (event_type IN ('VIEW', 'QUERY')),
    outcome VARCHAR(10) CHECK (outcome IN ('ANSWERED', 'PARTIAL', 'ABSTAINED', 'ERROR')),
    duration_ms BIGINT CHECK (duration_ms >= 0)
);
CREATE INDEX assistant_usage_time_origin ON assistant_usage (occurred_at, origin);
