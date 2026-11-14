"""Read the same ES/EN JSON question banks as Java; no network or runtime side effects."""
import json
import re
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


def select_cases(case_ids='', languages=('es', 'en'), all_cases=False, include_controls=False):
    """Select by stable caseId; the default retains the existing 25–30 capture run."""
    languages = tuple(languages)
    if not languages or any(lang not in ('es', 'en') for lang in languages):
        raise ValueError('Languages must be es and/or en')
    if sum((bool(case_ids), all_cases, include_controls)) > 1:
        raise ValueError('Choose case IDs, all cases, or the default plus controls')
    cases = load_cases()
    ids = {value.strip() for value in case_ids.split(',') if value.strip()}
    if case_ids and (not ids or ids - {case['caseId'] for case in cases}):
        raise ValueError('Unknown/empty caseId selection')
    if ids:
        cases = [case for case in cases if case['caseId'] in ids]
    elif not all_cases:
        cases = [case for case in cases if 25 <= case['number'] <= 30 or include_controls and
                 (case['number'] == 17 or (case['lang'], case['number']) in (('es', 1), ('en', 2)))]
    return [case for case in cases if case['lang'] in languages]


def validate_response(case, payload):
    """Existing status/citation/language expectations; no semantic score inferred from an audit."""
    if not isinstance(payload, dict):
        return ['Expected a JSON object']
    status, answer, citations = payload.get('status'), payload.get('answer'), payload.get('citations')
    expected = case['expectedStatus']
    errors = []
    if status not in (('PARTIAL', 'ABSTAINED') if expected == 'PARTIAL' else (expected,)):
        errors.append('Unexpected response status: ' + str(status))
    if not isinstance(answer, str) or not isinstance(citations, list):
        return errors + ['Invalid answer/citations types']
    if status == 'ABSTAINED':
        if payload.get('abstained') is not True or answer.strip() or citations:
            errors.append('Abstention must have empty answer/citations')
        return errors
    if payload.get('abstained') is not False or not answer.strip() or not citations:
        errors.append('Non-abstention requires substantive answer and citations')
    labels = set()
    for citation in citations:
        if not isinstance(citation, dict) or not re.fullmatch(r'\[S\d+\]', str(citation.get('label', ''))) or not citation.get('source'):
            errors.append('Invalid citation metadata')
        else:
            labels.add(citation['label'])
    mentioned = set(re.findall(r'\[S\d+\]', answer))
    if not mentioned or not mentioned.issubset(labels) or re.fullmatch(r'(?:\[S\d+\][\s,;:.]*)+', answer.strip()):
        errors.append('Missing/unresolved or citation-only answer')
    if case['lang'] == 'en' and not re.search(r'\b(the|a|an|is|are|you|your|to|of|for|with|can|will|may|should|it|and|or|in|on|before|depending|please)\b', answer, re.I):
        errors.append('English language check failed')
    return errors
