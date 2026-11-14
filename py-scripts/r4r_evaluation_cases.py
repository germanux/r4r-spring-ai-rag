"""Read the same ES/EN JSON question banks as Java; no network or runtime side effects."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def load_cases(root=ROOT):
    directory = root / 'src/test/resources/evaluation'
    spanish = json.loads((directory / 'questions-es.json').read_text())['cases']
    retrieval = {row['caseId']: row['question'] for row in spanish}
    result = []
    for lang in ['es', 'en']:
        filename = 'questions-' + lang + '.json'
        entries = json.loads((directory / filename).read_text())['cases']
        if len(entries) != 35:
            raise ValueError('Expected 35 canonical questions in ' + filename)
        for number, row in enumerate(entries, 1):
            if row['number'] != number or row['caseId'] != spanish[number - 1]['caseId']:
                raise ValueError('Mismatched bilingual order in ' + filename)
            result.append(dict(source=filename, caseId=row['caseId'], number=number, name=row['name'], question=row['question'],
                               retrievalQuestion=retrieval[row['caseId']], lang=lang, evidence=row['evidence'], expectedStatus=row['expectedStatus']))
    return result
