# py-codex-agent

Small automatic controller for the ordered R4R task plan.

It does not implement product code. It:

1. verifies accepted tasks;
2. selects the first pending or regressed task;
3. requests a structured read-only Codex plan;
4. launches OpenCode with the selected task and plan;
5. runs the exact deterministic gate;
6. requests a structured read-only Codex review;
7. permits a bounded number of revisions;
8. updates progress and memory;
9. creates a local commit when enabled;
10. advances automatically until complete or blocked.

Runtime output is written only under `runtime/runs/`. An unfinished task keeps
`runtime/locks/active-task.json`, allowing a later invocation to resume only when
its dirty paths remain within that task's scope.
