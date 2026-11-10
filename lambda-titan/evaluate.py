#!/usr/bin/env python3
"""Optional real Lambda retrieval evaluation using the existing 30 Java ES/EN cases."""
import argparse
import json
from pathlib import Path
import sys
import tempfile
import time
from aws_cli import aws

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'py-scripts'))
from r4r_evaluation_cases import load_cases


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run', action='store_true')
    parser.add_argument('--minimum-similarity', type=float)
    parser.add_argument('--output', type=Path, default=Path('/tmp/r4r-titan-evaluation.json'))
    args = parser.parse_args(argv)
    cases = load_cases()
    if not args.run:
        print(f'Offline preview: {len(cases)} canonical ES/EN questions; no AWS calls')
        return
    if args.minimum_similarity is None or not 0 <= args.minimum_similarity <= 1:
        parser.error('Supply an explicitly selected Titan --minimum-similarity in [0,1]')
    rows = []
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as directory:
        for case in cases:
            output = Path(directory) / 'response.json'
            start = time.monotonic()
            result = aws('lambda', 'invoke', '--function-name', 'r4r-titan-retrieval', '--invocation-type', 'RequestResponse',
                         '--payload', json.dumps({'question': case['question'], 'topK': 5}),
                         '--cli-binary-format', 'raw-in-base64-out', str(output))
            invoke_ms = (time.monotonic() - start) * 1000
            if result.get('FunctionError'):
                raise RuntimeError('Lambda evaluation failed; no further questions invoked')
            response = json.loads(output.read_text())
            selected = [doc for doc in response['documents'] if doc['score'] >= args.minimum_similarity]
            rows.append(dict(case=case, response=response, selectedDocuments=selected,
                             abstained=not selected, evidenceExpectationMatched=bool(selected) == case['evidence'],
                             lambdaInvokeMs=invoke_ms, measurement='real AWS; CLI invocation includes local CLI startup'))
            args.output.write_text(json.dumps(rows, ensure_ascii=False, indent=2))
    print(f'Saved {len(rows)} real retrieval responses; inspect relevance/provenance and abstention. Generation is evaluated by existing backend LLM tests.')


if __name__ == '__main__':
    main()
