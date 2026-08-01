#!/usr/bin/env python3
from __future__ import annotations

import argparse
from dataclasses import dataclass
from fnmatch import fnmatch
import json
import os
from pathlib import Path
import re
import shutil
import sys
import tempfile
from datetime import datetime, timezone
from urllib import request, error

FILE_START_RE = re.compile(r'^<<<R4R_FILE path="([^"\n]+)">>>\s*$', re.MULTILINE)
FILE_BLOCK_RE = re.compile(
    r'^<<<R4R_FILE path="([^"\n]+)">>>\s*\n(.*?)\n<<<R4R_END_FILE>>>\s*$',
    re.MULTILINE | re.DOTALL,
)
SUMMARY_BLOCK_RE = re.compile(
    r'^<<<R4R_SUMMARY>>>\s*\n(.*?)\n<<<R4R_END_SUMMARY>>>\s*$',
    re.MULTILINE | re.DOTALL,
)
CAMEL_RE = re.compile(r'\b[A-Z][A-Za-z0-9]{6,}\b')
PATH_RE = re.compile(r'(?<![A-Za-z0-9_.-])((?:src|docs|knowledge|docker-postgres)/[A-Za-z0-9_./-]+)')


@dataclass(frozen=True)
class ActiveTask:
    task_id: str
    objective: str
    command: str
    allowed_paths: tuple[str, ...]
    gate: tuple[str, ...]


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding='utf-8'))


def resolve_active_task(repo: Path) -> ActiveTask:
    progress_rel = os.environ.get('R4R_PROGRESS_PATH', '.opencode/progress.frontend.json')
    plan_rel = os.environ.get('R4R_PLAN_PATH', '.opencode/task-plan.frontend.json')
    progress = load_json(repo / progress_rel)
    plan = load_json(repo / plan_rel)
    active = progress.get('active_task')
    if not isinstance(active, str) or not active:
        for item in progress.get('tasks', []):
            if item.get('status') != 'ACCEPTED':
                active = item.get('id')
                break
    if not isinstance(active, str) or not active:
        raise RuntimeError('No active task is available for the compact LP worker')
    for item in plan.get('tasks', []):
        if item.get('id') == active:
            return ActiveTask(
                task_id=active,
                objective=str(item.get('objective') or ''),
                command=str(item.get('command') or ''),
                allowed_paths=tuple(str(v) for v in item.get('allowed_paths', [])),
                gate=tuple(str(v) for v in item.get('gate', [])),
            )
    raise RuntimeError(f'Active task {active!r} is missing from task-plan.json')


def safe_relative_path(value: str) -> str:
    value = value.strip().replace('\\', '/')
    path = Path(value)
    if not value or path.is_absolute() or '..' in path.parts:
        raise RuntimeError(f'Unsafe model output path: {value!r}')
    normalized = path.as_posix()
    if normalized.startswith('.git/') or normalized == '.git':
        raise RuntimeError(f'Git metadata path is forbidden: {normalized}')
    return normalized


def is_allowed(path: str, patterns: tuple[str, ...]) -> bool:
    return any(fnmatch(path, pattern) for pattern in patterns)


def extract_instruction_paths(prompt: str, repo: Path) -> list[str]:
    result: list[str] = []
    for raw in prompt.splitlines():
        line = raw.strip()
        if not line.startswith('- '):
            continue
        candidate = line[2:].strip().strip('`')
        if candidate and (repo / candidate).is_file():
            result.append(candidate)
    return result


