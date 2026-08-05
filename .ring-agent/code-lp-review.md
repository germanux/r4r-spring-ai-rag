# LP / Frontend Code Review - 20260803T220222Z

## Current State

**Active Task**: `task-fe-03-rag-ui`  
**Status**: PENDING, gate-red (exit code 2, gate-failure classification)  
**Last Green Attempt**: 2026-08-03T12:12:00.914271+00:00 (attempt 2)  
**Codex Decision**: REVISE

## Gate Diagnostic Summary

```
Classification: gate-failure
Exit code: 2
Fingerprint: 54153b57df193754978aac3cf16517a24f209586a2a4f49a162b67aa135bbfc1
Summary: The deterministic task gate failed; inspect the full captured log.

Source paths named by current evidence:
- frontend/src/app/app.component.spec.ts
- frontend/src/app/app.config.ts
- frontend/src/app/features/rag/rag-page.component.html
- frontend/src/app/features/rag/rag-page.component.spec.ts
- frontend/src/app/features/rag/rag-page.component.ts
```

## Issue Classification

The exact gate returns `exit code 2`. Codex REVISE decision indicates the worker only partially applied prior correction packet:

1. **Missing whitespace cleanup**: `git diff --check` was not run or passed before Angular work
2. **Incomplete corrections retained by worker**:
   - Trailing whitespace in diagnostic files
   - Unnecessary router provisioning still present in app.config.ts
   - Response answer rendered with `[innerHTML]` instead of interpolation
   - Property-only and `setTimeout`-based tests without controlled Subject emissions
   - Missing fixture DOM assertions for loading status, disabled controls, error alerts, structured abstention, escaped markup, citation ordering

## Evidence of Current State

From git status:  
```
M  frontend/src/app/app.component.spec.ts
M  frontend/src/app/app.component.ts
M  frontend/src/app/app.config.ts
AM frontend/src/app/features/rag/rag-page.component.html
A  frontend/src/app/features/rag/rag-page.component.scss
AM frontend/src/app/features/rag/rag-page.component.spec.ts
AM frontend/src/app/features/rag/rag-page.component.ts
```

## Mandatory Corrections for Next Pass

Per codex-qwen3-extra-instructions.md:

1. **Whitespace gate first**: Run `git diff --check` against frontend/** and remove trailing whitespace from every path named by the current gate output before running Angular work
2. **Remove router provisioning**: Keep Angular at major 17, preserve RAGAnswerResult contract and environment-backed BACKEND_URL; remove router imports/provisioning because AppComponent directly renders RagPageComponent and no accepted requirement uses routing
3. **Interpolate answer text**: Use interpolation `{{response.answer}}` instead of `[innerHTML]`; the escaping test must inspect fixture DOM and prove model markup remains literal with no injected element
4. **Controlled Subject emissions**: Replace property-only and setTimeout-based tests with controlled Subject<RAGAnswerResult> emissions followed by synchronous `fixture.detectChanges()` DOM assertions without timers
5. **DOM assertions required**:
   - Assert exact query payload, loading role="status", disabled textarea and submit button before emission
   - Re-enabled controls after success and error
   - Deterministic error text inside role="alert"
   - Rendered structured abstention, ordered citation ordinal/source/heading-path content
   - Absence of citation DOM when citation-like text exists only in the answer
6. **Cleanup**: Remove unused `of`, `throwError`, BehaviorSubject imports and NO_ERRORS_SCHEMA

## Next Action

Apply corrections to LP worktree:

1. First run `git diff --check` against frontend/** and remove all trailing whitespace from app.component.spec.ts, app.component.ts, app.config.ts, rag-page.* files
2. Remove router provisioning (RouterModule, RouterModule.forRoot) while retaining HTTP configuration
3. In template: use interpolation `{{response.answer}}` instead of `[innerHTML]`
4. In spec.ts: replace setTimeout timers with controlled Subject<RAGAnswerResult> emissions and synchronous fixture.detectChanges() DOM assertions
5. Assert all required DOM states: loading role="status", disabled controls before emission, re-enabled after success/error, error alert, structured abstention, escaped markup without injected HTML, citation ordering when present

Rerun exactly `./scripts/frontend-task-gate.sh task-fe-03-rag-ui`

## Acceptance Gates

- `./scripts/frontend-task-gate.sh task-fe-03-rag-ui` must return exit 0
- `git diff --check` must pass (no trailing whitespace)
- Codex decision must be ACCEPT after corrections
- fixture.detectChanges() DOM assertions for loading role="status", disabled controls before emission, re-enabled after success/error

## Avoid Repeating

Do not repeat incomplete correction application; the worker must remove all trailing whitespace from diagnostic files and implement all fixture-DOM assertions (loading status, disabled controls, error alert role="alert", structured abstention, escaped answer markup without injected HTML, citation ordering) in controlled Subject-based tests.
