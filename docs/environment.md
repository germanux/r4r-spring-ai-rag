# Project-local environment

`.env` contains project variables. It does not permanently modify system or user
environment variables.

## Docker Compose

`scripts/db.sh` calls:

```bash
docker compose --env-file .env -f docker-postgres/compose.yml ...
```

Compose substitutes the database image, names, ports and credentials.

## Spring Boot

`application.yml` imports:

```yaml
spring:
  config:
    import: optional:file:./.env[.properties]
```

Spring reads the same project file as application properties. Real operating-system
environment variables still take precedence.

## Shell launchers

Agent scripts use:

```bash
set -a
source .env
set +a
```

Those variables exist only in that shell and its child processes.

## Git

`.env` is ignored because it can contain machine-specific values or secrets.
`.env.example` is the versioned template.
