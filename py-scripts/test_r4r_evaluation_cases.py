"""Offline contract checks for the shared ES/EN evaluation bank."""
import json
import tempfile
import unittest
from pathlib import Path

from r4r_evaluation_cases import ROOT, load_cases


class EvaluationCasesTest(unittest.TestCase):
    def test_loads_35_aligned_cases_with_the_clean_evidence_contract(self):
        cases = load_cases()
        self.assertEqual(len(cases), 70)
        for language in ('es', 'en'):
            rows = [row for row in cases if row['lang'] == language]
            self.assertEqual([row['number'] for row in rows], list(range(1, 36)))
            bank = json.loads((ROOT / 'src/test/resources/evaluation' / f'questions-{language}.json').read_text())
            self.assertEqual([row['evidence'] for row in rows], [True] * 24 + [False] * 11)
            self.assertEqual([row['expectedStatus'] for row in rows], ['ANSWERED'] * 24 + ['PARTIAL'] * 6 + ['ABSTAINED'] * 5)
            self.assertEqual(len({row['caseId'] for row in bank['cases']}), 35)
            self.assertEqual([row['caseId'] for row in rows], [row['caseId'] for row in bank['cases']])
            by_id = {row['caseId']: row for row in bank['cases']}
            first, second, third = [by_id[f'case-{n}']['question'] for n in (31, 32, 33)]
            self.assertTrue(second.startswith(first + '\n'))
            self.assertTrue(third.startswith(second + '\n'))
            self.assertEqual(len(second.splitlines()), 2)
            self.assertEqual(len(third.splitlines()), 3)
        spanish = [row for row in cases if row['lang'] == 'es']
        english = [row for row in cases if row['lang'] == 'en']
        self.assertEqual([row['retrievalQuestion'] for row in english],
                         [row['question'] for row in spanish])

    def test_rejects_incomplete_bank(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            destination = root / 'src/test/resources/evaluation'
            destination.mkdir(parents=True)
            for language in ('es', 'en'):
                bank = json.loads((ROOT / 'src/test/resources/evaluation' / f'questions-{language}.json').read_text())
                bank['cases'] = bank['cases'][:30]
                (destination / f'questions-{language}.json').write_text(json.dumps(bank))
            with self.assertRaisesRegex(ValueError, 'Expected 35 canonical questions'):
                load_cases(root)


if __name__ == '__main__':
    unittest.main()
