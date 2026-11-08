# Ollama embedding failover: laptop → desktop → NAS

## Configuration

Public values live in versioned `docker-spring/.env.base`; optional private `.env`
contains only secrets and overrides, and has priority. The canonical endpoint list is:

```dotenv
RAG_EMBEDDING_PROVIDERS=http://192.168.1.209:11434,http://192.168.1.207:11434,http://192.168.1.203:11434
```

`PublicRagEnvironment` adapts that list to the existing typed providers. Each new
batch starts with laptop, then desktop, then NAS. One or two entries are allowed
for temporary overrides. Model/dimensions remain `nomic-embed-text:latest` and 768.
Read timeouts are `RAG_EMBEDDING_LP_TIMEOUT=8s`, `RAG_EMBEDDING_PC_TIMEOUT=8s`,
`RAG_EMBEDDING_NAS_TIMEOUT=30s`; connection/probe limits are 1s and the total is 35s.
The public file also pins the expected digest. Warmup/runtime use the first entry.

Individual URL aliases and the previous indexed-YAML configuration are superseded
by the compact list. For Docker service DNS, use `http://ollama-embeddings:11434` as
the third URL in an intentional override. No secret file was inspected or migrated.
See [public/private configuration](public-private-configuration.md) for precedence,
Compose interpolation, legacy variable cleanup and safe validation.

## Compatibility and routing

One Spring AI `EmbeddingModel` serves ingestion and retrieval, preserving the
`ollamaEmbeddingModel` name and Ollama type. `ObservedEmbeddingModel` measures one
operation, including probes and failed attempts. The router consumes the configured
provider records directly; there is no duplicate internal target definition.
The configuration record, routing class and two typed exceptions remain separate
because they represent binding, execution and distinct failure classifications.

Every candidate must advertise the configured nomic model and exact pinned SHA-256
via `/api/tags`. The pin above comes from the existing provider benchmark; physical
provider inventory has not been repeated for this change. Responses must contain
one ordered, nonzero, finite 768-dimensional vector per input and the expected model.
Changing the pin requires checking compatibility with the stored corpus, not merely
that the servers agree. Models are never downloaded or the corpus reindexed automatically.

Each batch starts at the first provider, including after fallback on a previous
request. No sticky routing, background retries, partial batches or fake vectors.
Technical HTTP 408/429/500/502/503/504, socket/connection/read-timeout and EOF failures
advance to the next entry. Configuration/authentication/parameter/model/digest/vector
errors and cancellation stop immediately. TLS configuration errors do not trigger
fallback. Exhausting technical attempts gives `EmbeddingUnavailableException`
(HTTP 503 in traditional MVC); SSE retains its existing safe `ERROR` event.

Spring AI's Ollama client performs the requests, once per provider. Each operation's
timeouts are clipped to the remaining embedding/SSE budget; cancellation is checked
between operations. Blocking socket I/O remains bounded by its timeout; a socket
read timeout is an inactivity limit, not a strict byte-stream wall-clock deadline.
The existing synchronous SSE scope is restored in `finally`, never passed across threads.

Logs retain batch UUID, provider, model, result, attempt milliseconds, total
milliseconds and fallback transitions, without input, vectors, endpoints or full
exceptions. SSE `embeddingMs` measures the complete embedding operation without
repeating the call. Other RAG metrics, generation, citations and REST/SSE contracts
are unchanged.

## S3 integration boundary

No S3 classes, vector ports, pgvector→S3 router or dependencies are changed.
Both vector adapters should consume the shared embedding bean. A vector-store
failure classifier must exclude `EmbeddingUnavailableException` and
`EmbeddingConfigurationException` before traversing transport causes: an embedding
outage is not a PostgreSQL outage.

## Verification

The original reconstruction passed 212 unit tests and 5 selected HTTP SSE tests.
Those historical counts are distinct from the simplified-configuration results below.
Real Spring AI HTTP clients use simulated loopback Ollama servers. Coverage retains
failover/recovery, compatibility, invalid parameters/responses, deadlines,
cancellation, metrics/logs and shared ingestion/query wiring. Updated tests check
list binding/order, differentiated timeouts and ignored legacy aliases.

Reproduce compilation, packaging, unit tests and the selected HTTP integration class:

```sh
mvn -q \
  -Dspring.flyway.enabled=false \
  -Dspring.sql.init.mode=never \
  -Dspring.ai.vectorstore.pgvector.initialize-schema=false \
  -Dspring.datasource.hikari.read-only=true \
  '-Dspring.datasource.hikari.connection-init-sql=SET default_transaction_read_only=on' \
  -Dit.test=RagStreamHttpIT verify
```

Spring may import normal local configuration without exposing it. The test-database
guard remains active. No migration or ingestion IT is selected. PostgreSQL schema
validation only reads metadata. Logs/reports remain ignored under `target/`.
Physical Ollama and production-corpus RAG, paid OpenAI and AWS calls are **NOT RUN**.
No worktree, commit, merge, push or deployment is part of this change.

### Simplified configuration: verified results

- Complete `mvn verify` command above: **PASSED**, including compilation and packaging.
- Unit suite: **238 PASSED**, 0 failures/errors/skipped.
- Included: **21** embedding failover tests and **6** Spring AI separation/configuration tests.
- Selected `RagStreamHttpIT`: **5 PASSED**, 0 failures/errors/skipped.
- YAML parsing, provider order and removal of the obsolete Compose alias: **PASSED**.
- `git diff --check` and staged whitespace check: **PASSED**.
- Physical providers/production corpus and paid OpenAI/AWS calls: **NOT RUN**.

Modified files: `EmbeddingFailoverProperties`, `OllamaEmbeddingConfiguration`,
`EmbeddingProviderRouter`, `OllamaModelPreloader` (primary endpoint binding only),
`application.yml`, `OllamaServerSeparationTest`, `EmbeddingProviderFailoverTest`,
`docker-spring/docker-compose.yml` (obsolete alias removal only), this document,
`ollama-server-separation.md` and `rag-sse.md`. No files were added or deleted.
