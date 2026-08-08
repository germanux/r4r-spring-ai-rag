from __future__ import annotations

from dataclasses import asdict, dataclass
import hashlib
import json
from pathlib import Path
import re
import shutil
from typing import Iterable, Sequence
from zipfile import ZIP_DEFLATED, ZipFile


_JAVA_PATH = re.compile(
    r"(?P<path>(?:[A-Za-z]:)?(?:[/\\][^\r\n:\[]+|src/(?:main|test)/java/[^\r\n:\[]+)\.java)"
    r"(?::\[(?P<line>\d+),(?P<column>\d+)\]|:(?P<stack_line>\d+))?"
)
_STACK_JAVA = re.compile(r"\((?P<name>[A-Za-z_$][A-Za-z0-9_$]*\.java):\d+\)")
_FRONTEND_PATH = re.compile(
    r"(?P<path>(?:frontend/|(?<![A-Za-z0-9_./-])src/)"
    r"[A-Za-z0-9_./@-]+\.(?:ts|tsx|html|css|scss|json))"
    r"(?::(?P<line>\d+):(?P<column>\d+))?"
)
_TEST_FAILURE = re.compile(
    r"^\[ERROR\]\s+(?P<class>[A-Za-z_$][A-Za-z0-9_$.]+)\."
    r"(?P<method>[A-Za-z_$][A-Za-z0-9_$]*)\s+[»:]",
    re.MULTILINE,
)


@dataclass(frozen=True)
class GateDiagnostics:
    classification: str
    summary: str
    fingerprint: str
    source_paths: tuple[str, ...]
    related_paths: tuple[str, ...]
    log_path: str
    summary_path: str
    manifest_path: str
    bundle_path: str

    def to_dict(self) -> dict[str, object]:
        return asdict(self)


def _relative_repo_path(repo: Path, value: str) -> str | None:
    normalized = value.replace("\\", "/")
    candidate = Path(normalized)
    if candidate.is_absolute():
        try:
            candidate = candidate.resolve().relative_to(repo.resolve())
        except (OSError, ValueError):
            return None
    text = candidate.as_posix().lstrip("./")
    if not text.endswith(".java"):
        return None
    if not (text.startswith("src/main/java/") or text.startswith("src/test/java/")):
        return None
    return text


def _java_index(repo: Path) -> dict[str, tuple[str, ...]]:
    values: dict[str, list[str]] = {}
    for root in (repo / "src/main/java", repo / "src/test/java"):
        if not root.exists():
            continue
        for path in root.rglob("*.java"):
            relative = path.relative_to(repo).as_posix()
            values.setdefault(path.name, []).append(relative)
    return {key: tuple(sorted(items)) for key, items in values.items()}


def extract_source_paths(repo: Path, stdout: str, stderr: str) -> tuple[str, ...]:
    text = stdout + "\n" + stderr
    paths: set[str] = set()
    index = _java_index(repo)

    for match in _JAVA_PATH.finditer(text):
        relative = _relative_repo_path(repo, match.group("path"))
        if relative and (repo / relative).is_file():
            paths.add(relative)

    for match in _STACK_JAVA.finditer(text):
        candidates = index.get(match.group("name"), ())
        if len(candidates) == 1:
            paths.add(candidates[0])

    for match in _TEST_FAILURE.finditer(text):
        simple_name = match.group("class").rsplit(".", 1)[-1] + ".java"
        candidates = index.get(simple_name, ())
        if len(candidates) == 1:
            paths.add(candidates[0])

    for match in _FRONTEND_PATH.finditer(text.replace("\\", "/")):
        relative = match.group("path").lstrip("./")
        # Angular commands execute from frontend/, so compiler diagnostics commonly
        # report paths such as src/app/example.spec.ts rather than repo-relative
        # frontend/src/app/example.spec.ts.
        if relative.startswith("src/"):
            relative = "frontend/" + relative
        if (repo / relative).is_file():
            paths.add(relative)

    return tuple(sorted(paths))


def classify_gate_failure(stdout: str, stderr: str, exit_code: int) -> tuple[str, str]:
    if exit_code == 0:
        return "green", "The deterministic task gate completed successfully."

    text = (stdout + "\n" + stderr).lower()
    if "compilation error" in text or "compilation failure" in text:
        return "compilation", "Java compilation or test compilation failed."
    if "angular 17 required" in text or "required frontend artifact is missing" in text:
        return "frontend-structure", "The Angular 17 frontend scaffold or a required task artifact is missing."
    if (
        "ng build" in text
        or "ng test" in text
        or "error ng" in text
        or "typescript error" in text
        or re.search(r"\berror ts\d+:", text)
    ):
        return "frontend-compilation", "Angular or TypeScript compilation failed."
    if "npm error" in text or "npm err!" in text:
        return "npm-failure", "The frontend npm command failed; inspect the exact npm output."
    if (
        "connection to 127.0.0.1:55433 refused" in text
        or "unable to obtain connection from database" in text
        or ("sql state" in text and "08001" in text)
    ):
        return (
            "database-unavailable",
            "The integration-test PostgreSQL service was unavailable; this is not a Java source defect.",
        )
    if "docker is unavailable" in text or "cannot connect to the docker daemon" in text:
        return "docker-unavailable", "Docker or Docker access was unavailable."
    if "tests run:" in text and ("failures:" in text or "errors:" in text):
        return "test-failure", "One or more deterministic tests failed or errored."
    if "flyway" in text and "migration" in text:
        return "migration-failure", "Flyway migration or schema initialization failed."
    return "gate-failure", "The deterministic task gate failed; inspect the full captured log."


