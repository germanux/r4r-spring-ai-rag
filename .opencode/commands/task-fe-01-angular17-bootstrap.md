# FE-01 — Angular 17 standalone bootstrap

## Ownership and scope

LP/30B frontend worker. Edit only `frontend/**` and `docs/frontend/**`.
Never edit Java, Maven, PostgreSQL, knowledge files, agent configuration or Git history.

## Outcome

Create a strict Angular **17.3.x** standalone application under `frontend/`. Use npm,
TypeScript strict mode, routing, HttpClient provisioning and environment-based backend
URL configuration. Do not upgrade to Angular 18 or later.

The first scaffold may be generated with Angular CLI 17.3.12, but the final files and
`package-lock.json` must live entirely under `frontend/`.

## Verification

Run exactly `./scripts/frontend-task-gate.sh task-fe-01-angular17-bootstrap`.
