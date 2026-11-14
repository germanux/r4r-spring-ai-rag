"""Offline checks of the existing deployer's opt-in S3 replacement block; no AWS calls."""
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch


class S3SyncTest(unittest.TestCase):
    def run_sync(self, replace=True, corrupt_backup=False):
        block = Path(__file__).with_name('deploy.sh').read_text().split("<<'PY_SYNC'\n", 1)[1].split('\nPY_SYNC', 1)[0]
        def row(key, size, lang):
            return dict(key=key, data=dict(float32=[struct.unpack('<f', struct.pack('<f', .1))[0]] + [0] * (size - 1)),
                        metadata=dict(source=lang + '/guide.md', source_text='Full evidence', ordinal=0))
        old = [row('old', 768, 'es')]
        new = [row('es-new', 1024, 'es'), row('en-new', 1024, 'en')]
        actions = []
        state = dict(rows=old, dimension=768)
        if not replace:
            state.update(rows=[new[0]], dimension=1024)
        def aws(command, **kwargs):
            operation = command[command.index('s3vectors') + 1]
            actions.append(operation)
            output = {}
            if operation == 'get-index':
                output = dict(index=dict(dimension=state['dimension'], distanceMetric='cosine'))
            elif operation == 'list-vectors':
                output = dict(vectors=state['rows'])
            elif operation == 'delete-index':
                state['rows'] = []
            elif operation == 'create-index':
                state['dimension'] = json.loads(Path(command[-1].removeprefix('file://')).read_text())['dimension']
            elif operation == 'put-vectors':
                rows = {r['key']: r for r in state['rows']}
                rows.update({r['key']: r for r in json.loads(Path(command[-1].removeprefix('file://')).read_text())['vectors']})
                state['rows'] = list(rows.values())
            else:
                raise AssertionError('Unexpected AWS operation: ' + operation)
            return subprocess.CompletedProcess(command, 0, json.dumps(output), '')
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); export = root / 'current.json'; backup = root / 'backup'; backup.mkdir()
            # Exercise float32 comparison: the JSON spelling need not match AWS's decimal spelling.
            export.write_text(json.dumps(dict(vectors=new)).replace('0.10000000149011612', '0.1'))
            (backup / 'r4r_rag-full-nomic.dump').write_bytes(b'test archive, not production data')
            (backup / 'postgres-manifest.json').write_text(json.dumps(dict(sha256=hashlib.sha256((backup / 'r4r_rag-full-nomic.dump').read_bytes()).hexdigest())))
            (backup / 's3-vectors.json').write_text(json.dumps(dict(vectors=old)))
            (backup / 's3-restore-index.json').write_text(json.dumps(dict(vectorBucketName='amzn-r4r-vectors', indexName='amzn-r4r-vector-index',
                dimension=768, dataType='float32', distanceMetric='cosine', metadataConfiguration=dict(nonFilterableMetadataKeys=['source_text']))))
            (backup / 's3-manifest.json').write_text(json.dumps(dict(sha256={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in backup.glob('s3-*.json')})))
            if corrupt_backup:
                (backup / 'r4r_rag-full-nomic.dump').write_bytes(b'corrupted')
            with patch.object(sys, 'argv', ['sync', str(export), str(replace).lower(), str(backup)]), patch('subprocess.run', side_effect=aws), patch('builtins.print'):
                if corrupt_backup:
                    with self.assertRaises(AssertionError):
                        exec(compile(block, 'deploy.sh/PY_SYNC', 'exec'), {})
                else:
                    exec(compile(block, 'deploy.sh/PY_SYNC', 'exec'), {})
        return actions, state

    def test_replaces_only_the_backed_up_index_and_verifies_float32_metadata(self):
        actions, state = self.run_sync()
        self.assertLess(actions.index('delete-index'), actions.index('create-index'))
        self.assertEqual(1024, state['dimension'])
        self.assertEqual({'es-new', 'en-new'}, {r['key'] for r in state['rows']})

    def test_corrupt_backup_prevents_any_write(self):
        actions, _ = self.run_sync(corrupt_backup=True)
        self.assertFalse({'delete-index', 'create-index', 'put-vectors'} & set(actions))

    def test_retry_import_does_not_delete_index(self):
        actions, state = self.run_sync(replace=False)
        self.assertNotIn('delete-index', actions)
        self.assertEqual(2, len(state['rows']))


if __name__ == '__main__':
    unittest.main()
