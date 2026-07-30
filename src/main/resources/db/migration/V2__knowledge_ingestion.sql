CREATE TABLE knowledge_sources (
    id BIGSERIAL PRIMARY KEY,
    source_path TEXT NOT NULL UNIQUE,
    content_sha256 BYTEA NOT NULL CHECK (octet_length(content_sha256) = 32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE knowledge_chunks (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL REFERENCES knowledge_sources(id) ON DELETE CASCADE,
    heading_path TEXT[] NOT NULL,
    ordinal INTEGER NOT NULL CHECK (ordinal >= 0),
    content_sha256 BYTEA NOT NULL CHECK (octet_length(content_sha256) = 32),
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(source_id, ordinal)
);

CREATE INDEX idx_knowledge_chunks_source ON knowledge_chunks(source_id);
CREATE INDEX idx_knowledge_sources_path ON knowledge_sources(source_path);
