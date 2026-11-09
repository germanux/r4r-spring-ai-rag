# PostgreSQL → S3 Vectors: startup, outage and recovery

## Root cause and current scope

The query router already handled JDBC failures, but normal startup still eagerly ran
Flyway and initialized Spring AI's pgvector store. A missing Docker service hostname
(`UnknownHostException: postgres`, normally wrapped in SQLSTATE `08001`) therefore
stopped the application before any query could reach the router. Lazy initialization
of only the port adapter was insufficient: its underlying `vectorStore` was eager.
The old `s3-readonly` profile avoided startup access but required manual selection
and did not restore PostgreSQL automatically.

On this branch Compose is already self-contained and **has no API dependency on a
healthy PostgreSQL service**. That part of the reported configuration is not present
in the current file; it is preserved and checked by the offline Compose regression.
No live deployment, AWS index, PostgreSQL server or Docker network was inspected or
changed during this implementation.

## Modes and configuration

| Primary | Failover enabled | S3 enabled | Behavior |
|---|---|---|---|
| pgvector | false | false/true | Traditional PostgreSQL startup and reads; no S3 fallback. |
| pgvector | true | true | Automatic degraded startup, read fallback and bounded PG recovery. |
| s3vectors | false | true | Direct S3 reads; writes remain PG unless `s3-readonly` rejects them. |
| Invalid combinations | | | Startup configuration error. |

The existing variables remain `RAG_VECTOR_PRIMARY`, `RAG_VECTOR_FAILOVER_ENABLED`
and `RAG_S3_VECTORS_ENABLED`. Public defaults remain `pgvector/false/false`. To enable
automatic recovery, the owner selects `pgvector/true/true` using the existing settings.
This task does not open or modify the private `.env`.

Additional public tuning, effective only in automatic failover mode:

| Variable | Spring property | Default | Purpose |
|---|---|---|---|
| `RAG_PG_RECOVERY_INTERVAL` | `r4r.rag.vector.postgres.recovery-interval` | 30s | Shared cooldown before a new readiness attempt. |
| `RAG_PG_CONNECTION_TIMEOUT` | `r4r.rag.vector.postgres.connection-timeout` | 3s | Hikari acquisition limit and default JDBC connection timeout. |
| `RAG_PG_SOCKET_TIMEOUT` | `r4r.rag.vector.postgres.socket-timeout` | 5s | Default PostgreSQL JDBC socket read timeout. |

The enabled-mode pool has minimum idle zero and initialization fail timeout -1, so
pool creation does not require a successful initial connection. Validation waits are
bounded. Explicit JDBC `connectTimeout`/`socketTimeout` data-source properties are
retained; choose them consistently with the request budget. Durations are validated:
recovery 1s–5m, acquisition 500ms–30s, socket 1s–60s. Hikari/JDBC convert subsecond
connection limits appropriately; the driver timeout has whole-second granularity.

AWS SDK configuration, standard credential chain and region are unchanged:
`eu-north-1`, bucket `amzn-r4r-vectors`, index `amzn-r4r-vector-index`, 768 dimensions,
cosine. No AWS_PROFILE, credential-file parsing or new authentication mechanism was
introduced. S3 is optional when disabled and strictly read-only when enabled.

## Startup and Flyway safety

Only when both failover and S3 are enabled:

1. Spring defers the PG `vectorStore`; the PG adapter stays lazy.
2. `PostgresAccessGate` probes the configured DataSource before migrations.
3. If reachable, normal Flyway migration and validation run. `validateOnMigrate`
   is mandatory in this mode, even if a conflicting native property requests false;
   this validates existing history before applying pending migrations. Explicit
   validation after migration gates subsequent application writes.
4. Proven connectivity failures leave PostgreSQL unready but allow MVC/API startup.
   The application serves reads from S3 until recovery. AWS is not queried merely
   to start the process; if AWS also fails, queries fail safely while HTTP remains up.
5. Authentication, configuration, SQL/schema/integrity and migration validation
   errors remain errors. They are not relabeled as a network outage. Typed Flyway
   validation/checksum/failed-migration errors veto even a connection error nested
   in their diagnostic chain. Startup fails for these errors; the service does not
   silently continue with a damaged migration history.

Flyway is **not permanently disabled**. Connection retries inside Flyway are zero;
readiness retries belong to the gate. SQL script initialization is `never`, because
Flyway owns the schema; all existing migration files are unchanged. Normal mode
without AWS failover retains traditional startup requirements. The explicit
`s3-readonly` profile also remains available: it deliberately disables migrations,
selects S3 directly and rejects writes, without automatic return to PG.

