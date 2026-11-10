"""Deployment/evaluation transport only. Business ingestion lives in Java."""
import json
import os
import re
import subprocess


def aws(*args):
    env = dict(os.environ, AWS_RETRY_MODE='standard', AWS_MAX_ATTEMPTS='1', AWS_PAGER='')
    result = subprocess.run(['aws', *args, '--profile', 'r4r-codex', '--region', 'eu-north-1', '--no-cli-pager'],
                            capture_output=True, text=True, env=env, timeout=50)
    if result.returncode:
        code = re.search(r'\(([A-Za-z0-9]+Exception)\)', result.stderr)
        raise RuntimeError('AWS operation failed: ' + (code[1] if code else 'CLI error'))
    return json.loads(result.stdout) if result.stdout.strip() else {}
