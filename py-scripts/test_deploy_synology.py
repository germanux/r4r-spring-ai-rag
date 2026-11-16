"""Run deployment with synthetic configuration and local SSH/Docker doubles only."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
DOUBLE = r'''#!/usr/bin/env python3
import json, os, pathlib, shutil, subprocess, sys, tempfile
root = pathlib.Path(os.environ['DEPLOY_FIXTURE'])
name = pathlib.Path(sys.argv[0]).name
args = sys.argv[1:]
with (root / 'calls.jsonl').open('a') as log:
    log.write(json.dumps([name, args]) + '\n')
if name == 'mvn':
    print('0.0.0')
elif name == 'ssh':
    if '-O' in args:
        sys.exit(0)
    command = args[-1]
    if command.startswith('mktemp -d'):
        stage = tempfile.mkdtemp(prefix='.r4r-deploy.', dir=root)
        (root / 'stage-path').write_text(stage)
        print(stage)
    else:
        command = command.replace(
            'export PATH=/usr/local/bin:/var/packages/ContainerManager/target/usr/bin:$PATH',
            'export PATH="' + str(root / 'bin') + ':$PATH"')
        sys.exit(subprocess.run(['/bin/bash', '-c', command]).returncode)
elif name == 'scp':
    files = []
    index = 0
    while index < len(args):
        if args[index] == '-o':
            index += 2
        elif args[index] == '-O':
            index += 1
        else:
            files.append(args[index])
            index += 1
    destination = files[-1].split(':', 1)[1]
    assert pathlib.Path(destination).stat().st_mode & 0o777 == 0o700
    for source in files[:-1]:
        shutil.copy(source, destination)
    if os.environ.get('FAIL_TRANSFER'):
        sys.exit(1)
elif name == 'sudo':
    if args != ['-v']:
        sys.exit(subprocess.run(args).returncode)
elif name == 'docker-compose':
    if 'config' in args:
        # Validation must use both uploaded files before touching active settings.
        assert (root / 'nas' / '.env').read_text() == 'PRIVATE=old-synthetic\n'
        env_files = [args[i + 1] for i, arg in enumerate(args) if arg == '--env-file']
        assert [pathlib.Path(path).name for path in env_files] == ['.env.base', '.env']
        assert pathlib.Path(env_files[1]).stat().st_mode & 0o777 == 0o600
        assert pathlib.Path(env_files[1]).read_text() == 'PRIVATE=new-synthetic\n'
        if os.environ.get('FAIL_CONFIG'):
            sys.exit(1)
elif name == 'docker':
    pass
else:
    raise AssertionError(name)
'''


class SynologyDeploymentTest(unittest.TestCase):
    def exercise(self, failure=None):
        with tempfile.TemporaryDirectory(prefix='r4r-deploy-test-') as directory:
            root = Path(directory)
            for name in ('scripts', 'docker-spring', 'nas', 'bin'):
                (root / name).mkdir()
            script = (ROOT / 'scripts/deploy-synology.sh').read_text().replace(
                'NAS_DIR="/volume1/docker/r4r"', f'NAS_DIR="{root / "nas"}"')
            (root / 'scripts/deploy-synology.sh').write_text(script)
            (root / 'scripts/export-docker-spring.sh').write_text(
                '#!/bin/bash\nset -eu\necho synthetic > docker-spring/r4r-api-0.0.0.tar\n')
            (root / 'scripts/export-docker-spring.sh').chmod(0o700)
            (root / 'scripts/build-frontend-angular.sh').write_text(
                '#!/bin/bash\nset -eu\n'
                'test ! -e "$(cat stage-path)/.env"\n'
                'touch frontend-completed\n')
            (root / 'docker-spring/docker-compose.yml').write_text(
                'services:\n  api:\n    image: r4r-api:old\n')
            (root / 'docker-spring/.env.base').write_text(
                'RAG_EMBEDDING_MODEL=qwen3-embedding:0.6b\n'
                'RAG_EMBEDDING_DIMENSIONS=1024\n')
            (root / 'docker-spring/.env').write_text('PRIVATE=new-synthetic\n')
            (root / 'nas/.env').write_text('PRIVATE=old-synthetic\n')
            (root / 'nas/.env.base').write_text('PUBLIC=old\n')
            (root / 'nas/docker-compose.yml').write_text('old-compose\n')
            for tool in ('ssh', 'scp', 'mvn', 'sudo', 'docker', 'docker-compose'):
                path = root / 'bin' / tool
                path.write_text(DOUBLE)
                path.chmod(0o700)
            env = {key: value for key, value in os.environ.items()
                   if key in ('PATH', 'HOME', 'LANG')}
            env.update(PATH=str(root / 'bin') + ':' + env['PATH'], DEPLOY_FIXTURE=str(root))
            if failure:
                env[failure] = '1'
            result = subprocess.run(['bash', 'scripts/deploy-synology.sh'],
                                    cwd=root, env=env, capture_output=True, text=True)
            self.assertNotIn('PRIVATE=', result.stdout + result.stderr)
            stage = Path((root / 'stage-path').read_text())
            self.assertFalse((stage / '.env').exists(), result.stderr)
            calls = [json.loads(line) for line in (root / 'calls.jsonl').read_text().splitlines()]
            if failure:
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual((root / 'nas/.env').read_text(), 'PRIVATE=old-synthetic\n')
                self.assertEqual((root / 'nas/.env.base').read_text(), 'PUBLIC=old\n')
                self.assertEqual((root / 'nas/docker-compose.yml').read_text(), 'old-compose\n')
                self.assertFalse((root / 'frontend-completed').exists())
                self.assertFalse(any(tool == 'docker' for tool, _ in calls))
            else:
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual((root / 'nas/.env').read_text(), 'PRIVATE=new-synthetic\n')
                self.assertEqual((root / 'nas/.env').stat().st_mode & 0o777, 0o600)
                self.assertEqual((root / 'nas/.env.base').read_bytes(),
                                 (root / 'docker-spring/.env.base').read_bytes())
                self.assertFalse(stage.exists())
                self.assertTrue((root / 'frontend-completed').exists())
                self.assertFalse(list((root / 'nas').rglob('.env.bak*')))

    def test_copy_configuration_and_finish_with_frontend(self):
        self.exercise()

    def test_invalid_configuration_preserves_active_files(self):
        self.exercise('FAIL_CONFIG')

    def test_failed_transfer_cleans_private_upload(self):
        self.exercise('FAIL_TRANSFER')


if __name__ == '__main__':
    unittest.main()