If Flyway is explicitly disabled in automatic failover mode, reads may use an
existing schema but application writes are refused: no unvalidated write is allowed.

## Queries, recovery and transactions

`FailoverKnowledgeVectorStore` remains the primary implementation of the existing
`KnowledgeVectorStore` port. There is no parallel router, generic JDBC interceptor,
new scheduler, unbounded queue or background polling subsystem.

- Healthy requests use PostgreSQL; empty successful results do not query AWS.
- An infrastructure failure marks PG unavailable and attempts S3. During cooldown,
  subsequent reads go to S3 without resolving/recreating the PG adapter.
- The first request after cooldown performs one readiness attempt. A nonblocking
  lock prevents duplicate migration/recovery jobs: concurrent requests use S3 instead
  of waiting behind that attempt. Failed requests do not extend cooldown indefinitely.
- When PG returns, Flyway migrates/validates before PG is selected. The lazy Spring AI
  store performs its normal schema validation on initialization. The next successful
  lookup uses PG and logs recovery, without restarting Spring.
- `index()`/`replaceSource()` never use S3. An infrastructure advisor guards the
  existing PG adapter and `KnowledgeIngestionService.ingest()` **before transaction
  acquisition**, preventing migration inside an already-open ingestion transaction.
  Transactional PostgreSQL behavior is retained.
- A permanent migration error discovered during recovery blocks further PG access
  and surfaces as a controlled error; it requires operator correction, rather than
  automatic repair, schema recreation or silent continued fallback.

A recovery request can spend the configured acquisition/read time and the duration
of legitimate migrations. Other requests do not queue behind it. Normal Flyway DB
locking still applies between independent application processes; this is a single-API
MVP, not a distributed migration coordinator. Long future migrations may need an
explicit maintenance window and revised JDBC budgets. No claim of one hard wall-clock
limit across every migration statement is made. Existing SSE cancellation/deadline
handling remains in place.

## Classification, metadata and diagnostics

Only failures arising from PG access are candidates. Positive JDBC evidence is
required: `CannotGetJdbcConnectionException`, connection/pool exceptions, infrastructure
SQL timeouts, SQLSTATE `08xxx`, `57P01`, `57P02`, `57P03`, `53300`, or a JDBC-wrapped
transport failure (including Docker hostname resolution). Bare Ollama/DNS exceptions
are not sufficient. Nested bean/Flyway wrappers do not hide genuine JDBC evidence;
embedding/configuration/authentication and nonrecoverable SQL errors still veto it.
Cancellation/interruption never triggers vector fallback.

Logs contain provider/result/duration, bounded failure reason, cooldown, recovery and
combined unavailability; never question text, document text, credentials or raw provider
error bodies. Both-provider failure is a safe REST 503 with retained diagnostic causes;
SSE emits ERROR without ANSWER_READY. Runtime does not query the vector port and
remains available. No new public endpoint or frontend change is needed.

S3 failures additionally log `stage` (`embedding`, `embedding_validation`, `aws_query`
or `response_validation`), exception/root types, HTTP status, an allowlisted AWS error
code and the configured region/bucket/index. Bounded reasons distinguish missing SDK
credentials, timeout, DNS, connection, TLS, service errors and invalid responses.
Unknown AWS error codes become `other`; SDK exception messages, request IDs, response
bodies and stack traces are never emitted. Cancellation is not logged as an outage.
The original exception is rethrown, preserving routing and HTTP/SSE behavior.

### Owner-provided Synology outage export, 2026-11-09

The HTML export shows successful startup/migrations and a normal PG answer before
the owner stopped PostgreSQL. At 18:53:16 CET, SQLSTATE `08003` triggers the expected
PG-to-S3 fallback. The secondary embedding succeeds in about 130 ms, then S3 retrieval
fails after about 5.75 s; the request ends with `both_unavailable`. This proves that
fallback was attempted. The old logs contain no secondary exception type, status or
error code, so they do not establish missing credentials, IAM denial, network timeout
or invalid metadata as the cause. The duration alone is insufficient to choose one.

The structured S3 diagnostic above addresses this evidence gap. Its next occurrence
in the deployed API can identify the failing stage and error category. It is not a
confirmed repair of the deployed S3 failure: no Synology access, credentials, AWS/IAM
changes or live calls were performed. Keep the original owner export outside commits.

The existing S3 mapper reconstructs documentary text from `source_text`, source from
`source`, heading path and ordinal from metadata; vector keys are validated/deduplicated.
A key-only AWS result is not enough to generate a cited answer. Missing/invalid metadata
fails rather than fabricating context. Top-K, cosine threshold and citation processing
are unchanged. `returnMetadata=true` is explicitly verified by the tests. Existing
heading-context validation remains in force.

