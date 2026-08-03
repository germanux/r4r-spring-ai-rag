#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
TASK="${1:-all}"

fail() {
  printf '%s\n' "$*" >&2
  exit 3
}

require_file() {
  [[ -f "$1" ]] || fail "Required task artifact is missing: $1"
}

clean_diff_gate() {
  git diff --check
  git diff --cached --check
}

base_gate() {
  "$ROOT/scripts/verify.sh" all
}

require_ingestion_artifacts() {
  require_file src/main/resources/db/migration/V2__knowledge_ingestion.sql
  require_file src/main/java/com/riansares/r4r/ingestion/KnowledgeIngestionService.java
  require_file src/test/java/com/riansares/r4r/ingestion/KnowledgeIngestionServiceTest.java
  require_file src/test/java/com/riansares/r4r/ingestion/KnowledgeIngestionServiceIT.java
}

ingestion_gate() {
  require_ingestion_artifacts
  "$ROOT/scripts/verify.sh" all
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

require_cli_baseline() {
  require_file src/main/java/com/riansares/r4r/ingestion/KnowledgeIngestionCli.java
  require_file src/test/java/com/riansares/r4r/ingestion/KnowledgeIngestionCliTest.java
}

run_cli_tests() {
  local tests="$1"
  clean_diff_gate
  require_cli_baseline
  rm -rf target
  mvn -q -DskipTests compile
  mvn -q "-Dtest=$tests" -DfailIfNoTests=true test
}

cli_baseline_gate() {
  run_cli_tests "KnowledgeIngestionCliTest"
}

cli_contract_gate() {
  require_file src/test/java/com/riansares/r4r/ingestion/KnowledgeIngestionCliContractTest.java
  run_cli_tests "KnowledgeIngestionCliTest,KnowledgeIngestionCliContractTest"
}

cli_lifecycle_gate() {
  require_file src/test/java/com/riansares/r4r/ingestion/KnowledgeIngestionSpringLifecycleTest.java
  run_cli_tests "KnowledgeIngestionCliTest,KnowledgeIngestionSpringLifecycleTest"
}

cli_failure_gate() {
  require_file src/test/java/com/riansares/r4r/ingestion/KnowledgeIngestionFailureClassificationTest.java
  run_cli_tests "KnowledgeIngestionCliTest,KnowledgeIngestionFailureClassificationTest"
}

cli_process_gate() {
  require_file src/test/java/com/riansares/r4r/ingestion/KnowledgeIngestionCliProcessIT.java
  run_cli_tests "KnowledgeIngestionCliTest,KnowledgeIngestionCliProcessIT"
}

cli_final_gate() {
  clean_diff_gate
  require_cli_baseline
  require_file src/test/java/com/riansares/r4r/ingestion/KnowledgeIngestionCliContractTest.java
  require_file src/test/java/com/riansares/r4r/ingestion/KnowledgeIngestionSpringLifecycleTest.java
  require_file src/test/java/com/riansares/r4r/ingestion/KnowledgeIngestionFailureClassificationTest.java
  require_file src/test/java/com/riansares/r4r/ingestion/KnowledgeIngestionCliProcessIT.java
  rm -rf target
  "$ROOT/scripts/verify.sh" all
  mvn -q \
    -Dtest=KnowledgeIngestionCliTest,KnowledgeIngestionCliContractTest,KnowledgeIngestionSpringLifecycleTest,KnowledgeIngestionFailureClassificationTest,KnowledgeIngestionCliProcessIT \
    -DfailIfNoTests=true test
}

case "$TASK" in
  task-01-base) base_gate ;;
  task-02-ingestion) ingestion_gate ;;
  task-03-pgvector) pgvector_gate ;;
  task-04-rag) rag_gate ;;
  task-05-rag-api) rag_api_gate ;;
  task-06-production-ingestion-cli) cli_baseline_gate ;;
  task-06b-cli-contract) cli_contract_gate ;;
  task-06c-spring-lifecycle) cli_lifecycle_gate ;;
  task-06d-failure-classification) cli_failure_gate ;;
  task-06e-child-process) cli_process_gate ;;
  task-06f-ingestion-validation) cli_final_gate ;;
  all)
    require_rag_artifacts
    "$ROOT/scripts/verify.sh" all
    ;;
  *)
    echo "Usage: $0 {task-01-base|task-02-ingestion|task-03-pgvector|task-04-rag|task-05-rag-api|task-06-production-ingestion-cli|task-06b-cli-contract|task-06c-spring-lifecycle|task-06d-failure-classification|task-06e-child-process|task-06f-ingestion-validation|all}" >&2
    exit 2
    ;;
esac
