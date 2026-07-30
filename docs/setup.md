# Setup

Run:

```bash
./scripts/setup.sh
./scripts/verify.sh all
./scripts/run-codex-agent.sh
```

The setup script may request sudo to install missing host tools. PostgreSQL itself is
never installed on the host; both databases use `docker-postgres/compose.yml`.

The first automatic run may create a baseline commit when the freshly extracted
repository is dirty and Task 01 is green. Later unowned dirty changes cause a safe
stop instead of being silently committed.
