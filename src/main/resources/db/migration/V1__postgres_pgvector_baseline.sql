CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS r4r_schema_marker (
    id INTEGER PRIMARY KEY,
    description TEXT NOT NULL,
    installed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO r4r_schema_marker (id, description)
VALUES (1, 'R4R Spring AI RAG baseline')
ON CONFLICT (id) DO NOTHING;