def score_source_files(repo: Path, prompt: str, task: ActiveTask) -> list[Path]:
    roots = [repo / 'src' / 'main', repo / 'src' / 'test']
    candidates: list[Path] = []
    for root in roots:
        if root.exists():
            candidates.extend(p for p in root.rglob('*') if p.is_file() and p.suffix in {'.java', '.kt', '.ts', '.html', '.css', '.js'})

    identifiers = set(CAMEL_RE.findall(prompt))
    identifiers.update(CAMEL_RE.findall(task.objective))
    explicit_paths = set(PATH_RE.findall(prompt))
    scored: list[tuple[int, str, Path]] = []
    for path in candidates:
        rel = path.relative_to(repo).as_posix()
        try:
            text = path.read_text(encoding='utf-8')
        except (OSError, UnicodeDecodeError):
            continue
        score = 0
        if rel in explicit_paths:
            score += 1000
        if path.stem in identifiers:
            score += 500
        for identifier in identifiers:
            if identifier in text:
                score += 20
        if task.task_id.split('-')[-1] in rel.lower():
            score += 15
        if 'Application' in path.stem:
            score += 2
        if score:
            scored.append((-score, rel, path))
    scored.sort()
    return [item[2] for item in scored[:12]]


def build_context(repo: Path, prompt: str, task: ActiveTask, max_chars: int) -> str:
    selected: list[Path] = []
    seen: set[str] = set()

    def add(relative: str) -> None:
        path = repo / relative
        if not path.is_file():
            return
        rel = path.relative_to(repo).as_posix()
        if rel in seen:
            return
        seen.add(rel)
        selected.append(path)

    for relative in extract_instruction_paths(prompt, repo):
        add(relative)
    add(task.command)
    add('pom.xml')
    for match in PATH_RE.findall(prompt):
        add(match.rstrip('.,:;'))
    for path in score_source_files(repo, prompt, task):
        add(path.relative_to(repo).as_posix())

    # Existing task-scoped changes are always relevant for compiler/test repair.
    try:
        import subprocess
        changed = subprocess.run(
            ['git', 'status', '--porcelain=v1', '-z'], cwd=repo,
            text=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, check=False,
        ).stdout
        entries = changed.split('\0')
        for entry in entries:
            if len(entry) >= 4:
                candidate = entry[3:]
                if ' -> ' in candidate:
                    candidate = candidate.split(' -> ', 1)[1]
                if is_allowed(candidate, task.allowed_paths):
                    add(candidate)
    except Exception:
        pass

    chunks: list[str] = []
    used = 0
    for path in selected:
        rel = path.relative_to(repo).as_posix()
        try:
            text = path.read_text(encoding='utf-8')
        except (OSError, UnicodeDecodeError):
            continue
        header = f'\n===== FILE {rel} =====\n'
        remaining = max_chars - used - len(header)
        if remaining <= 0:
            break
        if len(text) > remaining:
            text = text[: max(0, remaining - 80)] + '\n...[truncated by compact worker]...\n'
        chunks.append(header + text)
        used += len(header) + len(text)
        if used >= max_chars:
            break
    return ''.join(chunks)


def call_model(base_url: str, model: str, system: str, user: str, max_tokens: int, timeout: int) -> tuple[str, dict]:
    endpoint = base_url.rstrip('/') + '/chat/completions'
    payload = {
        'model': model,
        'stream': False,
        'temperature': 0.20,
        'max_tokens': max_tokens,
        'messages': [
            {'role': 'system', 'content': system},
            {'role': 'user', 'content': user},
        ],
    }
    req = request.Request(
        endpoint,
        data=json.dumps(payload).encode('utf-8'),
        headers={'Content-Type': 'application/json'},
        method='POST',
    )
    try:
        with request.urlopen(req, timeout=timeout) as response:
            raw = response.read().decode('utf-8')
    except error.HTTPError as exc:
        body = exc.read().decode('utf-8', errors='replace')
        raise RuntimeError(f'LP compact model HTTP {exc.code}: {body[-4000:]}') from exc
    except error.URLError as exc:
        raise RuntimeError(f'LP compact model connection failed: {exc}') from exc
    data = json.loads(raw)
    try:
        content = data['choices'][0]['message']['content']
    except (KeyError, IndexError, TypeError) as exc:
        raise RuntimeError(f'Unexpected LP model response: {raw[-4000:]}') from exc
    if not isinstance(content, str) or not content.strip():
        raise RuntimeError('LP model returned no textual patch content')
    return content, data


