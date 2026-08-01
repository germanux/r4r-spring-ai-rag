#!/usr/bin/env python3
from __future__ import annotations

from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import os
from pathlib import Path
import subprocess
import tempfile
import threading

WORKER = Path(__file__).with_name('r4r-lp-compact-worker.py')


class Handler(BaseHTTPRequestHandler):
    content = ''

    def do_POST(self) -> None:
        length = int(self.headers.get('Content-Length', '0'))
        self.rfile.read(length)
        body = json.dumps({
            'choices': [{'message': {'content': type(self).content}}]
        }).encode('utf-8')
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *_: object) -> None:
        return


def make_repo(root: Path) -> None:
    (root / '.opencode' / 'commands').mkdir(parents=True)
    (root / '.opencode' / 'progress.json').write_text(
        json.dumps({'active_task': 'task-x', 'tasks': [{'id': 'task-x', 'status': 'PENDING'}]}),
        encoding='utf-8',
    )
    (root / '.opencode' / 'task-plan.json').write_text(
        json.dumps({'tasks': [{
            'id': 'task-x',
            'objective': 'Create a focused Java file',
            'command': '.opencode/commands/task-x.md',
            'allowed_paths': ['src/main/**', 'src/test/**'],
            'gate': ['true'],
        }]}),
        encoding='utf-8',
    )
    (root / '.opencode' / 'commands' / 'task-x.md').write_text('# Task X\n', encoding='utf-8')
    (root / 'pom.xml').write_text('<project/>\n', encoding='utf-8')
    subprocess.run(['git', 'init', '-q'], cwd=root, check=True)


def invoke(repo: Path, port: int) -> subprocess.CompletedProcess[str]:
    env = dict(os.environ)
    env.update({
        'R4R_PROGRESS_PATH': '.opencode/progress.json',
        'R4R_PLAN_PATH': '.opencode/task-plan.json',
    })
    return subprocess.run(
        [
            'python3', str(WORKER), '--repo', str(repo),
            '--base-url', f'http://127.0.0.1:{port}/v1',
            '--model', 'selftest', '--prompt', 'Implement task-x',
        ],
        env=env,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )


def main() -> int:
    server = HTTPServer(('127.0.0.1', 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            make_repo(repo)
            Handler.content = (
                '<<<R4R_FILE path="src/main/java/x/New.java">>>\n'
                'package x;\npublic class New {}\n'
                '<<<R4R_END_FILE>>>\n'
                '<<<R4R_SUMMARY>>>\n'
                '# Local understanding report\n'
                '## Task objective in my own words\n'
                'Create the focused Java file.\n'
                '## Instructions I reconciled\n'
                'Used the active task scope.\n'
                '## Mapping from requirements to changed code and tests\n'
                'Created New.java.\n'
                '## Claims supported by current gate evidence\n'
                'Post-edit gate not yet run.\n'
                '## Uncertainties, contradictions or possible instruction defects\n'
                'None.\n'
                '## Questions or corrections requested from Codex\n'
                'Review the exact diff.\n'
                '<<<R4R_END_SUMMARY>>>'
            )
            result = invoke(repo, server.server_port)
            if result.returncode != 0:
                raise RuntimeError(result.stderr or result.stdout)
            target = repo / 'src/main/java/x/New.java'
            if target.read_text(encoding='utf-8') != 'package x;\npublic class New {}\n':
                raise RuntimeError('Allowed file content mismatch')
            if '# Local understanding report' not in result.stdout:
                raise RuntimeError('Compact LP summary was not emitted')
            if 'Create the focused Java file.' not in result.stdout:
                raise RuntimeError('Model-authored compact LP summary was not preserved')

        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            make_repo(repo)
            Handler.content = (
                '<<<R4R_FILE path="scripts/escape.sh">>>\n'
                'echo forbidden\n'
                '<<<R4R_END_FILE>>>'
            )
            result = invoke(repo, server.server_port)
            if result.returncode == 0:
                raise RuntimeError('Out-of-scope model output was not rejected')
            if (repo / 'scripts/escape.sh').exists():
                raise RuntimeError('Out-of-scope file was written')
    finally:
        server.shutdown()
        thread.join(timeout=5)

    print('OK: compact LP worker self-test passed')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
