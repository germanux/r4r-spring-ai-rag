#!/usr/bin/env python3
"""Optional bounded real ES/EN evaluation. Run only after successful ingestion."""
import json
import tempfile
from pathlib import Path
from corpus import aws

CASES = [
    ('es-budget','¿Qué información hace falta para preparar un presupuesto de reforma?',True),
    ('en-budget','What information do you need to prepare a renovation quote?',True),
    ('es-damp','¿Cómo abordáis las humedades en una vivienda?',True),
    ('en-damp','How do you deal with damp in a home?',True),
    ('es-area','¿Trabajáis en Ocaña y Tembleque?',True),
    ('en-area','Do you serve Ocaña and Tembleque?',True),
    ('es-bath','¿Qué tenéis en cuenta al reformar un baño?',True),
    ('en-bath','What do you consider when renovating a bathroom?',True),
    ('es-absent','¿Cuál es la dosis de insulina para tratar la diabetes?',False),
    ('en-absent','What insulin dosage should I use for diabetes?',False),
    ('es-space','¿Cuál es el calendario de lanzamientos espaciales de 2030?',False),
    ('en-space','What is the space launch schedule for 2030?',False),
]


def main():
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument('--run',action='store_true')
    parser.add_argument('--output',type=Path,default=Path('/tmp/r4r-titan-evaluation.json'))
    args = parser.parse_args()
    if not args.run:
        print('Offline preview: 12 cases, 8 in-domain, 4 abstention; use --run after ingestion')
        return
    assert len(CASES) <= 50
    rows = []
    with tempfile.TemporaryDirectory() as directory:
        for label, question, answerable in CASES:
            output = Path(directory) / 'response.json'
            result = aws('lambda','invoke','--function-name','r4r-titan-retrieval','--payload',json.dumps({'question':question,'topK':5}),
                         '--cli-binary-format','raw-in-base64-out',str(output))
            if result.get('FunctionError'):
                raise RuntimeError('Lambda evaluation failed at ' + label + '; stop, inspect CloudWatch')
            response = json.loads(output.read_text())
            rows.append(dict(case=label,expectedAnswerable=answerable,**response))
            args.output.write_text(json.dumps(rows,ensure_ascii=False,indent=2))
    print('Saved 12 responses; human relevance/citation review required before selecting Titan threshold')

if __name__ == '__main__':
    main()