Embeddings and generation keep their independent routers: compatible nomic 768 vectors
and PC → laptop → OpenAI generation. Retrieval timings include actual attempts;
`vectorSearchMs` remains null when not independently observed. PG failure after its
embedding may cause a second embedding for S3, as before; no extra operation is added
just for measurement. S3 SDK requests keep existing bounded timeouts and maxAttempts=1.
The snapshot remains manual and can become stale after corpus updates.

## Reproducible controlled tests

No credentials, AWS/PostgreSQL/Ollama servers or stopped production containers are
needed. The safe local gate uses public configuration copies, a synthetic secret and
a JDBC driver that refuses external database connections. Dedicated startup fixtures
provide their own failing simulated DataSource; full HTTP tests use real MVC/Tomcat,
real Flyway initialization and the real router/S3 adapter with simulated SDK responses
and embeddings/generation.

```bash
python3 py-scripts/test_secret_isolation.py
python3 py-scripts/test_compose_configuration.py
node --test front-athagon-r4r-static/js/app.test.cjs
```

The Maven gate runs all unit tests and explicitly selected local HTTP/CLI ITs
`RagStreamHttpIT,VectorDegradedHttpIT,KnowledgeIngestionCliProcessIT`, with synthetic
parent environment flags enabling both vector failover and S3. It does **not** claim
execution of the existing external database/live ITs. Focused coordinator tests use a controllable monotonic clock
and latches; no waits of 30 seconds, forced model unloads or production outages.

| Required case | Automated evidence |
|---|---|
| 1. PG/AWS available → PG | Gate/router unit tests; no AWS calls for healthy/empty PG results. |
| 2. PG fails during query → S3 | Router and warm-outage tests preserve metadata/arguments. |
| 3. PG unavailable at startup | Real Flyway/Spring AI auto-config with SQL/DNS failure; actual HTTP starts and serves cited S3 responses. |
| 4. PG recovery | Same gate/router instance with clock advancement, migration/validation before PG reuse. |
| 5. Both unavailable | REST 503, SSE ERROR/no answer, no generation call. |
| 6. AWS disabled | Traditional fatal PG startup retained; existing PG-only wiring/transactions pass. |
| 7. S3 metadata/citations | Real S3 mapper and cited service through REST/SSE with SDK fixtures. |
| 8. Models/dimensions | 768-float S3 request, configured nomic metadata, original generation model, existing provider tests. |
| 9. Bounded attempts | Concurrent requests, cooldown, single migration, timeout validation and bounded pool tests. |
| 10. Independent HTTP/runtime | Runtime returns 200 after combined storage failure. |

### Verified results for this implementation

- **331 current-source unit tests PASS**, no failures/errors/skips. This includes
  coordinator/recovery, typed migration vetoes, concurrency, transaction ordering,
  commit/rollback, S3 diagnostic/redaction scenarios, the existing vector, embedding,
  generation, security and API suites.
- **11 local HTTP/CLI IT PASS**: 5 existing streaming/security tests, 3 degraded
  startup tests and 3 child-process CLI tests. Full Spring/Tomcat/Flyway initialization
  and actual child JVM exit codes are exercised; database, AWS, embeddings and
  generation are simulated. These are not live cloud tests.
- **81 frontend tests PASS**, with no frontend changes.
- **1 synthetic-secret local gate PASS**, including the Maven executions above and
  fresh-report checks; no marker in output or reports.
- **1 offline Compose test PASS**, using only the current standalone YAML and copied
  public defaults plus synthetic overrides. API has no PostgreSQL/Ollama dependency,
  timeout forwarding and restart policy are checked, no service is started.
- Java 21 compilation and executable **0.3.3** packaging PASS; whitespace checks PASS.

The selected Maven verify is
`-Dit.test=RagStreamHttpIT,VectorDegradedHttpIT,KnowledgeIngestionCliProcessIT` inside
`test_secret_isolation.py`. Existing real PostgreSQL/ingestion and live Ollama/OpenAI
ITs are **NOT RUN**, rather than excluded to mask failures or counted as passed.
The already validated live matrix and AWS commands 9–15 were not repeated. There is
no new claim of physical NAS outage, real pending migration execution, live S3 startup
or production recovery verification. No existing migration SQL was changed.

Initial development runs found and corrected local compile/fixture issues (Spring
exception constructor, Mockito restubbing, lifecycle calls, explicit Flyway enablement
under the isolated runner, JDBC double for transactional tests). Final selected gates
have zero failures; their assertions and original regression tests were retained.

