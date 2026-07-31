#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
TASK="${1:-all}"

require_file() {
  [[ -f "$1" ]] || { echo "Required task artifact is missing: $1" >&2; exit 3; }
}

base_gate() {
  "$ROOT/scripts/verify.sh" all
}

ingestion_gate() {
  require_ingestion_artifacts
  "$ROOT/scripts/verify.sh" all
}

require_ingestion_artifacts() {
  require_file src/main/resources/db/migration/V2__knowledge_ingestion.sql
  require_file src/main/java/com/riansares/r4r/ingestion/KnowledgeIngestionService.java
  require_file src/test/java/com/riansares/r4r/ingestion/KnowledgeIngestionServiceTest.java
  require_file src/test/java/com/riansares/r4r/ingestion/KnowledgeIngestionServiceIT.java
}

require_pgvector_artifacts() {
  require_ingestion_artifacts
  require_file src/main/resources/db/migration/V3__pgvector_store.sql
  require_file src/main/java/com/riansares/r4r/vector/PgVectorKnowledgeStore.java
  require_file src/test/java/com/riansares/r4r/vector/PgVectorKnowledgeStoreIT.java
}

require_rag_artifacts() {
  require_pgvector_artifacts
  require_file src/main/java/com/riansares/r4r/rag/CitedRagService.java
  require_file src/test/java/com/riansares/r4r/rag/CitedRagServiceTest.java
}

pgvector_gate() {
  require_pgvector_artifacts
  "$ROOT/scripts/verify.sh" all
}

rag_gate() {
  require_rag_artifacts
  "$ROOT/scripts/verify.sh" all
}

rag_api_gate() {
  require_rag_artifacts
  require_file src/main/java/com/riansares/r4r/rag/api/RagQueryController.java
  require_file src/test/java/com/riansares/r4r/rag/api/RagQueryControllerTest.java
  "$ROOT/scripts/verify.sh" all
}

case "$TASK" in
  task-01-base) base_gate ;;
  task-02-ingestion) ingestion_gate ;;
  task-03-pgvector) pgvector_gate ;;
  task-04-rag) rag_gate ;;
  task-05-rag-api) rag_api_gate ;;
  all)
    require_rag_artifacts
    "$ROOT/scripts/verify.sh" all
    ;;
  *)
    echo "Usage: $0 {task-01-base|task-02-ingestion|task-03-pgvector|task-04-rag|task-05-rag-api|all}" >&2
    exit 2
    ;;
esac