def _related_paths(repo: Path, classification: str, source_paths: Sequence[str]) -> tuple[str, ...]:
    related: set[str] = set(source_paths)
    if classification in {"frontend-structure", "frontend-compilation", "npm-failure"}:
        for value in (
            "frontend/package.json",
            "frontend/package-lock.json",
            "frontend/angular.json",
            "frontend/tsconfig.json",
        ):
            if (repo / value).is_file():
                related.add(value)
    if classification in {"database-unavailable", "docker-unavailable", "migration-failure"}:
        for value in (
            "docker-postgres/compose.yml",
            ".env.example",
            "src/test/resources/application-test.yml",
            "src/main/resources/application.yml",
        ):
            if (repo / value).is_file():
                related.add(value)
    return tuple(sorted(related))


def _copy_paths(repo: Path, destination: Path, paths: Iterable[str]) -> None:
    for relative in paths:
        source = repo / relative
        if not source.is_file():
            continue
        target = destination / "files" / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)


def build_gate_diagnostics(
    repo: Path,
    evidence_dir: Path,
    command: Sequence[str],
    exit_code: int,
    stdout: str,
    stderr: str,
) -> GateDiagnostics:
    diagnostics_dir = evidence_dir / "diagnostics"
    diagnostics_dir.mkdir(parents=True, exist_ok=True)

    classification, summary = classify_gate_failure(stdout, stderr, exit_code)
    source_paths = extract_source_paths(repo, stdout, stderr)
    related_paths = _related_paths(repo, classification, source_paths)

    full_log = (
        "COMMAND\n"
        + " ".join(command)
        + f"\n\nEXIT CODE\n{exit_code}\n\nSTDOUT\n{stdout}\n\nSTDERR\n{stderr}\n"
    )
    log_path = diagnostics_dir / "gate-full.log"
    log_path.write_text(full_log, encoding="utf-8")

    fingerprint = hashlib.sha256(
        ("\0".join(command) + f"\0{exit_code}\0" + stdout + "\0" + stderr).encode(
            "utf-8", errors="replace"
        )
    ).hexdigest()

    summary_path = diagnostics_dir / "gate-summary.md"
    listed_sources = "\n".join(f"- `{value}`" for value in source_paths) or "- none"
    listed_related = "\n".join(f"- `{value}`" for value in related_paths) or "- none"
    summary_path.write_text(
        "# Gate diagnostic summary\n\n"
        f"- Classification: `{classification}`\n"
        f"- Exit code: `{exit_code}`\n"
        f"- Fingerprint: `{fingerprint}`\n"
        f"- Summary: {summary}\n\n"
        "## Source paths named by current evidence\n\n"
        f"{listed_sources}\n\n"
        "## Related files packaged for OpenCode escalation\n\n"
        f"{listed_related}\n\n"
        "## Full evidence\n\n"
        "OpenCode escalation must inspect `gate-full.log`; the local worker receives only this summary and a bounded tail.\n",
        encoding="utf-8",
    )

    _copy_paths(repo, diagnostics_dir, related_paths)

    manifest_path = diagnostics_dir / "error-manifest.json"
    manifest = {
        "schema_version": 1,
        "classification": classification,
        "summary": summary,
        "fingerprint": fingerprint,
        "command": list(command),
        "exit_code": exit_code,
        "source_paths": list(source_paths),
        "related_paths": list(related_paths),
        "full_log": "gate-full.log",
    }
    manifest_path.write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )

    bundle_path = diagnostics_dir / "escalation-error-bundle.zip"
    with ZipFile(bundle_path, "w", compression=ZIP_DEFLATED) as archive:
        for path in sorted(diagnostics_dir.rglob("*")):
            if not path.is_file() or path == bundle_path:
                continue
            archive.write(path, path.relative_to(diagnostics_dir).as_posix())

    return GateDiagnostics(
        classification=classification,
        summary=summary,
        fingerprint=fingerprint,
        source_paths=source_paths,
        related_paths=related_paths,
        log_path=log_path.relative_to(repo).as_posix(),
        summary_path=summary_path.relative_to(repo).as_posix(),
        manifest_path=manifest_path.relative_to(repo).as_posix(),
        bundle_path=bundle_path.relative_to(repo).as_posix(),
    )
