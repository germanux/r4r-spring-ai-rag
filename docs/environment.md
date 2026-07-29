# How `.env` is loaded

`.env` is a project-local configuration file. It does not permanently modify Linux,
the user account, or the desktop session.

## Docker Compose

`scripts/db.sh` always calls:

```text
docker compose --env-file <repository>/.env -f docker-postgres/compose.yml ...
```

Compose substitutes `${VARIABLE}` expressions and passes the declared values into the
containers. The loading is explicit and independent of the shell's current directory.

## Spring Boot

`application.yml` contains:

```yaml
spring.config.import: optional:file:./.env[.properties]
```

Spring reads `.env` as a Java properties file when launched from the repository root.
The scripts and Maven gates run there. Real operating-system environment variables or
command-line properties have higher precedence and can override the file.

## OpenCode and the Python controller

`scripts/run-opencode.sh` and `scripts/run-codex-agent.sh` execute `source .env` with
automatic export enabled. Those values exist only in that script process and its child
processes. They are not written to system or user configuration.

## Git

`.env` is ignored because it is machine-specific. `.env.example` is the versioned
reference. The ZIP includes a ready local `.env`; `setup.sh` recreates it from the
example if it is missing.
