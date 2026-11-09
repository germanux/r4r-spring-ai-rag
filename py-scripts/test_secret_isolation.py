"""Local security gate: public configuration, synthetic parent secret, no external JDBC or live IT."""
import os
from pathlib import Path
import subprocess
import time
import unittest

ROOT = Path(__file__).resolve().parents[1]


class SecretIsolationTest(unittest.TestCase):
    def test_unit_suite_and_local_http_do_not_disclose_parent_secret(self):
        fixture = ROOT / 'target' / 'local-security-fixture'
        fixture.mkdir(parents=True, exist_ok=True)
        source = (ROOT / 'src/main/resources/application.yml').read_text()
        public = source.replace('      - optional:file:./docker-spring/.env[.properties]\n', '')
        self.assertNotIn('./docker-spring/.env[', public, 'Private import must be removed before running tests')
        public = public.replace('optional:file:./docker-spring/.env.base[.properties]',
                                (ROOT / 'docker-spring/.env.base').as_uri() + '[.properties]')
        (fixture / 'application.yml').write_text(public)
        (fixture / 'application-test.yml').write_text((ROOT / 'src/test/resources/application-test.yml').read_text())
        marker = 'R4R_TEST_SECRET_SENTINEL_123'
        env = {key: value for key, value in os.environ.items()
               if key in ('PATH', 'HOME', 'LANG', 'JAVA_HOME', 'TMPDIR')}
        env['POSTGRES_PASSWORD'] = marker
        env['AWS_EC2_METADATA_DISABLED'] = 'true'
        env['RAG_VECTOR_FAILOVER_ENABLED'] = 'true'
        env['RAG_S3_VECTORS_ENABLED'] = 'true'
        command = ['mvn', '-B', 'verify', '-Dit.test=RagStreamHttpIT,VectorDegradedHttpIT,KnowledgeIngestionCliProcessIT',
                   '-Dspring.config.location=' + (fixture / 'application.yml').as_uri(),
                   '-Dspring.flyway.enabled=false', '-Dspring.sql.init.mode=never',
                   '-Dspring.ai.vectorstore.pgvector.initialize-schema=false',
                   '-Dspring.datasource.driver-class-name=com.riansares.r4r.config.TestDatabaseGuard$NoExternalJdbcDriver']
        started = time.time_ns()
        result = subprocess.run(command, cwd=ROOT, env=env, capture_output=True, text=True, timeout=600)
        leaked = marker in result.stdout or marker in result.stderr
        # Inspect only reports produced by this invocation, never stale live artifacts or private files.
        reports = list((ROOT / 'target/surefire-reports').glob('*PublicRagEnvironmentTest*'))
        reports += list((ROOT / 'target/surefire-reports').glob('TEST-*.xml'))
        reports += list((ROOT / 'target/failsafe-reports').glob('*RagStreamHttpIT*'))
        reports += list((ROOT / 'target/failsafe-reports').glob('*VectorDegradedHttpIT*'))
        reports += list((ROOT / 'target/failsafe-reports').glob('*KnowledgeIngestionCliProcessIT*'))
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
        self.assertNotIn('not eligible for getting processed by all BeanPostProcessors', result.stdout,
                         'Spring infrastructure was instantiated before post-processor registration completed')


if __name__ == '__main__':
    unittest.main()
