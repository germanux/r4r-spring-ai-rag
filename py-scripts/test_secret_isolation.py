"""Local security gate: public configuration, synthetic parent secret, no external JDBC or live IT."""
import os
from pathlib import Path
import subprocess
import time
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


class SecretIsolationTest(unittest.TestCase):
    def test_unit_suite_and_local_http_do_not_disclose_parent_secret(self):
        fixture = ROOT / 'target' / 'local-security-fixture'
        fixture.mkdir(parents=True, exist_ok=True)
        marker = 'R4R_TEST_SECRET_SENTINEL_123'
        env = {key: value for key, value in os.environ.items()
               if key in ('PATH', 'HOME', 'LANG', 'JAVA_HOME', 'TMPDIR')}
        env['POSTGRES_PASSWORD'] = marker
        env['AWS_EC2_METADATA_DISABLED'] = 'true'
        env['RAG_VECTOR_FAILOVER_ENABLED'] = 'true'
        env['RAG_S3_VECTORS_ENABLED'] = 'true'
        env['OPENAI_API_KEY'] = marker
        env['AWS_ACCESS_KEY_ID'] = 'SYNTHETIC_TEST_ACCESS'
        env['AWS_SECRET_ACCESS_KEY'] = marker
        for flag in ('RAG_LIVE_TEST', 'RAG_LIVE_EN_TEST', 'RAG_LUNA_LIVE_TEST', 'RAG_WARMUP_LIVE_TEST'):
            env[flag] = 'true'  # Legacy flags cannot bypass explicit Maven profile selection.
        # Exercise the default Maven lifecycle; no hidden test selection or JDBC/Flyway overrides.
        command = ['mvn', '-B', 'verify']
        started = time.time_ns()
        result = subprocess.run(command, cwd=ROOT, env=env, capture_output=True, text=True, timeout=600)
        leaked = marker in result.stdout or marker in result.stderr
        # Inspect only reports produced by this invocation, never stale live artifacts or private files.
        reports = list((ROOT / 'target/surefire-reports').glob('*PublicRagEnvironmentTest*'))
        reports += list((ROOT / 'target/surefire-reports').glob('TEST-*.xml'))
        reports += list((ROOT / 'target/failsafe-reports').glob('TEST-*.xml'))
        fresh = [report for report in set(reports) if report.stat().st_mtime_ns >= started]
        for report in fresh:
            text = report.read_text(errors='replace')
            if marker in text:
                leaked = True
                report.write_text(text.replace(marker, '[SYNTHETIC SECRET REDACTED]'))
        (fixture / 'maven.log').write_text((result.stdout + result.stderr).replace(marker, '[SYNTHETIC SECRET REDACTED]'))
        self.assertFalse(leaked, 'Synthetic parent secret appeared in test output or reports')
        self.assertEqual(result.returncode, 0, 'Local Maven gate failed; see target/local-security-fixture/maven.log')
        self.assertTrue(any('PublicRagEnvironmentTest' in report.name for report in fresh),
                        'The configuration fixture did not produce fresh reports')
        self.assertTrue(any('RagStreamHttpIT' in report.name for report in fresh),
                        'The local HTTP integration tests did not produce fresh reports')
        self.assertTrue(any('VectorDegradedHttpIT' in report.name for report in fresh),
                        'The degraded startup integration tests did not produce fresh reports')
        self.assertTrue(any('KnowledgeIngestionCliProcessIT' in report.name for report in fresh),
                        'The child CLI integration tests did not produce fresh reports')
        for name in ('PostgresBaselineIT', 'KnowledgeIngestionServiceIT', 'PgVectorKnowledgeStoreIT',
                     'PgVectorKnowledgeStoreTransactionalRollbackIT', 'S3VectorsReadOnlyIT', 'LunaLiveIT',
                     'RagLiveQuestionsIT', 'RagLiveEnglishQuestionsIT', 'OllamaEmbeddingWarmupLiveIT'):
            selected = [report for report in fresh if report.name.startswith('TEST-')
                        and report.name.endswith('.' + name + '.xml')]
            self.assertEqual(len(selected), 1, name + ' must produce an explicit NOT RUN report')
            suite = ET.parse(selected[0]).getroot()
            self.assertEqual(suite.attrib['tests'], suite.attrib['skipped'], name + ' must remain opt-in')
            self.assertTrue(all('NOT RUN:' in node.attrib.get('message', '')
                                for node in suite.findall('testcase/skipped')), name)
        self.assertNotIn('not eligible for getting processed by all BeanPostProcessors', result.stdout,
                         'Spring infrastructure was instantiated before post-processor registration completed')


if __name__ == '__main__':
    unittest.main()
