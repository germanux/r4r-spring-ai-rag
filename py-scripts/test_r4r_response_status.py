"""Offline contract checks; never start the live validation controller."""
import unittest
from r4r_evaluation_cases import load_cases
from r4r_live_failover_validation import validate


class ResponseStatusTest(unittest.TestCase):
    def setUp(self):
        cases = load_cases()
        self.positive = next(c for c in cases if c['lang'] == 'en' and c['number'] == 1)
        self.partial = next(c for c in cases if c['lang'] == 'en' and c['number'] == 25)
        self.answer = dict(status='PARTIAL', abstained=False, answer='The documented checks apply [S1].', citations=[
            dict(label='[S1]', source='en/guides/R4R_83_OLD_HOMES_AND_REHABILITATION.md')])

    def test_positive_cases_cannot_pass_as_partial(self):
        self.assertTrue(validate(self.positive, self.answer))

    def test_partial_cases_accept_supported_partial_or_empty_abstention(self):
        self.assertEqual(validate(self.partial, self.answer), [])
        self.assertEqual(validate(self.partial, dict(status='ABSTAINED', abstained=True, answer='', citations=[])), [])
        self.assertTrue(validate(self.partial, {**self.answer, 'status': 'ANSWERED'}))

    def test_abstention_invariants_are_not_bypassed_by_the_status_label(self):
        self.assertTrue(validate(self.partial, {**self.answer, 'status': 'ABSTAINED', 'abstained': True}))


if __name__ == '__main__':
    unittest.main()
