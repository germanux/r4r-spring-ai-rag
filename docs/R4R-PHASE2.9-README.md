# R4R phase 2.9 — local edit sound and LP report

## Included behavior

- Plays `scripts/u-freesound_community-success-1-6297.mp3` once when the local LLM
  changes at least one task-owned, non-runtime repository file in an edit attempt.
- `R4R_SUCCESS_SOUND=/absolute/path/file.mp3` overrides the default sound.
- Missing sound/player falls back to the terminal bell without failing the worker.
- The compact LP worker returns a model-authored `# Local understanding report` in
  the same response as its replacement files.
- The Python controller appends the exact post-edit gate exit code and timeout state
  before handing that report to Codex.

## Required audio file

The MP3 binary is not included. Place the requested file at:

```text
scripts/u-freesound_community-success-1-6297.mp3
```

or configure `R4R_SUCCESS_SOUND`.

## Apply ZIP

From the repository root:

```bash
unzip -o r4r-local-llm-sound-lp-summary-phase2.9-dropin.zip
```

## Apply Git patch

```bash
git apply --check r4r-local-llm-sound-lp-summary-phase2.9.patch
git apply r4r-local-llm-sound-lp-summary-phase2.9.patch
```

## Verification

```bash
bash -n scripts/notify-success.sh
python3 scripts/selftest-r4r-lp-compact-worker.py
PYTHONPATH=py-codex-agent/src python3 -m unittest \
  py-codex-agent.tests.test_runner.RunnerTest.test_compact_local_understanding_preserves_model_report_and_gate \
  py-codex-agent.tests.test_runner.RunnerTest.test_file_change_notification_uses_success_sound_mode
```
