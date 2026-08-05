# Worker understanding assessment

## PC understanding quality

Evidence:

- `pc-runtime/memory.md` still tracks unresolved objective (real CLI as bounded child JVM).
- `pc-runtime/codex-qwen3-extra-instructions.md` contains explicit corrections to prior misunderstandings.

Assessment:

- Understanding is partially aligned on objective, but prior implementation path was rejected by Codex for mechanism/type compatibility.
- Worker should follow the codified packet exactly instead of re-deriving approach.

Required improvement in next pass:

- Provide direct requirement→edit mapping for each numbered Codex instruction (SPI registration, marker gating, postprocessor ordering, assignable replacement type, assertions).

## LP understanding quality

Evidence:

- `lp-runtime/local_understanding.md` has no requirement-to-code mapping.
- `lp-runtime/checkpoint.json` shows `status: no-product-diff`.
- `lp-runtime/codex-qwen3-extra-instructions.md` explicitly flags local understanding omission.

Assessment:

- Current understanding quality is insufficient for closure despite green gate.
- The missing mapping and no-op pass are the immediate blockers to Codex acceptance.

Required improvement in next pass:

- Explicitly map bootstrap/strict/routing/HttpClient/Angular 17 deps/lockfile/env selection to concrete files and show where production replacement is configured.

## Direction for both workers

- Treat current Codex correction packets as authoritative checklists.
- Do one bounded correction pass each; do not widen scope.
- Re-run exact gate only after concrete edit verification and whitespace check.