The subsequent plain Maven run exposed an environment-dependent regression: inherited
failover/S3 flags activated `PostgresAccessGate` in HTTP/CLI fixtures that deliberately
exclude `DataSource`. The reported five HTTP context errors and two CLI exit-code
failures were reproduced locally using public configuration and synthetic flags.
Those fixtures now explicitly select their simulated PostgreSQL-only routing. The
child CLI also loads a separate public configuration directory and a filtered process
environment; deliberately conflicting environment flags remain part of its test.
The degraded HTTP fixture continues to enable and exercise real failover wiring.
Making the advisor factory static prevents early configuration instantiation during
post-processor discovery, with an assertion covering the warning. That regression's 319 unit
and 11 local integration tests pass with both parent flags enabled, no early
post-processor warning and no synthetic credential in output or fresh reports.

After reviewing the Synology export, the final local gate passes 331 unit and 11
integration tests. Added diagnostic scenarios cover access denial, expired tokens,
missing credentials, SDK call/attempt timeouts, DNS/connect/TLS, unknown service codes,
generic SDK failures, invalid metadata, embedding errors and cancellation. Tests
assert original exception identity, absence of private sentinel data/throwable logs
and no extra infrastructure calls. An initial test import error was corrected before
the successful final gate; no live S3 repair is claimed.

Previous historical verification (before this change) included 227 selected Java tests
and the explicit `s3-readonly` startup path; it did not prove automatic degraded startup.
The earlier live query matrix is preserved in `../rag-live-failover-validation.md` and
is not repeated or promoted to evidence of these new startup changes.

## Manual API-only deployment on Synology (not executed)

1. Keep the existing private `.env`, AWS credentials, volumes and service addresses.
   Verify the existing routing flags are pgvector/true/true to enable this mode. No
   new credential or AWS/IAM change is required. New timeout defaults are optional.
2. Run the local gate above. Its successful Maven verify packages
   `target/r4r-spring-ai-rag-0.3.3.jar` under Java 21. Build the API image using that JAR
   as `app.jar` and the existing `docker-spring/Dockerfile`, with the NAS architecture.
   Do not accidentally reuse the old versioned `docker-spring/app.jar`. A disposable
   build context under `target/` avoids replacing that tracked artifact. Compose uses
   an image, not a build section; `docker compose build api` alone cannot build it.
3. Load the reviewed `r4r-api:0.3.3` image using the owner's normal process and update
   the versioned Compose file (new timeout forwarding; still no PG depends_on). Save
   the previous image ID/tag first for rollback. Do not distribute `.env` with an image.
4. In the existing Synology project directory, the owner can validate privately and
   recreate **only API**, without starting or stopping any dependencies:

   ```bash
   docker compose config --quiet
   docker compose up -d --no-deps --force-recreate api
   ```

   Do not run `compose down`, remove volumes, or start the entire stack to simulate
   failure. Keep Cloudflare and database services untouched. No index import/reindex.
5. Check API startup logs for ready/degraded/invalid state and one ordinary cited
   request. A startup integrity error must be investigated, not fixed by turning off
   Flyway validation. For rollback, restore the saved API image and recreate only API.

The deployment and any physical outage/recovery checks are manual, **NOT EXECUTED**
by this task. No production credentials were read or printed; no commit is pushed.


## Changed files

New production classes under `src/main/java/com/riansares/r4r/vector/`:
`PostgresAccessGate.java`, `PostgresFailureClassifier.java`,
`config/PostgresFailoverConfiguration.java`, `config/PostgresFailoverProperties.java`.
Updated there: `FailoverKnowledgeVectorStore.java` and
`config/KnowledgeVectorStoreConfiguration.java`. Runtime mappings/defaults:
`src/main/resources/application.yml`, `docker-spring/.env.base`,
`docker-spring/docker-compose.yml`.

New tests under `src/test/java/com/riansares/r4r/vector/`:
`PostgresAccessGateTest.java`, `config/PostgresFailoverStartupTest.java`,
`VectorDegradedHttpIT.java`. Updated runners:
`py-scripts/test_secret_isolation.py`, `py-scripts/test_compose_configuration.py`.
Regression fixtures: `rag/api/RagStreamHttpIT.java`,
`ingestion/KnowledgeIngestionCliProcessIT.java`, `config/PublicRagEnvironmentTest.java`.
S3 outage diagnostics: `vector/s3vectors/S3VectorsKnowledgeStore.java` and its unit tests.
Documentation: this operation guide and the routing summary in `README.md`.
