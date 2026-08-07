# Setup

Run:

```bash
./scripts/setup.sh
./scripts/verify.sh all
./scripts/run-ring-system.sh
```

The setup script may request sudo to install missing host tools. PostgreSQL itself is
never installed on the host; both databases use `docker-postgres/compose.yml`.

Ring publishes fresh assignments for the PC and LP full-stack workers. Workers run
through OpenCode and stop safely on stale assignments, scope violations, exhausted
attempts or failed gates. Bootstrap commits are disabled.
