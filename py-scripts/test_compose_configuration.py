"""Offline Compose validation: public inputs and synthetic overrides in an isolated tree only."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1]


class ComposeConfigurationTest(unittest.TestCase):
    def test_allowlists_precedence_and_independent_startup(self):
        fixture = ROOT / 'target' / 'compose-config-fixture'
        fixture.mkdir(parents=True, exist_ok=True)
        for name in ('.env.base', 'docker-compose.yml', 'compose-services.yml'):
            shutil.copyfile(ROOT / 'docker-spring' / name, fixture / name)
        overrides = fixture / 'synthetic-overrides.properties'
        overrides.write_text('POSTGRES_PASSWORD=fake-db\nOPENAI_API_KEY=fake-openai\n'
                             'TUNNEL_TOKEN=fake-tunnel\nAWS_ACCESS_KEY_ID=fake-access\n'
                             'AWS_SECRET_ACCESS_KEY=fake-secret\n'
                             'RAG_GENERATION_PROVIDERS=http://fake-host:11434|test|7s\n')
        # Remove inherited interpolation/control variables; never load the actual project .env.
        env = {k: v for k, v in os.environ.items()
               if k in ('PATH', 'HOME', 'LANG', 'DOCKER_CONFIG')}
        command = ['docker', 'compose', '--env-file', '.env.base', '--env-file', overrides.name,
                   'config', '--no-env-resolution', '--format', 'json']
        result = subprocess.run(command, cwd=fixture, env=env, capture_output=True, text=True, check=True)
        services = json.loads(result.stdout)['services']
        for name in ('postgres', 'postgres-test'):
            self.assertEqual(set(services[name]['environment']),
                             {'POSTGRES_DB', 'POSTGRES_USER', 'POSTGRES_PASSWORD'})
            self.assertEqual(services[name]['environment']['POSTGRES_PASSWORD'], 'fake-db')
        self.assertEqual(services['postgres-test']['environment']['POSTGRES_DB'], 'r4r_rag_test')
        self.assertEqual(services['cloudflared']['environment'], {'TUNNEL_TOKEN': 'fake-tunnel'})
        self.assertEqual(set(services['ollama-embeddings']['environment']),
                         {'OLLAMA_HOST', 'OLLAMA_NUM_PARALLEL', 'OLLAMA_MAX_LOADED_MODELS',
                          'OLLAMA_KEEP_ALIVE', 'RAG_EMBEDDING_MODEL'})
        api = services['api']
        self.assertNotIn('ollama-embeddings', api.get('depends_on', {}))
        self.assertNotIn('postgres', api.get('depends_on', {}))
        self.assertNotIn('TUNNEL_TOKEN', api['environment'])
        self.assertNotIn('POSTGRES_TEST_USER', api['environment'])
        self.assertEqual(api['environment']['OPENAI_API_KEY'], 'fake-openai')
        self.assertEqual(api['environment']['RAG_GENERATION_PROVIDERS'], 'http://fake-host:11434|test|7s')
        for service in services.values():
            self.assertNotIn('env_file', service)


if __name__ == '__main__':
    unittest.main()
