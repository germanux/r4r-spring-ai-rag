# PostgreSQL → S3 Vectors read failover

## Routing

`FailoverKnowledgeVectorStore` is the primary implementation of the existing
`KnowledgeVectorStore` port. `CitedRagService`, ingestion callers, generation,
embeddings and API contracts are unchanged. Provider adapters remain separate;
there is no JDBC interception, background monitor or sticky circuit state.

| Primary | Failover | S3 enabled | Behavior |
| --- | --- | --- | --- |
| pgvector | false | false/true | PostgreSQL only |
| pgvector | true | true | PostgreSQL, then S3 for proven infrastructure failures |
| s3vectors | false | true | S3 directly |
| other combinations | | | Rejected during bean creation |

Variables are `RAG_VECTOR_PRIMARY`, `RAG_VECTOR_FAILOVER_ENABLED`,
`RAG_S3_VECTORS_ENABLED`. Public defaults stay pgvector/false/false. Existing public
S3 configuration names `amzn-r4r-vectors/amzn-r4r-vector-index`, region `eu-north-1`,
768 dimensions/cosine are retained. No index was accessed or modified by this task.

Each new query starts at the configured primary. A valid empty PostgreSQL result
is returned unchanged. `index()` and `replaceSource()` always call the PostgreSQL
adapter, including when S3 is the read primary. Its transaction proxy is preserved.
S3 is never used for writes. The explicit recovery profile below refuses writes.

## Classification and diagnostics

Only the PostgreSQL search invocation is classified. It must contain positive JDBC
evidence: `CannotGetJdbcConnectionException`, SQL connection/pool exceptions,
JDBC infrastructure timeout, SQLSTATE `08xxx`, `57P01`, `57P02`, `57P03`, or `53300`.
Causes and JDBC `nextException` chains are examined with cycle protection. Transport
errors without JDBC evidence are not enough (Ollama also uses network connections).

Embedding configuration/unavailability, authentication (`28xxx`), bad SQL/schema/data,
configuration errors, cancellation/interruption and other SQL states veto fallback,
even if a recoverable outer wrapper exists. `57014` is treated as SQL cancellation;
there is no guess that every query cancellation is an infrastructure timeout.
Infrastructure `SQLTimeoutException` with no state or HYT00/HYT01 can recover.
The router rethrows excluded exceptions unchanged; it does not turn every DAO or
runtime failure into a fallback.

Both-provider failure gives a safe HTTP 503 `VectorStoreUnavailableException`,
retaining PostgreSQL as cause and S3 as a suppressed exception. Existing SSE produces
its controlled ERROR event. No query, content, endpoint or provider response body is
logged; logs record provider, result, duration and a bounded reason code.

S3 chunks/metadata and search arguments pass through unchanged. Existing citation
validation and SSE phases remain in the service. Retrieval metrics include both
attempts. If PostgreSQL fails after its internal embedding was computed, S3 may
compute another embedding through its existing adapter. This is a real second
attempt, not duplicate work for measurement. No invented isolated SQL timer is added.
S3 still uses its existing bounded SDK timeouts and no multi-attempt retry policy.

## Boot when PostgreSQL is unavailable

Normal startup retains Flyway and pgvector schema validation. Do not silently skip
failed migrations. To intentionally start **read-only S3 recovery**, the owner can
select the `s3-readonly` Spring profile, e.g. set `SPRING_PROFILES_ACTIVE=s3-readonly`
as a private override or pass `--spring.profiles.active=s3-readonly` locally.
This task does not edit the private file or execute that command with credentials.

The profile:
- selects S3 primary, enables S3 and disables redundant vector fallback;
- disables Flyway/SQL initialization and schema creation/validation only in this mode;
- defers just Spring AI's `vectorStore` bean and the PG adapter;
- blocks port writes before resolving PostgreSQL;
- leaves other modules, embeddings and generation unchanged.

Compose no longer makes the API depend on healthy PostgreSQL. Normal startup can
retry via the existing `restart: unless-stopped` while PostgreSQL starts. Recovery
mode can start without it. Exiting the profile restores normal migrations; returning
to writes must follow a successful normal startup. Explicit native Spring overrides
that force incompatible routing are rejected, not silently accepted.

The profile must be combined with owner-supplied AWS environment credentials.
Only the API container receives `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` and
optional `AWS_SESSION_TOKEN`; `AWS_PROFILE` and host `~/.aws` mounts are not needed.
The standard SDK default chain resolves them, not Spring authentication code.
`RAG_S3_VECTORS_REGION` explicitly selects the client region; no extra regional
variable is necessary. For a local JVM, Spring's `.env` property import alone does
not export OS environment variables: the owner must supply the AWS variables to
the process securely. No private files were read or loaded by this verification.
See [AWS/Synology manual verification](s3-vectors-environment.md).

## Offline verification

Java tests use mocked DataSource/JDBC, embeddings and S3 clients. The startup test
uses the actual Spring pgvector/Flyway auto-configurations and verifies no
`DataSource.getConnection()` call, no Flyway bean, no initialized pgvector bean,
a successful simulated S3 search, and rejected writes. Normal migration defaults
are checked separately by reading public YAML without resolving its imports.

Other tests cover classification exclusions, nested JDBC failures, empty results,
primary selection, retrying PostgreSQL after recovery, arguments/metadata/citations,
SSE/metrics, transactional writes and sanitized logging. The Compose Python test
runs only against public copied files and synthetic overrides.

No AWS, PostgreSQL, Ollama or paid generation call, ingestion, reindex or deployment
is part of this validation. Existing live/integration suites requiring those
services are not run. Git commit/push remain manual.

### Results

- **227 selected Java tests PASSED** across 31 classes, 0 failures/errors/skips.
  Includes 25 router tests and 3 configuration/startup tests, plus existing RAG,
  transaction, S3 adapter, embedding, generation, CORS and SSE regressions.
- **1 isolated Python/Compose test PASSED**; public-only Compose validation passed.
- Compilation/executable packaging and staged/unstaged whitespace checks passed.
- Live AWS/index compatibility, real PostgreSQL outage/recovery, IAM profile and
  deployed Synology behavior: **NOT RUN**. No credentials were resolved or tested.
- S3 data freshness is not asserted: this read-only implementation does not sync
  or reindex the existing index. The owner must validate that its model, vectors
  and metadata match the current corpus before enabling it operationally.

New files:
- `vector/FailoverKnowledgeVectorStore.java`
- `vector/VectorStoreUnavailableException.java`
- `vector/config/S3ReadOnlyStartupConfiguration.java`
- `application-s3-readonly.yml`
- `vector/FailoverKnowledgeVectorStoreTest.java`
- `vector/config/VectorReadOnlyStartupTest.java`
- this operation document.

Updated: `KnowledgeVectorStoreConfiguration`, the S3 configuration description,
existing vector wiring tests, public `.env.base`, Compose API dependencies/profile
forwarding, the isolated Compose test, README and public/private configuration guide.
No edits to `CitedRagService`, the port, either search adapter, the corpus or SQL
migrations. No `.env`, AWS profile or credential file was loaded.
