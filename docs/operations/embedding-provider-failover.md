# Ollama embedding failover: laptop → desktop → NAS

## Scope and reconstruction

Implementation reconstructed directly on `main` after the original uncommitted
worktree under temporary storage disappeared on reboot. The session history retained
design decisions and previous results, not a recoverable file patch. This document
and the Java tests are retained in the main repository; current verification below
is distinct from the earlier session's results.

One Spring AI `EmbeddingModel` serves document ingestion and retrieval. It retains
the `ollamaEmbeddingModel` bean name and Ollama type, with `ObservedEmbeddingModel`
measuring one operation, including compatibility probes and failed attempts.
Generation failover, vector-store adapters, citations and API contracts are unchanged.
Every new batch tries **laptop → desktop → NAS**. There is no sticky fallback,
background retry, model download, fake vector or partial-batch success.

## Configuration

| Environment variable | Default |
| --- | --- |
| `RAG_EMBEDDING_LP_BASE_URL` | `http://192.168.1.209:11434` |
| `RAG_EMBEDDING_PC_BASE_URL` | `http://192.168.1.207:11434` |
| `RAG_EMBEDDING_NAS_BASE_URL` | `http://192.168.1.203:11434` |
| `RAG_EMBEDDING_MODEL` | `nomic-embed-text:latest` |
| `RAG_EMBEDDING_DIMENSIONS` | `768` |
| `RAG_EMBEDDING_CONNECT_TIMEOUT` | `1s` |
| `RAG_EMBEDDING_READ_TIMEOUT` | `8s` (laptop) |
| `RAG_EMBEDDING_PC_READ_TIMEOUT` | laptop read timeout |
| `RAG_EMBEDDING_NAS_READ_TIMEOUT` | `30s`, or legacy read timeout override |
| `RAG_EMBEDDING_PROBE_TIMEOUT` | `1s` |
| `RAG_EMBEDDING_TOTAL_TIMEOUT` | `35s` |
| `RAG_EMBEDDING_EXPECTED_DIGEST` | see below |

Pinned SHA-256 from the existing provider benchmark:
`0a109f422b47e3a30ba2b10eca18548e944e8a23073ee3f3e947efcf3c45e59f`.
Each candidate must advertise that exact model digest via `/api/tags`; this session
did not repeat physical-provider inventory. All returned vectors must have 768 finite
components, be nonzero, and match the batch size, order and model.
Changing the pin requires checking compatibility with the stored corpus, not merely
that all three servers agree. Do not reindex or download models automatically.

**Legacy compatibility:** `RAG_EMBEDDING_BASE_URL` remains an alias for the primary
endpoint. If an existing environment sets it to the NAS, it overrides the laptop
default. Set `RAG_EMBEDDING_LP_BASE_URL` explicitly to guarantee the requested order.
For the NAS Docker network, set only the NAS endpoint to
`http://ollama-embeddings:11434` where that service name is reachable.
No `.env` was read or modified by this task.

## Errors, cancellation and timing

Fallback is limited to HTTP 408/429/500/502/503/504 and connection/socket/read-timeout
or EOF failures. Authentication, invalid requests, missing models, digest mismatches,
TLS configuration, invalid vectors and cancellation do not advance to another model.
If all providers fail technically, `EmbeddingUnavailableException` reports HTTP 503
through traditional MVC handling. SSE retains its safe existing `ERROR` event.

Each probe and embedding call uses Spring AI's Ollama client with bounded timeouts;
there are no retries within a provider. The total embedding deadline is capped by
the remaining SSE request budget. The existing synchronous request scope is restored
in `finally`; it is never transferred between threads. Blocking socket I/O remains
bounded by its timeout; disconnect cancellation is checked between operations.
Socket read timeout is an inactivity timeout, not a strict byte-stream wall clock.

Logs contain a batch UUID, logical provider, model, outcome, attempt milliseconds,
total milliseconds and fallback transitions. They do not contain input text, vectors,
endpoint addresses or full exception messages. SSE `embeddingMs` covers the whole
embedding operation; it does not trigger a second call to measure anything.

Warmup/runtime still observe the configured primary embedding endpoint; this change
does not add three-provider warmup or change frontend runtime presentation.

