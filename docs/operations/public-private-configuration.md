# Public defaults and private overrides

`docker-spring/.env.base` is public, versioned configuration. It replaces
`.env.example`. `docker-spring/.env` stays ignored and contains only secrets and
intentional overrides. The real private file was not manually opened, printed or modified. During an
additional bootstrap test, Spring unexpectedly accumulated imports and loaded it
despite a command-line import override. This violated the intended test isolation;
no values were printed. The corrected test uses a generated public-only YAML with
no private import at all, and its property-source assertions verify isolation.

## Normal operation and precedence

Run Spring from the repository root as usual. Spring imports the public file,
then the optional private file; later imports override earlier ones. OS variables
and explicit Spring command-line properties retain their standard higher priority.
Container execution receives the same values through Compose `env_file`; neither
file needs to be mounted in the API image. Imports are optional for that reason.

Use the usual `docker compose up -d` from `docker-spring` (do not execute it as part
of this validation). Compose 2.24+ is required; the local version checked is 2.40.3.
`docker-compose.yml` includes `compose-services.yml` with `.env.base` as public
**interpolation** defaults. The normal project `.env`/shell can override those
interpolation values. Service `env_file` then passes `.env.base`, followed by the
optional `.env`, to containers. These are different mechanisms.

PostgreSQL's `environment` maps the test DB/user intentionally for `postgres-test`.
Passwords are obtained from `env_file`, never interpolated into YAML. The API uses
a container-network JDBC URL by default, but an explicit `SPRING_DATASOURCE_URL`
override is respected. Cloudflared obtains `TUNNEL_TOKEN` via `env_file`, avoiding
an empty `environment` entry that would mask its value. Ollama receives only the
public file, so it is not given API or database credentials.

## Compact lists

- `RAG_GENERATION_PROVIDERS`: two ordered `URL|model` entries: PC, laptop.
- `RAG_EMBEDDING_PROVIDERS`: one to three ordered URLs, normally laptop, PC, NAS.
- `RAG_CORS_ALLOWED_ORIGINS`: one comma-separated explicit HTTP(S) origin list,
  including JetBrains, Python localhost/loopback, NAS and production origins.

The public file uses unquoted values to work unchanged as both dotenv and Java
properties. The compact-list adapter also accepts outer quotes as in the requested
private override example. Invalid list entries fail with non-sensitive messages.
`PublicRagEnvironment` runs after config imports; it maps compact lists to the
existing typed embedding properties and generation consumers. It performs no I/O,
network call, failover decision or credential handling. Native Spring overrides
still take precedence. Changing lists does not change models or index dimensions.

Models, timeouts, digest, dimensions, retrieval settings, warmup/SSE/security limits,
PostgreSQL names, knowledge paths, and independent S3/Luna flags are public defaults.
S3 remains disabled, separate from Luna (also disabled). S3 bucket/index are blank
until explicitly configured; enabling S3 requires valid names and least-privilege
non-root AWS credentials. This task does not resolve AWS credentials or contact AWS.

## Manual private-file cleanup (performed by the owner only)

1. Keep the existing `POSTGRES_PASSWORD`, `OPENAI_API_KEY`, `TUNNEL_TOKEN`, and any
   required scoped AWS credentials/profile references. Never copy them into the base.
2. Remove public values from the private file when the base default is correct.
   Keep genuine overrides, including existing PostgreSQL username/database/path values
   if they differ: changing these blindly will not rename an existing database/user.
3. Replace legacy generation variables `RAG_PC_OLLAMA_BASE_URL`,
   `RAG_PC_GENERATION_MODEL`, `RAG_FALLBACK_OLLAMA_BASE_URL`,
   `RAG_FALLBACK_GENERATION_MODEL` with one `RAG_GENERATION_PROVIDERS` override if needed.
4. Replace old embedding endpoint aliases (`RAG_EMBEDDING_BASE_URL`,
   `RAG_EMBEDDING_LP_BASE_URL`, `RAG_EMBEDDING_PC_BASE_URL`,
   `RAG_EMBEDDING_NAS_BASE_URL`) or indexed provider overrides with
   `RAG_EMBEDDING_PROVIDERS` only when the public order/endpoints should differ.
5. Replace all `RAG_API_ALLOWED_ORIGIN_*` variables with `RAG_CORS_ALLOWED_ORIGINS`.
   Preserve required development/public origins; do not use a wildcard.
6. Old `RAG_FALLBACK_READ_TIMEOUT` becomes `RAG_GENERATION_LAPTOP_TIMEOUT`;
   old per-provider embedding read timeout aliases become
   `RAG_EMBEDDING_LP_TIMEOUT`, `RAG_EMBEDDING_PC_TIMEOUT`, `RAG_EMBEDDING_NAS_TIMEOUT`.
7. Native overrides such as `R4R_RAG_*` or `SPRING_*` intentionally retain Spring
   precedence. Remove obsolete indexed/native settings if switching to compact lists.
8. Never overwrite a private key. No automatic migration script is provided.

## Safe validation

```sh
cd docker-spring
docker compose --env-file .env.base config --no-env-resolution --quiet
```

This explicit public interpolation source and `--no-env-resolution` avoid reading
private service env files. Do not use plain `docker compose config` or
`config --environment` against the actual deployment. Validation here uses only
public defaults, fictitious properties and loopback HTTP mocks, never a live DB/LLM.


## Validation results

- Compose 2.40.3: public-only structural validation **PASSED**, both in the project
  with the explicit safe flags and in an isolated public copy without a private file.
- Selected unit suites (29 classes): **196 PASSED**, 0 failures/errors/skipped,
  including the corrected bootstrap test. Compilation and executable packaging **PASSED**.
- Initial additional bootstrap test: **FAILED** (test-profile guard), followed by
  **FAILED** isolation assertion that exposed the accumulated-import incident above.
  Neither failure is presented as a successful isolated test.
- Corrected bootstrap verification uses only public defaults and fictitious properties.
- Database/live RAG/paid OpenAI/AWS integration tests: **NOT RUN** by design.

The normal runtime intentionally imports the owner-managed private file; tests must
not assume that overriding `spring.config.import` removes imports from other sources.
Avoid an unfiltered `mvn test` for this task: existing lifecycle/database tests may
import private runtime configuration or require real services.
