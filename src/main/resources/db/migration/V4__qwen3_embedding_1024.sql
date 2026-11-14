-- Explicit cutover only: verify backups, stop RAG readers/writers, then clear old vectors.
-- Never pad/truncate/reuse Nomic embeddings. Sources/chunks are preserved until full ingestion.
DO $$
DECLARE current_type text;
BEGIN
    SELECT format_type(atttypid, atttypmod) INTO current_type
    FROM pg_attribute WHERE attrelid = 'vector_store'::regclass AND attname = 'embedding';
    IF current_type = 'vector(1024)' THEN RETURN; END IF;
    IF current_type <> 'vector(768)' THEN
        RAISE EXCEPTION 'Unexpected embedding column type: %', current_type;
    END IF;
    IF EXISTS (SELECT 1 FROM vector_store) THEN
        RAISE EXCEPTION 'Qwen3 migration requires an empty vector_store. Verify backups, stop the API, explicitly DELETE old vectors, then migrate and fully reingest.';
    END IF;
    DROP INDEX IF EXISTS idx_vector_store_embedding;
    ALTER TABLE vector_store ALTER COLUMN embedding TYPE vector(1024);
    CREATE INDEX idx_vector_store_embedding ON vector_store
        USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 100);
END $$;
