# S3 Vectors: AWS environment credentials and Synology handoff

## Verified configuration points

- `S3VectorsConfiguration.s3VectorsClient`: the AWS Java SDK builds its standard
  default credential chain. There is no custom authentication or Spring property
  credential adapter. The bean exists only when S3 is enabled.
- `RagVectorProperties` / `application.yml`: `RAG_S3_VECTORS_REGION` selects the
  explicit client region (`eu-north-1`); no `AWS_REGION`/`AWS_DEFAULT_REGION` alias
  is needed. Bucket/index are `amzn-r4r-vectors/amzn-r4r-vector-index`.
- `.env.base`: public operation settings only. AWS access/secret/session credential
  placeholders were removed; do not put even replacement-key placeholders there.
- `docker-compose.yml` includes `compose-services.yml` using `.env.base` as public
  interpolation defaults. The normal project `.env` overrides them, and the shell
  retains Compose's normal higher precedence.
- Only `api.environment` maps `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, optional
  `AWS_SESSION_TOKEN`. PostgreSQL, Cloudflared and Ollama receive no AWS variables.
  No container depends on `AWS_PROFILE` or a host `~/.aws` mount.

Spring's local `.env` import loads Java properties, not OS environment variables.
For local Java execution, the owner must securely provide AWS variables to the
process. Docker Compose does that through the explicit API environment mapping.
Do not translate private properties into a homemade SDK credentials provider.
The normal SDK chain is retained; deployment supplies the environment credentials.

Routing is unchanged: PostgreSQL is primary, writes go only to PostgreSQL, and S3
is an opt-in read fallback. `s3-readonly` remains the separate emergency profile;
this task did not alter it or run live failover.

## Manual steps — not executed by this task

1. **Effective IAM permissions.** Verify the least-privilege identity actually used
   by the API, resource policies, permissions boundaries, session policy and any
   applicable SCP. Check `s3vectors:ListVectors` for the specific index, not just
   the policy file you intended to deploy. `ListVectors` is an operator inventory
   check; the application search adapter uses `QueryVectors` with metadata.
   Therefore it needs both `s3vectors:QueryVectors` and `s3vectors:GetVectors`.
   `GetIndex` is needed for the operator metadata check below. Do not change IAM
   or grant broad administrative/root access merely to get a test to pass.
   See [AWS permission mapping](https://docs.aws.amazon.com/AmazonS3/latest/userguide/s3-vectors-access-management.html).

2. **Read the real index.** With scoped credentials securely supplied by the owner,
   run these read-only commands manually; this task did not execute them:

   ```sh
   aws s3vectors get-index --region eu-north-1 \
     --vector-bucket-name amzn-r4r-vectors --index-name amzn-r4r-vector-index \
     --no-cli-pager
   aws s3vectors list-vectors --region eu-north-1 \
     --vector-bucket-name amzn-r4r-vectors --index-name amzn-r4r-vector-index \
     --max-results 1 --no-paginate --no-cli-pager
   ```

   Confirm 768 dimensions, cosine distance, presence of the expected corpus and
   compatibility of its model/metadata with PostgreSQL. Listing one key does not
   prove corpus freshness, metadata compatibility or complete RAG correctness.
   If the index uses a customer-managed KMS key, verify its applicable decrypt
   permissions as well; do not alter encryption or policies in this task.

3. **Load credentials on Synology.** The owner manually sets existing scoped
   `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` in `docker-spring/.env`, plus
   `AWS_SESSION_TOKEN` only for temporary credentials. Keep file permissions private.
   Do not commit the file, print `docker inspect` environment output or paste keys
   into commands/logs. Remove/refresh any stale session token when switching to
   long-lived access keys. No credential rotation/copy is performed automatically.
   The owner must recreate the API container during an authorized deployment for
   changed environment values to take effect; restarting alone does not replace them.

4. **Enable and test fallback deliberately.** After the IAM/index checks, use
   `RAG_VECTOR_PRIMARY=pgvector`, `RAG_S3_VECTORS_ENABLED=true` and
   `RAG_VECTOR_FAILOVER_ENABLED=true` as owner-managed overrides. Use the normal
   profile for regular operation. First verify a normal PostgreSQL answer/citation.
   In an isolated test deployment, simulate PostgreSQL connection failure without
   stopping production, check `pgvector → s3vectors` logs, answer/citations and then
   restore access to confirm the next query uses PostgreSQL. Check that empty
   evidence/embedding/authentication errors do not trigger vector fallback.
   No reindex, production outage or AWS/IAM change is required by this handoff.

## Isolated validation

`S3VectorsEnvironmentCredentialsTest` starts clean child JVMs with synthetic AWS
variables, non-existent profile-file paths and metadata lookup disabled. It builds
**the production SDK client**, resolves the default chain, checks access/secret
and optional session handling, and verifies that the explicit region overrides a
conflicting synthetic `AWS_REGION`. It calls no AWS service method and emits only
a success/failure marker. Another test confirms disabled S3 creates no client.
Existing configuration tests verify no credential resolution when S3 is disabled.

`py-scripts/test_compose_configuration.py` uses an isolated public copy and synthetic
private overrides under a different filename. It checks precedence, exact AWS
values in API only, absence of profile/region aliases, and absence of credential
assignments in public defaults. Captured Compose JSON is not printed.

Safe project validation (does not resolve the actual private file):

```sh
cd docker-spring
docker compose --env-file .env.base config --no-env-resolution --quiet
```

Do not print plain `docker compose config` or `config --environment` against the real
configuration as a substitute. Current Maven builds use isolated test configuration;
real AWS integration is explicit via `mvn verify -Paws-it` and dedicated test resources.
See [Maven testing](../testing.md) for the offline build and optional integrations.

## Security and scope

`.env` is ignored by Git. The real private file, credentials and AWS profile files
were not read, copied, printed, loaded or modified. No AWS root credentials, real
service request, reindex, deployment, commit or push was used. Existing owner changes
under `docs/commands/` were left untouched. Tests contain synthetic credentials only.
Static checks cover relevant public configuration, implementation, tests and operation
documents; they are not a certification of every historical artifact in the repository.

## Results of this revision

- **230 selected Java unit tests PASSED** in 32 classes, 0 failures/errors/skips.
  Three new tests cover basic/temporary SDK environment credentials, non-default
  region selection and disabled S3 startup without credentials.
- **1 isolated Compose test PASSED** with synthetic private overrides. Public-only
  `docker compose ... config --no-env-resolution --quiet` also passed.
- Maven compilation/executable packaging and staged/unstaged whitespace checks passed.
- No apparent credential-value patterns were found in the scoped tracked public
  configuration, vector implementation/tests and operation documentation checked.
  New tests use clearly synthetic values; no real private file was inspected.
- Full DB/live integration suites: **NOT RUN**, since they may import private
  runtime configuration or require real services. IAM, actual index reads and
  Synology failover/deployment remain manual and unverified.

Modified: `.env.base` (remove credential placeholders only), `compose-services.yml`
(document existing API-only SDK injection), `S3VectorsConfiguration.java`
(document the existing default chain), `test_compose_configuration.py`, README,
`public-private-configuration.md`, `vector-store-failover.md`.
New: this guide and `S3VectorsEnvironmentCredentialsTest.java`.
No dependency, authentication implementation, routing rule, S3 query adapter,
write behavior or `s3-readonly` profile was changed.
