# Public defaults and private overrides

`docker-spring/.env.base` is public and versioned. Optional `.env` is ignored and
contains secrets plus intentional overrides. No private file has been read, printed,
modified or loaded during this revision. The import-isolation failure in the prior
revision is historical; bootstrap tests now generate YAML with no private import.

## Provider lists

The authoritative format for all three independent circuits is
`URL-or-openai|model|read-timeout`, comma separated. See the complete
[ordered failover configuration and migration](ordered-provider-failover.md).

```dotenv
RAG_GENERATION_PROVIDERS=http://192.168.1.207:11434|qwen3:8b|20s,http://192.168.1.209:11434|qwen2.5:7b|60s,openai|gpt-6-luna|35s
RAG_AUDIT_PROVIDERS=http://192.168.1.207:11434|qwen3:8b|20s,openai|gpt-6-luna|35s
RAG_EMBEDDING_PROVIDERS=http://192.168.1.209:11434|qwen3-embedding:0.6b|1500ms,http://192.168.1.207:11434|qwen3-embedding:0.6b|1500ms,http://192.168.1.203:11434|qwen3-embedding:0.6b|30s
RAG_EMBEDDING_MODEL=qwen3-embedding:0.6b
```

Order is priority, including `openai` at any position in chat lists. No OpenAI
entry means no remote use for that function; the audit list can be empty.
Generation and embedding lists must be nonempty. Embeddings reject remote models
and preserve the common Qwen3 model, digest and 1024 dimensions.

`PublicRagEnvironment` adapts all lists after imports. IDs derive from endpoint,
not position. Each query starts from the preferred provider; only embeddings retain
the existing short negative cache. Cold loading timeouts do not enter that cache.
Warmup filters remote entries and keeps the first local generator's protected
priority, including when OpenAI precedes it. Runtime observation never loads models.
Connection/probe and global budgets remain separate. CORS still uses one origin
list. Malformed lists and obsolete activation settings fail without echoing values. Compose forwards only an internal
`RAG_LEGACY_PROVIDER_SETTINGS` marker when obsolete variables remain in interpolation
inputs, so an old private false flag cannot silently enable remote spending.

## Spring and Compose precedence

Local Spring imports `.env.base` first, optional `.env` second. Environment variables
and explicit Spring arguments retain their usual higher priority. Neither file is
mounted into the API container; Compose transmits the effective allowlisted values.