## S3 integration boundary

No S3 classes, `KnowledgeVectorStore`, pgvector→S3 router or dependencies are modified.
Both vector adapters should consume the same shared embedding bean. A vector-store
failure classifier must exclude `EmbeddingUnavailableException` and
`EmbeddingConfigurationException` **before** traversing their transport causes:
an embedding outage is not a PostgreSQL outage. Possible manual merge overlap is
limited to the embedding YAML block, `RagProgress` helpers and shared test assertions.

## Verification

HTTP fixtures use real Spring AI clients against JDK loopback servers, not live LLMs.
Coverage includes ordered failover, recovery next request, technical HTTP failures,
connection refusal, timeouts, invalid configuration/model/digest/vectors, cancellation,
SSE deadline/scope cleanup, one measured embedding operation, log redaction/timing,
and shared ingestion/query bean wiring. Existing generation, RAG/citations and SSE
regressions are executed without changing expectations.

Current execution results are recorded below after completion. No physical Ollama,
paid OpenAI, production-corpus end-to-end or S3 network call is part of this gate.

### Reconstructed implementation: verified results

- `mvn verify` with the flags below: **PASSED** (compilation and executable JAR).
- Complete unit suite: **212 PASSED**, 0 failures, 0 errors, 0 skipped.
- Within that suite: `EmbeddingProviderFailoverTest`: **19 PASSED**;
  `OllamaServerSeparationTest`: **6 PASSED**.
- Selected HTTP integration class `RagStreamHttpIT`: **5 PASSED**, 0 skipped.
  It covers actual HTTP/CORS/SSE while retrieval and generation are simulated.
- `git diff --check` and staged whitespace check: **PASSED**.
- Physical Ollama compatibility checks and production-corpus RAG: **NOT RUN**.
- Paid OpenAI and AWS/S3 calls: **NOT RUN**, deliberately outside scope.
- Initial sandbox run could not bind loopback sockets or attach Mockito;
  rerun with those permissions succeeded. Historical session counts are not used
  as evidence for this reconstruction.

Reproduce from the repository root (Spring imports its normal local configuration;
the agent does not inspect it):

```sh
mvn -q \
  -Dspring.flyway.enabled=false \
  -Dspring.sql.init.mode=never \
  -Dspring.ai.vectorstore.pgvector.initialize-schema=false \
  -Dspring.datasource.hikari.read-only=true \
  '-Dspring.datasource.hikari.connection-init-sql=SET default_transaction_read_only=on' \
  -Dit.test=RagStreamHttpIT verify
```

The test profile retains the test-database guard. No ingestion or migration IT is
selected. PostgreSQL schema validation reads metadata; it does not initialize it.
Logs are local ignored artifacts under `target/embedding-failover/`, with JUnit XML
in `target/surefire-reports/` and the selected class in `target/failsafe-reports/`.
Other pre-existing failsafe reports are not counted as executions of this task.

### Files reconstructed or adapted

New:
- `src/main/java/com/riansares/r4r/config/EmbeddingFailoverProperties.java`
- `src/main/java/com/riansares/r4r/rag/EmbeddingProviderRouter.java`
- `src/main/java/com/riansares/r4r/rag/EmbeddingUnavailableException.java`
- `src/main/java/com/riansares/r4r/rag/EmbeddingConfigurationException.java`
- `src/test/java/com/riansares/r4r/rag/EmbeddingProviderFailoverTest.java`
- `docs/operations/embedding-provider-failover.md`

Modified:
- `src/main/java/com/riansares/r4r/config/OllamaEmbeddingConfiguration.java`
- `src/main/java/com/riansares/r4r/rag/ObservedEmbeddingModel.java`
- `src/main/java/com/riansares/r4r/rag/RagProgress.java`
- `src/main/resources/application.yml` (embedding configuration only)
- `src/test/java/com/riansares/r4r/config/OllamaServerSeparationTest.java`
- `src/test/java/com/riansares/r4r/rag/api/RagStreamHttpIT.java`

The SSE test now verifies the actual configured Top-K/minimum score instead of the
obsolete hardcoded Top-K of 3. Production retrieval remains Top-K 5 and score 0.63.
No new worktree, Git commit, merge, push or deployment was created.