def parse_file_blocks(text: str) -> list[tuple[str, str]]:
    blocks = FILE_BLOCK_RE.findall(text.strip())
    if not blocks:
        starts = FILE_START_RE.findall(text)
        detail = f' starts={starts}' if starts else ''
        raise RuntimeError('LP model output did not contain complete R4R file blocks.' + detail)
    result: list[tuple[str, str]] = []
    seen: set[str] = set()
    for raw_path, content in blocks:
        path = safe_relative_path(raw_path)
        if path in seen:
            raise RuntimeError(f'Duplicate model output path: {path}')
        seen.add(path)
        result.append((path, content.rstrip() + '\n'))
    return result


def parse_summary(text: str, task: ActiveTask, blocks: list[tuple[str, str]]) -> str:
    match = SUMMARY_BLOCK_RE.search(text.strip())
    if match is not None and match.group(1).strip():
        summary = match.group(1).strip()
        if not summary.startswith('# Local understanding report'):
            summary = '# Local understanding report\n\n' + summary
        return summary.rstrip() + '\n'

    changed = ', '.join(path for path, _ in blocks) or 'none'
    return (
        '# Local understanding report\n\n'
        '## Task objective in my own words\n'
        f'{task.objective or task.task_id}\n\n'
        '## Instructions I reconciled\n'
        'The compact LP response omitted its requested model-authored summary. '
        'Codex must rely on the exact diff and controller gate evidence.\n\n'
        '## Mapping from requirements to changed code and tests\n'
        f'Changed paths reported by the compact worker: {changed}.\n\n'
        '## Claims supported by current gate evidence\n'
        'No post-edit gate claim was available to the model at generation time. '
        'The controller appends the authoritative gate result.\n\n'
        '## Uncertainties, contradictions or possible instruction defects\n'
        'The requested summary block was missing.\n\n'
        '## Questions or corrections requested from Codex\n'
        'Review the exact patch and deterministic gate evidence.\n'
    )


def apply_files(repo: Path, task: ActiveTask, blocks: list[tuple[str, str]]) -> Path:
    for path, _ in blocks:
        if not is_allowed(path, task.allowed_paths):
            raise RuntimeError(f'Model attempted an out-of-scope path for {task.task_id}: {path}')
        if path.startswith(('runtime/', '.opencode/', 'scripts/', 'py-codex-agent/')):
            raise RuntimeError(f'Controller/configuration path is forbidden to LP product worker: {path}')

    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    backup = repo / 'runtime' / 'control' / f'lp-compact-backup-{stamp}'
    backup.mkdir(parents=True, exist_ok=False)

    for relative, content in blocks:
        target = repo / relative
        resolved_parent = target.parent.resolve()
        repo_root = repo.resolve()
        if repo_root != resolved_parent and repo_root not in resolved_parent.parents:
            raise RuntimeError(f'Output escapes repository: {relative}')
        if target.exists():
            backup_target = backup / relative
            backup_target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(target, backup_target)
        target.parent.mkdir(parents=True, exist_ok=True)
        fd, tmp_name = tempfile.mkstemp(prefix=target.name + '.', dir=target.parent)
        try:
            with os.fdopen(fd, 'w', encoding='utf-8', newline='') as handle:
                handle.write(content)
            os.replace(tmp_name, target)
        finally:
            if os.path.exists(tmp_name):
                os.unlink(tmp_name)
    return backup


def write_debug(repo: Path, stem: str, text: str) -> Path:
    directory = repo / 'runtime' / 'control' / 'lp-compact'
    directory.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ')
    path = directory / f'{stamp}-{stem}'
    path.write_text(text, encoding='utf-8')
    return path


def emit_text(text: str) -> None:
    print(json.dumps({'type': 'message', 'part': {'type': 'text', 'text': text}}, ensure_ascii=False))


