from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from r4r_ring_agent.ring_stabilization import (
    ArtifactValidation,
    MonitorOutcome,
    STATUS_INVALID_ARTIFACT,
    STATUS_PARTIAL,
    STATUS_REPEATED_ERROR,
    STATUS_SUCCESS,
    _classify_final_status,
    _parse_frontmatter,
    normalize_tool_error,
    validate_artifacts,
)


class RingStabilizationTests(unittest.TestCase):
    def test_external_directory_children_share_root_signature(self):
        root = Path("/home/german/Desarrollo/r4r-pc-worker.git")
        first, threshold1 = normalize_tool_error(
            "read",
            {"filePath": str(root / "src/main/App.java")},
            "The user has specified a rule which prevents you from using this specific tool call",
            forbidden_roots=[root],
        )
        second, threshold2 = normalize_tool_error(
            "glob",
            {"path": str(root / "src")},
            "external_directory denied",
            forbidden_roots=[root],
        )
        self.assertEqual(first, second)
        self.assertEqual(first, f"external_directory|{root}")
        self.assertEqual(threshold1, 2)
        self.assertEqual(threshold2, 2)

    def test_schema_error_is_normalized(self):
        signature, threshold = normalize_tool_error(
            "write",
            {"filePath": ".ring-agent/global-summary.md", "fileContent": "x"},
            'SchemaError(Missing key at ["content"]) callID=call_123',
            forbidden_roots=[],
        )
        self.assertEqual(
            signature,
            "tool_schema|write|missing:content|.ring-agent/global-summary.md",
        )
        self.assertEqual(threshold, 3)

    def test_frontmatter_parser(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "agent.md"
            path.write_text('---\nmode: primary\nmodel: "p/m"\n---\nbody\n', encoding="utf-8")
            parsed = _parse_frontmatter(path)
            self.assertEqual(parsed["mode"], "primary")
            self.assertEqual(parsed["model"], "p/m")

    def test_complete_artifact_validation(self):
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp)
            run_id = "20260801T200000Z"
            state = {
                "schema_version": 1,
                "run_id": run_id,
                "overall_status": "READY",
                "decisions": {
                    "PC": {
                        "action": "HOLD",
                        "task_id": None,
                        "reason": "Backend evidence requires review before continuation.",
                        "acceptance_gates": ["Exact backend gate is green and Codex ACCEPT is present."],
                    },
                    "LP": {
                        "action": "CONTINUE",
                        "task_id": "task-fe-01-angular17-bootstrap",
                        "reason": "Frontend has a bounded correction with direct evidence.",
                        "acceptance_gates": ["Unit tests and exact FE-01 gate are green."],
                    },
                },
                "integration_risks": [],
                "evidence_limitations": [],
            }
            (output / "state.json").write_text(json.dumps(state), encoding="utf-8")
            markdown = "# Review\n\n" + ("Evidence-grounded review and bounded next action. " * 5)
            for name in (
                "code-pc-review.md",
                "code-lp-review.md",
                "backend-frontend-handoff.md",
                "worker-understanding.md",
                "global-summary.md",
            ):
                (output / name).write_text(markdown, encoding="utf-8")
            result = validate_artifacts(output, run_id)
            self.assertTrue(result.complete, result)

    def test_repeated_error_has_precedence_over_complete_artifacts(self):
        monitor = MonitorOutcome(
            status=STATUS_REPEATED_ERROR,
            process_exit=-15,
            error={"code": "REPEATED_TOOL_ERROR"},
            repeated_signatures={"x": 3},
        )
        artifacts = ArtifactValidation(valid=[
            "state.json",
            "code-pc-review.md",
            "code-lp-review.md",
            "backend-frontend-handoff.md",
            "worker-understanding.md",
            "global-summary.md",
        ])
        status, flags, _ = _classify_final_status(monitor, artifacts)
        self.assertEqual(status, STATUS_REPEATED_ERROR)
        self.assertEqual(flags, [])

    def test_partial_artifacts_classification(self):
        monitor = MonitorOutcome(None, 1, None, {})
        artifacts = ArtifactValidation(valid=["state.json"], missing=["global-summary.md"])
        status, flags, _ = _classify_final_status(monitor, artifacts)
        self.assertEqual(status, STATUS_PARTIAL)
        self.assertIn(STATUS_PARTIAL, flags)

    def test_no_artifacts_is_invalid(self):
        monitor = MonitorOutcome(None, 1, None, {})
        artifacts = ArtifactValidation(missing=["state.json"])
        status, _, _ = _classify_final_status(monitor, artifacts)
        self.assertEqual(status, STATUS_INVALID_ARTIFACT)

    def test_success_requires_exit_zero_and_complete_artifacts(self):
        monitor = MonitorOutcome(None, 0, None, {})
        artifacts = ArtifactValidation(valid=[
            "state.json",
            "code-pc-review.md",
            "code-lp-review.md",
            "backend-frontend-handoff.md",
            "worker-understanding.md",
            "global-summary.md",
        ])
        status, flags, error = _classify_final_status(monitor, artifacts)
        self.assertEqual(status, STATUS_SUCCESS)
        self.assertEqual(flags, [])
        self.assertIsNone(error)


if __name__ == "__main__":
    unittest.main()
