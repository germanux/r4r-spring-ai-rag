import copy
import unittest
import io
import json
import tempfile
import urllib.error
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from r4r_capture_evaluation import audit_text, retry_seconds, validate_capture, run_audit, collect, post_json, evaluation_summary
from r4r_evaluation_cases import select_cases, validate_response


class CaptureEvaluationTest(unittest.TestCase):
    def record(self):
        citation = dict(label="[S1]", source="en/guide.md", headingPath=["Heading"], ordinal=4)
        response = dict(answer="Documented advice [S1].", status="PARTIAL", abstained=False,
                        citations=[citation], provider=dict(name="local", model="qwen3:8b"))
        capture = dict(**{k: response[k] for k in ("answer", "status", "abstained", "citations", "provider")},
                       configuration=dict(minScore=.63), generationInvoked=True, generatedOutput="model output",
                       chunks=[dict(**citation, content="  Full source\n\nnot trimmed.\n", cited=True),
                               dict(label="[S2]", source="en/other.md", headingPath=[], ordinal=1,
                                    content="An uncited source.", cited=False)])
        return dict(question="A test question", language="en", httpResponse=response, capture=capture,
                    manualNote="DO NOT LEAK PRIOR REVIEW", expectedStatus="DO NOT LEAK EXPECTATION")

    def test_validates_full_context_including_uncited_chunk(self):
        record = self.record()
        validate_capture(record["httpResponse"], record["capture"])

    def test_rejects_wrong_ordinal_and_citation_flags(self):
        for field, value in (("ordinal", 99), ("cited", False), ("label", "[S9]")):
            record = copy.deepcopy(self.record())
            record["capture"]["chunks"][0][field] = value
            with self.assertRaises((ValueError, KeyError)):
                validate_capture(record["httpResponse"], record["capture"])

    def test_audit_contains_exact_untrimmed_text_and_no_previous_judgments(self):
        record = self.record()
        prompt = audit_text(record)
        for chunk in record["capture"]["chunks"]:
            self.assertIn(chunk["content"], prompt)
        self.assertIn("An uncited source.", prompt)
        self.assertNotIn(record["manualNote"], prompt)
        self.assertNotIn(record["expectedStatus"], prompt)
        self.assertNotIn("model output", prompt)

    def test_abstention_without_generation_has_no_fabricated_sources(self):
        record = self.record()
        record["httpResponse"].update(answer="", status="ABSTAINED", abstained=True, citations=[], provider=None)
        record["capture"].update(**record["httpResponse"], generationInvoked=False, generatedOutput=None, chunks=[])
        validate_capture(record["httpResponse"], record["capture"])
        self.assertIn("No se invocó al generador", audit_text(record))

    def test_numeric_retry_after_is_respected(self):
        self.assertEqual(12, retry_seconds("12"))
        self.assertEqual(2, retry_seconds("0"))

    def test_case_selection_reuses_all_seventy_and_stable_ids(self):
        self.assertEqual(70, len(select_cases(all_cases=True)))
        self.assertEqual(35, len(select_cases(all_cases=True, languages=('es',))))
        self.assertEqual(12, len(select_cases()))
        self.assertEqual(16, len(select_cases(include_controls=True)))
        selected = select_cases(case_ids='case-23')
        self.assertEqual([28, 28], [case['number'] for case in selected])
        self.assertEqual(['es', 'en'], [case['lang'] for case in selected])
        for kwargs in ({'case_ids': 'case-99'}, {'all_cases': True, 'case_ids': 'case-01'}, {'languages': ('fr',)}):
            with self.assertRaises(ValueError):
                select_cases(**kwargs)

    def test_status_expectations_are_not_relaxed_by_an_audit(self):
        partial = select_cases(case_ids='case-18', languages=('en',))[0]
        response = self.record()['httpResponse']
        response['answer'] = 'The documented advice applies [S1].'
        self.assertEqual([], validate_response(partial, response))
        self.assertTrue(validate_response(partial, {**response, 'status': 'ANSWERED'}))
        positive = select_cases(case_ids='case-01', languages=('en',))[0]
        self.assertTrue(validate_response(positive, response))

    def review(self, original='ANSWERED', recommended='PARTIAL', effective='PARTIAL', verdict='FAIL'):
        return dict(originalStatus=original, recommendedStatus=recommended, effectiveStatus=effective,
                    conclusive=True, note='A site check remains necessary.', metadata=dict(verdict=verdict,
                    reason='The source does not establish site feasibility.', provider='Ollama', model='qwen3:8b', durationMs=20))

    def fake_http(self, payload, status=200):
        response = io.BytesIO(json.dumps(payload).encode())
        response.status = status
        return response

    def test_actual_audit_metadata_is_saved_without_changing_rag(self):
        rag = dict(status='ANSWERED', answer='Original [S1].', auditId='12345678-1234-1234-1234-123456789abc')
        before = copy.deepcopy(rag)
        with patch('urllib.request.urlopen', return_value=self.fake_http(self.review())) as http:
            result = run_audit('http://localhost', rag)
        self.assertEqual('COMPLETED', result['outcome'])
        self.assertEqual('FAIL', result['verdict'])
        self.assertEqual('qwen3:8b', result['model'])
        self.assertTrue(result['proposedDowngrade'])
        self.assertTrue(result['appliedDowngrade'])
        self.assertEqual('PARTIAL', result['effectiveStatus'])
        self.assertEqual(rag, before)
        self.assertEqual(b'', http.call_args.args[0].data)

    def test_upgrades_are_recommendations_only_and_unsafe_server_upgrade_is_error(self):
        rag = dict(status='PARTIAL', auditId='12345678-1234-1234-1234-123456789abc')
        for effective, outcome in (('PARTIAL', 'COMPLETED'), ('ANSWERED', 'ERROR')):
            with patch('urllib.request.urlopen', return_value=self.fake_http(self.review('PARTIAL', 'ANSWERED', effective, 'PASS'))):
                result = run_audit('http://localhost', rag)
            self.assertEqual(outcome, result['outcome'])
            self.assertEqual('ANSWERED', result['recommendedStatus'])
            self.assertEqual('PARTIAL', result['effectiveStatus'])
            self.assertFalse(result['appliedDowngrade'])

    def test_missing_id_disabled_expired_and_audit_failures_do_not_raise(self):
        with patch('urllib.request.urlopen') as http:
            self.assertEqual('SKIPPED', run_audit('http://localhost', {'status': 'ABSTAINED'})['outcome'])
            http.assert_not_called()
        rag = dict(status='ANSWERED', auditId='12345678-1234-1234-1234-123456789abc')
        for error, expected in ((urllib.error.HTTPError('http://localhost', 404, '', {}, None), 'SKIPPED'),
                                (urllib.error.HTTPError('http://localhost', 503, '', {}, None), 'ERROR'),
                                (TimeoutError('private token'), 'ERROR')):
            with patch('urllib.request.urlopen', side_effect=error) as http:
                result = run_audit('http://localhost', rag)
            self.assertEqual(expected, result['outcome'])
            self.assertEqual('ANSWERED', result['effectiveStatus'])
            self.assertNotIn('private token', str(result))
            self.assertEqual(1, http.call_count)
        for response in (None, {'conclusive': False}, {**self.review(), 'metadata': None},
                         self.review('PARTIAL', 'PARTIAL', 'PARTIAL', 'PASS')):
            with patch('urllib.request.urlopen', return_value=self.fake_http(response)):
                self.assertEqual('ERROR', run_audit('http://localhost', rag)['outcome'])

    def test_429_waits_for_retry_after_and_does_not_repeat_generation(self):
        limited = urllib.error.HTTPError('http://localhost', 429, '', {'Retry-After': '7'}, None)
        with patch('urllib.request.urlopen', side_effect=[limited, self.fake_http(self.review())]) as http, patch('time.sleep') as sleep:
            result = run_audit('http://localhost', dict(status='ANSWERED', auditId='12345678-1234-1234-1234-123456789abc'))
        self.assertEqual('COMPLETED', result['outcome'])
        self.assertEqual(2, http.call_count)
        sleep.assert_called_once_with(7)

    def test_http_collection_keeps_rag_and_summary_when_one_audit_fails(self):
        tokens = ['00000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000002']
        queries = []
        def server(request, timeout):
            if request.full_url.endswith('/answers'):
                queries.append(json.loads(request.data))
                return self.fake_http(dict(status='ANSWERED', answer='The documented advice applies [S1].', abstained=False,
                         citations=[dict(label='[S1]', source='en/guide.md')], provider=dict(name='Ollama', model='qwen3:8b'),
                         auditId=tokens[len(queries) - 1]))
            if request.full_url.endswith(tokens[1]):
                raise urllib.error.HTTPError(request.full_url, 503, '', {}, None)
            return self.fake_http(self.review())
        args = SimpleNamespace(capture_dir=None, run_id='simulated', case_ids='case-01', all_cases=False,
                               languages='es,en', include_controls=False, allow_openai=False)
        env = dict(RAG_LIVE_TEST='true', RAG_LIVE_EN_TEST='true', RAG_OPENAI_ENABLED='false', RAG_TEST_BASE_URL='http://localhost')
        with tempfile.TemporaryDirectory() as directory, patch('r4r_capture_evaluation.ROOT', Path(directory)), \
                patch.dict('os.environ', env), patch('urllib.request.urlopen', side_effect=server), patch('time.sleep'), \
                patch('builtins.print'):
            collect(args)
            run = Path(directory) / 'docs/evaluation/runs/simulated'
            spanish = json.loads((run / 'es-01.json').read_text())
            english = json.loads((run / 'en-01.json').read_text())
            summary = json.loads((run / 'summary.json').read_text())
            self.assertEqual('ANSWERED', spanish['httpResponse']['status'])
            self.assertEqual('PARTIAL', spanish['audit']['effectiveStatus'])
            self.assertEqual('ERROR', english['audit']['outcome'])
            self.assertEqual('ANSWERED', english['httpResponse']['status'])
            self.assertEqual(1, summary['auditFailures'])
            self.assertEqual(1, summary['appliedDowngrades'])
            self.assertEqual({'PASS': 2}, summary['ragChecks'])
            for path in run.glob('*.json'):
                for token in tokens:
                    self.assertNotIn(token, path.read_text())
        self.assertEqual(['es', 'en'], [query['responseLanguage'] for query in queries])
        self.assertTrue(all('retrievalQuestion' not in query for query in queries))

    def test_all_seventy_cases_run_sequentially_with_simulated_http_only(self):
        cases = select_cases(all_cases=True)
        queries, audit_calls, statuses = [], [], {}
        def server(request, timeout):
            if request.full_url.endswith('/answers'):
                case = cases[len(queries)]
                query = json.loads(request.data)
                self.assertEqual(case['question'], query['question'])
                self.assertEqual(case['lang'], query['responseLanguage'])
                queries.append(query)
                status = case['expectedStatus']
                payload = dict(status=status, abstained=status == 'ABSTAINED',
                    answer='' if status == 'ABSTAINED' else 'The documented checks apply [S1].',
                    citations=[] if status == 'ABSTAINED' else [dict(label='[S1]', source='test-source.md')])
                if status != 'ABSTAINED':
                    token = f'00000000-0000-4000-8000-{len(queries):012x}'
                    payload['auditId'] = token
                    statuses[token] = status
                return self.fake_http(payload)
            token = request.full_url.rsplit('/', 1)[-1]
            audit_calls.append(token)
            status = statuses[token]
            return self.fake_http(self.review(status, status, status, 'PASS'))
        args = SimpleNamespace(capture_dir=None, run_id='simulated-70', case_ids='', all_cases=True,
                               languages='es,en', include_controls=False, allow_openai=False)
        env = dict(RAG_LIVE_TEST='true', RAG_LIVE_EN_TEST='true', RAG_OPENAI_ENABLED='false', RAG_TEST_BASE_URL='http://localhost')
        with tempfile.TemporaryDirectory() as directory, patch('r4r_capture_evaluation.ROOT', Path(directory)), \
                patch.dict('os.environ', env), patch('urllib.request.urlopen', side_effect=server), \
                patch('time.sleep') as sleep, patch('builtins.print'):
            collect(args)
            run = Path(directory) / 'docs/evaluation/runs/simulated-70'
            summary = json.loads((run / 'summary.json').read_text())
            self.assertEqual(70, summary['total'])
            self.assertEqual({'PASS': 70}, summary['ragChecks'])
            self.assertEqual(0, summary['auditFailures'])
            self.assertEqual(10, summary['auditSkipped'])
            self.assertEqual(71, len(list(run.glob('*.json'))))
        self.assertEqual(70, len(queries))
        self.assertEqual(60, len(audit_calls))
        self.assertEqual(69, sleep.call_count)
        self.assertTrue(all(call.args == (2,) for call in sleep.call_args_list))


if __name__ == "__main__":
    unittest.main()
