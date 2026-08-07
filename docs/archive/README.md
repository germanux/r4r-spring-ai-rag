# Archived agent implementations

This directory contains historical controller, profile, plan and probe material.
Nothing below `docs/archive/` is a runtime authority or an executable dependency.

The production system uses:

- `.opencode/task-plan.json` as the only task plan;
- `config/r4r-agents.json` as the only agent/model configuration;
- `py-ring-agent/src/r4r_worker/` as the deterministic worker controller;
- OpenCode for every model session.

The former `py-codex-agent` source is preserved only for regression archaeology.
