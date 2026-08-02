# R4R Phase 2.24 — Notification policy

The controller previously emitted audible signals for all of these events:

- a success MP3 whenever the local LLM changed a product file;
- two terminal bells after each green gate;
- four more terminal bells before a Codex handoff;
- an error MP3 whenever a controller run ended with a non-zero status.

Under a persistent supervisor, retries and short-lived worker failures made those
signals repeat even when branch synchronization itself completed successfully.

Phase 2.24 changes the default policy to `R4R_NOTIFICATION_MODE=changes`. This retains
the explicitly requested success MP3 for real local-LLM edits and suppresses gate,
handoff and error sounds. Notification events are still written to controller logs.

Supported modes:

- `off`: no audible notifications;
- `changes`: only real local-LLM file changes (default);
- `errors`: only error MP3s;
- `important`: file changes plus error MP3s;
- `all`: legacy behavior, including gate/handoff terminal bells.

When errors are enabled, `R4R_ERROR_SOUND_COOLDOWN_SECONDS` rate-limits the error MP3.
The default is 900 seconds. The state is kept outside the repository under
`${XDG_RUNTIME_DIR:-/tmp}/r4r-notifications-$UID`.
