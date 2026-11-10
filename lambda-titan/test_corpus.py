"""Python tests cover launchers/transport; corpus and vector invariants are tested in Java."""
import contextlib
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import aws_cli
import corpus
import deploy
import evaluate


class ToolTests(unittest.TestCase):
    @patch('corpus.subprocess.run')
    @patch.object(Path, 'is_file', return_value=True)
    def test_launcher_defaults_to_offline_java(self, exists, run):
        run.return_value.returncode = 0
        self.assertEqual(0, corpus.main([]))
        command = run.call_args.args[0]
        self.assertIn('com.riansares.r4r.titan.CorpusImporter', command)
        self.assertNotIn('--ingest', command)

    @patch('corpus.subprocess.run')
    @patch.object(Path, 'is_file', return_value=True)
    def test_launcher_forwards_explicit_ingestion_and_exit(self, exists, run):
        run.return_value.returncode = 3
        self.assertEqual(3, corpus.main(['--ingest']))
        self.assertIn('--ingest', run.call_args.args[0])

    @patch('aws_cli.subprocess.run')
    def test_aws_profile_region_and_single_attempt_are_fixed(self, run):
        run.return_value = subprocess.CompletedProcess([], 0, '{}', '')
        aws_cli.aws('sts', 'get-caller-identity')
        command = run.call_args.args[0]
        self.assertEqual('r4r-codex', command[command.index('--profile') + 1])
        self.assertEqual('eu-north-1', command[command.index('--region') + 1])
        self.assertEqual('1', run.call_args.kwargs['env']['AWS_MAX_ATTEMPTS'])

    @patch('aws_cli.subprocess.run')
    def test_aws_errors_do_not_leak_sdk_response(self, run):
        run.return_value = subprocess.CompletedProcess([], 254, '', '(AccessDeniedException) private-secret')
        with self.assertRaisesRegex(RuntimeError, 'AccessDeniedException') as failure:
            aws_cli.aws('lambda', 'invoke')
        self.assertNotIn('private-secret', str(failure.exception))

    def test_uses_all_existing_bilingual_cases(self):
        cases = evaluate.load_cases()
        self.assertEqual(60, len(cases))
        self.assertEqual(30, sum(c['lang'] == 'en' for c in cases))
        self.assertEqual(22, sum(not c['evidence'] for c in cases))
        self.assertEqual(60, len({(c['lang'], c['number']) for c in cases}))

    @patch('evaluate.aws')
    def test_evaluation_preview_never_calls_aws(self, aws):
        with contextlib.redirect_stdout(io.StringIO()):
            evaluate.main([])
        aws.assert_not_called()

    @patch('deploy.aws')
    def test_update_only_existing_lambda_and_verify_digest(self, aws):
        with tempfile.TemporaryDirectory() as directory:
            jar = Path(directory) / 'code.jar'; jar.write_bytes(b'code')
            config = dict(Runtime='java21', Role=deploy.ROLE, Handler='com.riansares.r4r.titan.RetrievalHandler::handleRequest',
                          Timeout=30, MemorySize=1024, Architectures=['x86_64'], RevisionId='revision', CodeSha256='old',
                          State='Active', LastUpdateStatus='Successful', VpcConfig=None)
            import base64, hashlib
            done = dict(config, CodeSha256=base64.b64encode(hashlib.sha256(b'code').digest()).decode())
            aws.side_effect = [{'Arn': 'arn:aws:iam::124021120482:user/r4r-codex-deployer'}, config, {}, done]
            with contextlib.redirect_stdout(io.StringIO()):
                deploy.update(jar)
            operations = [c.args[:2] for c in aws.call_args_list]
            self.assertEqual([('sts', 'get-caller-identity'), ('lambda', 'get-function-configuration'),
                              ('lambda', 'update-function-code'), ('lambda', 'get-function-configuration')], operations)

    @patch('evaluate.aws')
    def test_function_error_stops_evaluation_after_one_call(self, aws):
        aws.return_value = {'FunctionError': 'Unhandled'}
        with tempfile.TemporaryDirectory() as directory, self.assertRaises(RuntimeError):
            evaluate.main(['--run', '--minimum-similarity', '0.6', '--output', str(Path(directory)/'result.json')])
        self.assertEqual(1, aws.call_count)
