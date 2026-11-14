import copy
import unittest

from r4r_capture_evaluation import audit_text, retry_seconds, validate_capture


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


if __name__ == "__main__":
    unittest.main()
