# R4R setup + Claude Code phase 2.16

This patch fixes a false negative in `scripts/setup.sh`.

Claude Code accepts `--max-turns`, `--output-format`, `--permission-mode` and
`--append-system-prompt-file` in non-interactive print mode. Some installed releases do
not list every print-mode-only option in the top-level `claude --help` output. Phase
2.15 interpreted a missing help line as a missing parser feature, reinstalled the npm
package and aborted even though the surgical launcher subsequently used the option
successfully.

Phase 2.16:

- inspects both `claude --help` and `claude -p --help`;
- strips ANSI control sequences before matching;
- verifies that the selected executable can report a version;
- treats missing help entries as warnings rather than destructive update triggers;
- reports multiple `claude` executables in `PATH` to expose installation conflicts;
- retains fail-closed runtime behavior in the surgical launcher;
- supports optional strict documentation validation with
  `R4R_CLAUDE_STRICT_HELP=1 ./scripts/setup.sh`.

No authentication token, Claude configuration or project source file is modified.
