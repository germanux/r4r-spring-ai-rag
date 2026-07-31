CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE vector_store (
    id UUID PRIMARY KEY,
    content TEXT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    embedding VECTOR(768) NOT NULL
);

CREATE INDEX idx_vector_store_embedding
    ON vector_store
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 100);

CREATE INDEX idx_vector_store_source
    ON vector_store ((metadata->>'source'));
