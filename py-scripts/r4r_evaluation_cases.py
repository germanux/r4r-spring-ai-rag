"""Read the canonical Java ES/EN evaluation questions; no network or runtime side effects."""
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
STRING = r'"((?:\\.|[^"\\])*)"'
ENTRY = re.compile(r'new QuestionCase\(' + STRING + r', ' + STRING + r'(?:, ' + STRING + r')?, (true|false)\)')


def load_cases(root=ROOT):
    result = []
    for lang, filename in [('es', 'RagLiveQuestionsIT.java'), ('en', 'RagLiveEnglishQuestionsIT.java')]:
        text = (root / 'src/test/java/com/riansares/r4r/llm_tests' / filename).read_text()
        block = text.split('CASES = List.of(', 1)[1].split('\n    );', 1)[0]
        entries = ENTRY.findall(block)
        if len(entries) != 15:
            raise ValueError('Expected 15 canonical questions in ' + filename)
        for number, (name, question, retrieval, evidence) in enumerate(entries, 1):
            decode = lambda value: json.loads('"' + value + '"')
            result.append(dict(source=filename, number=number, name=decode(name), question=decode(question),
                               retrievalQuestion=decode(retrieval) if retrieval else decode(question),
                               lang=lang, evidence=evidence == 'true'))
    return result