def smoke(base_url: str, model: str, timeout: int) -> int:
    content, _ = call_model(
        base_url, model,
        'Reply with the exact token requested. Do not call tools.',
        'Reply exactly LP_COMPACT_OK',
        32, timeout,
    )
    if 'LP_COMPACT_OK' not in content:
        raise RuntimeError(f'Unexpected compact smoke response: {content!r}')
    print('OK: inferencia directa compacta LP_COMPACT_OK')
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument('--repo', type=Path, required=True)
    parser.add_argument('--base-url', required=True)
    parser.add_argument('--model', required=True)
    parser.add_argument('--prompt')
    parser.add_argument('--prompt-file', type=Path)
    parser.add_argument('--smoke', action='store_true')
    parser.add_argument('--max-context-chars', type=int, default=int(os.environ.get('R4R_LP_MAX_CONTEXT_CHARS', '42000')))
    parser.add_argument('--max-tokens', type=int, default=int(os.environ.get('R4R_LP_MAX_OUTPUT_TOKENS', '6144')))
    parser.add_argument('--timeout', type=int, default=int(os.environ.get('R4R_LP_REQUEST_TIMEOUT', '7200')))
    args = parser.parse_args()

    repo = args.repo.resolve()
    if args.smoke:
        return smoke(args.base_url, args.model, args.timeout)

    prompt = args.prompt
    if args.prompt_file:
        prompt = args.prompt_file.read_text(encoding='utf-8')
    if not prompt:
        raise RuntimeError('Compact LP worker received no controller prompt')

    task = resolve_active_task(repo)
    context = build_context(repo, prompt, task, args.max_context_chars)
    system = '''You are the compact implementation worker in a controlled coding pipeline.
You have NO tools. The controller has already supplied the exact task, Codex plan,
current gate failure and selected repository files. Produce complete replacement file
contents only for files that must be created or changed now.

Output format is strict and contains no Markdown fences or prose outside the blocks:
<<<R4R_FILE path="relative/path.ext">>>
complete file content
<<<R4R_END_FILE>>>

Repeat that block for each changed file. After the final file, output exactly one report:
<<<R4R_SUMMARY>>>
# Local understanding report
## Task objective in my own words
## Instructions I reconciled
## Mapping from requirements to changed code and tests
## Claims supported by current gate evidence
## Uncertainties, contradictions or possible instruction defects
## Questions or corrections requested from Codex
<<<R4R_END_SUMMARY>>>

The report must explain why each changed file is needed. Because the controller runs the
post-edit gate after this response, mark post-edit test success as not yet proven.
Never output Git commands, patches, deletions, controller/config files or paths outside
the active task. Keep the solution minimal, compile-ready and consistent with the
supplied source interfaces.'''
    user = f'''ACTIVE TASK: {task.task_id}
OBJECTIVE: {task.objective}
ALLOWED PATH PATTERNS: {list(task.allowed_paths)}
EXACT GATE: {list(task.gate)}

CONTROLLER/CODEX PACKET:
{prompt}

BOUNDED REPOSITORY CONTEXT:
{context}

Return only complete R4R file blocks followed by the R4R summary block.'''

    request_debug = write_debug(repo, 'request.txt', system + '\n\n' + user)
    content, raw = call_model(args.base_url, args.model, system, user, args.max_tokens, args.timeout)
    response_debug = write_debug(repo, 'response.txt', content)
    write_debug(repo, 'response.json', json.dumps(raw, indent=2, ensure_ascii=False))
    blocks = parse_file_blocks(content)
    summary = parse_summary(content, task, blocks)
    backup = apply_files(repo, task, blocks)
    emit_text(
        f'Compact LP worker wrote {len(blocks)} task-scoped file(s): '
        + ', '.join(path for path, _ in blocks)
        + f'. Backup: {backup.relative_to(repo)}. Request: {request_debug.relative_to(repo)}. '
        + f'Response: {response_debug.relative_to(repo)}.'
    )
    emit_text(summary)
    return 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except (OSError, ValueError, RuntimeError, json.JSONDecodeError) as exc:
        print(f'LP_COMPACT_WORKER_ERROR: {exc}', file=sys.stderr)
        raise SystemExit(1)