The normal Synology command remains `docker compose up -d` from `docker-spring`.
Compose 2.20.3+ is required for `include` ([Docker documentation](https://docs.docker.com/compose/how-tos/multiple-compose-files/include/)); local validation uses 2.40.3. The root Compose file includes
`compose-services.yml` with `.env.base` as interpolation defaults. The normal project
`.env` overrides the defaults, and shell variables take precedence. Unlike the prior
implementation, **services have no shared `env_file`**. Explicit `environment` maps
are intentional allowlists using those effective interpolation values:

| Service | Received values |
| --- | --- |
| PostgreSQL | DB, user, password only |
| PostgreSQL test | test DB/user mapped to PostgreSQL variables, password only |
| Spring API | public RAG/DB settings, DB password, OpenAI key, scoped AWS access/secret/session keys; no tunnel token or test DB credentials |
| Cloudflared | tunnel token only |
| Ollama | four `OLLAMA_*` settings and the embedding model for initialization/healthcheck; no credentials |

An explicit `SPRING_DATASOURCE_URL` override is honored; otherwise the API uses
`postgres:5432` on the container network. Host Java uses the LAN PostgreSQL settings.
No AWS root credentials or AWS service call is used. The API container uses standard
AWS access/secret/session environment variables; it does not depend on `AWS_PROFILE`
or `~/.aws` mounts. `RAG_S3_VECTORS_REGION` is the only regional configuration input.
S3 flags and explicit OpenAI list entries remain independent. See [AWS environment integration](s3-vectors-environment.md).

Normal API startup still requires PostgreSQL for migrations, but Compose no longer waits for PostgreSQL health. Ollama/NAS health is not a prerequisite
for API startup. For `docker compose up api`, neither Ollama nor PostgreSQL is a transitive dependency.
Configured local models may be unavailable on startup: asynchronous warmup records
the failure without preventing another configured provider from handling queries.
No real Synology deployment has been started or changed by these checks.

## Exact manual cleanup of the private file

Do not replace keys or alter an existing PostgreSQL user/database blindly. Only the
owner edits the private file. The following obsolete variables can be removed after
converting any intentional override into the new compact lists:

```text
RAG_OLLAMA_BASE_URL
RAG_GENERATION_MODEL
RAG_PC_OLLAMA_BASE_URL
RAG_PC_GENERATION_MODEL
RAG_FALLBACK_OLLAMA_BASE_URL
RAG_FALLBACK_GENERATION_MODEL
RAG_FALLBACK_READ_TIMEOUT
RAG_GENERATION_PC_TIMEOUT
RAG_GENERATION_LAPTOP_TIMEOUT
RAG_EMBEDDING_BASE_URL
RAG_EMBEDDING_LP_BASE_URL
RAG_EMBEDDING_PC_BASE_URL
RAG_EMBEDDING_NAS_BASE_URL
RAG_EMBEDDING_READ_TIMEOUT
RAG_EMBEDDING_PC_READ_TIMEOUT
RAG_EMBEDDING_NAS_READ_TIMEOUT
RAG_EMBEDDING_LP_TIMEOUT
RAG_EMBEDDING_PC_TIMEOUT
RAG_EMBEDDING_NAS_TIMEOUT
RAG_WARMUP_PC_TIMEOUT
RAG_WARMUP_LAPTOP_TIMEOUT
RAG_API_ALLOWED_ORIGIN_PUBLIC
RAG_API_ALLOWED_ORIGIN_LOCALHOST
RAG_API_ALLOWED_ORIGIN_LOOPBACK
RAG_API_ALLOWED_ORIGIN_PYTHON_LOCALHOST
RAG_API_ALLOWED_ORIGIN_PYTHON_LOOPBACK
RAG_API_ALLOWED_ORIGIN_SYNOX_NAS
RAG_API_ALLOWED_ORIGIN_SYNOX_NAS_S
```

Keep private `POSTGRES_PASSWORD`, `OPENAI_API_KEY`, `TUNNEL_TOKEN`, and, if S3 needs
them, least-privilege `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, optional
`AWS_SESSION_TOKEN`. Never use root credentials. Keep genuine overrides such as
`RAG_GENERATION_PROVIDERS`, `RAG_AUDIT_PROVIDERS`, `RAG_EMBEDDING_PROVIDERS`, `RAG_CORS_ALLOWED_ORIGINS`,
knowledge paths, differing DB identity, and S3 activation flags when required. OpenAI is enabled only by its list entries.
The **old compact list syntax must also be updated** to include each entry's timeout.
Public values identical to `.env.base` can be removed from `.env`. Native indexed
provider/old PC/fallback Spring properties are obsolete; use compact lists instead.
Arbitrary extra private variables are no longer forwarded automatically to containers.

All previously exposed operational defaults now live in `.env.base`. Constants
that are not currently configurable remain in code: generation and embedding keep-alive 8h,
OpenAI output cap 2048 tokens, input guard 32000 characters, and internal executor/
telemetry bounds. They are not new environment settings and were left unchanged
rather than extending the configuration surface during this correction.
S3 bucket/index remain the existing configured resources; this refactor does not change them.

Private native S3/OpenAI overrides previously forwarded by shared `env_file` must
be migrated to the canonical public names if used. Examples:
`R4R_RAG_VECTOR_PRIMARY` → `RAG_VECTOR_PRIMARY`,
`R4R_RAG_VECTOR_FAILOVERENABLED` → `RAG_VECTOR_FAILOVER_ENABLED`,
`R4R_RAG_VECTOR_S3VECTORS_ENABLED` → `RAG_S3_VECTORS_ENABLED`,
`R4R_RAG_VECTOR_S3VECTORS_REGION/BUCKET/INDEX` → `RAG_S3_VECTORS_REGION/BUCKET/INDEX`,
Legacy OpenAI flags/model/timeouts and singular audit settings must instead become
explicit entries in the corresponding provider lists. Do not retain activation flags: startup rejects them.
Use the actual native spelling you had configured; no private variables were
inspected, so this is a migration checklist, not a claim about their presence.

## Safe tests

```sh
cd docker-spring
docker compose --env-file .env.base config --no-env-resolution --quiet
```

For precedence and credential isolation, run from repository root:

```sh
python3 py-scripts/test_compose_configuration.py
```

The Python test copies only three public files into ignored `target/`, creates
synthetic overrides under a different filename, clears inherited interpolation
variables, and checks Compose's captured JSON without printing configuration.
It never references the actual private file. Java bootstrap tests use generated
public-only YAML because overriding `spring.config.import` does not remove other
imports. The current Maven defaults use an independent test classpath configuration:
`mvn test`, `mvn package` and `mvn verify` no longer require services or the private
file. Real integrations require explicit profiles; see [Maven testing](../testing.md).

## Results of this correction

- **199 selected Java unit tests PASSED** (29 classes), no failures/errors/skips.
  Includes four-provider generation/translation routing, four-provider embedding
  failover/recovery, reordering with stable endpoint identity, per-entry timeouts,
  local-only warmup, CORS, citations, SSE and mocked S3 regressions.
- **1 isolated Python/Compose test PASSED**, verifying synthetic override precedence,
  exact secret allowlists and the absence of an API→Ollama health dependency.
- Public-only Compose structural check, executable JAR packaging, and both Git
  whitespace checks: **PASSED**.
- Real Synology Compose version/startup, physical Ollama/DB, paid OpenAI and AWS:
  **NOT RUN**. NAS Compose must support `include` (2.20.3+); check with
  `docker compose version` on the NAS before manual deployment. No secrets are
  needed for that version check. No live deployment has been performed.
- No private `.env` was read, modified, printed or loaded in this correction.

Changed files: `.env.base`, `compose-services.yml`, `application.yml`,
`PublicRagEnvironment`, `RagConfiguration`, `OllamaModelPreloader`,
`PublicRagEnvironmentTest`, `OllamaServerSeparationTest`,
`EmbeddingProviderFailoverTest`, `OllamaModelPreloaderTest`, README and the three
configuration/embedding operation documents. New files:
`GenerationProviderProperties.java` and `py-scripts/test_compose_configuration.py`.
No S3 implementation, dependency, vector-store port or database schema changed.


For PostgreSQL outages, see [vector read failover and S3 read-only startup](vector-store-failover.md).
