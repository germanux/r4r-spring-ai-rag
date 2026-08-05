# PC Code Review (task-06e-child-process)

## Current evidence reviewed

- `pc-runtime/progress.json`: active task is `task-06e-child-process` and still `PENDING`.
- `pc-runtime/codex-qwen3-extra-instructions.md`: Codex decision is `REVISE` with a mandatory bounded correction packet.
- `pc-git-status.txt` + `pc-git-diff-stat.txt`: only one modified file with just **2 insertions** in `TestChildApplicationContextInitializer.java`.
- `pc-runtime/manifest.json`: no current-run `gate_summary`, `codex_review`, `checkpoint`, or `local_understanding` artifacts published in this snapshot.

## First current defect

The current PC state is a **partially applied REVISE** with no proof of closure. The Codex packet requires a full mechanism shift (test SPI registration and assignable service replacement sequencing), but the visible diff is minimal and isolated. That is insufficient evidence that the full correction packet was implemented and revalidated.

## Bounded next action (one worker pass)

1. Complete the full Codex packet for `task-06e-child-process` strictly in test-only scope:
   - process IT,
   - one initializer,
   - `src/test/resources/META-INF/spring.factories` (preserve existing entries),
   - helper cleanup adjustments.
2. Remove unsupported `-Dcontext.initializer.classes` path and rely on test-classpath SPI loading.
3. Ensure replacement bean is assignable to `KnowledgeIngestionService` and installed at the required lifecycle timing (post-definition registration, pre-singleton creation), while keeping real orchestration active.
4. Re-run exact gate once and retain diagnostics bundle.

## Acceptance conditions

- `./scripts/task-gate.sh task-06e-child-process` exits `0`.
- Diagnostics prove expected child-process behavior (success exit/result line and failure classification constraints from Codex packet).
- Codex returns `ACCEPT` for `task-06e-child-process` before task closure.

## Avoid repeating

- Do **not** iterate on tiny initializer-only edits without completing the mandated SPI + type-compatible replacement path and producing fresh gate/Codex evidence.

## Evidence paths

- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260805T174028Z/pc-runtime/progress.json`
- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260805T174028Z/pc-runtime/codex-qwen3-extra-instructions.md`
- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260805T174028Z/pc-git-status.txt`
- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260805T174028Z/pc-git-diff-stat.txt`
- `/home/german/Desarrollo/r4r-ring-agent.git/runtime/ring-agent/ring/20260805T174028Z/pc-runtime/manifest.json`
