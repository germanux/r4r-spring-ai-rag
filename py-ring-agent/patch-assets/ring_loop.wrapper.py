"""R4R_RING_STABILIZED_WRAPPER

Compatibility facade: preserve every name from the previous ring_loop module while
routing run_ring_loop through the deterministic stabilization supervisor.
"""
from __future__ import annotations

from . import ring_loop_legacy as _legacy

for _name, _value in vars(_legacy).items():
    if _name not in {
        "run_ring_loop",
        "__name__",
        "__loader__",
        "__package__",
        "__spec__",
        "__doc__",
        "__file__",
        "__cached__",
        "__builtins__",
    }:
        globals()[_name] = _value

from .ring_stabilization import run_stabilized_ring_loop


def run_ring_loop(worktrees, once=False, **kwargs):
    aliases = {
        "timeout_seconds": "session_timeout_seconds",
        "sleep_seconds": "interval_seconds",
    }
    normalized = {aliases.get(key, key): value for key, value in kwargs.items()}
    allowed = {
        "interval_seconds",
        "session_timeout_seconds",
        "idle_timeout_seconds",
        "strict_model_check",
    }
    filtered = {key: value for key, value in normalized.items() if key in allowed}
    return run_stabilized_ring_loop(worktrees, once=once, **filtered)
