# py-codex-agent

A deliberately small controller for one cycle:

`pre-gate -> OpenCode -> path check -> post-gate -> optional Codex JSON review`.

It never commits or pushes. Every run is stored under `runtime/runs/<UTC timestamp>/`
with logs, evidence, decisions and `state.json` together.

`R4R_CODEX_CMD_JSON` is optional. When empty, the cycle ends as `REVIEW_PENDING`.
When configured, it must be a JSON array representing a command that reads the review
request from standard input and writes only the strict decision JSON to standard output.
