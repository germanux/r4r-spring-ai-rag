# R4R setup + Claude Code phase 2.15

`./scripts/setup.sh` now installs and verifies OpenCode, Codex, Claude Code and
CodeGraph, creates the editable Python controller environment, supplies missing local
PostgreSQL defaults without overwriting existing values, and reports authentication
state separately from installation state.

Useful modes:

```bash
./scripts/setup.sh --tools-only
./scripts/setup.sh --skip-db
./scripts/setup.sh --skip-verify
```

Claude Code is installed from the official npm package
`@anthropic-ai/claude-code`. Authentication remains interactive and is never written to
the repository. Run `claude` once when `claude auth status` reports no active session.

The surgical launcher creates a detached temporary Git worktree under
`/tmp/r4r-claude-surgical/<RUN-ID>/worktree`; launching it from the Ring checkout does
not make Claude edit the Ring worktree.
