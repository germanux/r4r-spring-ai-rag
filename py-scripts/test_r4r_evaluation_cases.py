"""Offline contract checks for the shared ES/EN evaluation bank."""
import json
import tempfile
import unittest
from pathlib import Path

from r4r_evaluation_cases import ROOT, load_cases


class EvaluationCasesTest(unittest.TestCase):
    def test_loads_35_cases_per_language_without_changing_historical_banks(self):
        cases = load_cases()
        self.assertEqual(len(cases), 70)
        for language in ('es', 'en'):
            rows = [row for row in cases if row['lang'] == language]
            self.assertEqual([row['number'] for row in rows], list(range(1, 36)))
            self.assertEqual(rows[31]['question'].splitlines()[0], rows[30]['question'])
            self.assertTrue(rows[32]['question'].startswith(rows[31]['question'] + '\n'))
            bank = json.loads((ROOT / 'src/test/resources/evaluation' / f'questions-{language}.json').read_text())
            self.assertEqual(len(bank['history']['generalization']), 30)
            self.assertEqual(len(bank['history']['nomic']), 15)
            self.assertEqual([row['caseId'] for row in bank['cases'][30:]],
                             [f'case-{n}' for n in range(31, 36)])
            self.assertTrue(all(row['evidence'] for row in bank['cases'][30:]))
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
