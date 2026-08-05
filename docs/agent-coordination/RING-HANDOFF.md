# Backend ↔ Frontend handoff (RUN 20260805T222913Z)

## Current queue posture

- **PC/backend:** `task-06f-ingestion-validation` is still PENDING and currently blocked first by PC worktree conflict hygiene (`UU` evidence files), then by outstanding bounded REVISE corrections.
- **LP/frontend:** `task-fe-03c-citations` is still PENDING with Codex REVISE requirements not yet proven in rendered DOM tests.

## Ownership boundaries to keep disjoint

- **PC writes only backend scope** (not `frontend/**`).
- **LP writes only frontend scope** (not backend Java/test resources).
- Ring does not direct either worker to write Git history or bypass exact gates.

## Cross-stack integration risks right now

1. **False-green risk:** LP has a generic green gate summary while FE-03C proof remains incomplete.
2. **Preflight hygiene risk:** PC merge-conflicted artifact files can block deterministic `git diff --check` and hide true backend behavior.
3. **Artifact churn risk:** large `.opencode/current/**` changes across merges may reintroduce whitespace/conflict noise in both queues.

## Ordered handoff actions

1. **PC first correction pass:** clear unmerged evidence files; then apply bounded `application.yml` REVISE fix; rerun exact backend gate.
2. **LP correction pass in parallel-safe scope:** implement the three required FE-03C DOM assertions and rerun exact frontend gate.
3. Reassess integration only after both exact gates are green and both Codex decisions are `ACCEPT`.

## Acceptance checkpoints for next coordination cycle

- PC evidence includes: clean preflight + exact gate exit `0` + Codex `ACCEPT`.
- LP evidence includes: FE-03C DOM proof + exact gate exit `0` + Codex `ACCEPT`.
