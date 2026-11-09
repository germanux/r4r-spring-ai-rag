#!/usr/bin/env python3
"""Idempotent Titan ingestion. Standard library only; explicit AWS CLI profile/region."""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import subprocess
import tempfile

BUCKET = 'amzn-r4r-vectors'
INDEX = 'r4r-titan-v2-1024'
MODEL = 'amazon.titan-embed-text-v2:0'


def aws(*args):
    # AWS CLI standard retry mode: at most 3 total attempts, no retry on AccessDenied.
    env = dict(os.environ, AWS_RETRY_MODE='standard', AWS_MAX_ATTEMPTS='3')
    result = subprocess.run(['aws', *args, '--profile', 'r4r-codex', '--region', 'eu-north-1'],
                            capture_output=True, text=True, env=env)
    if result.returncode:
        raise RuntimeError(result.stderr.strip())
    return json.loads(result.stdout) if result.stdout.strip() else {}


def normalize(metadata):
    result = dict(metadata)
    for key in ('source', 'source_text'):
        if not isinstance(result.get(key), str) or not result[key].strip():
            raise ValueError('Missing ' + key)
    if type(result.get('ordinal')) is not int or result['ordinal'] < 0:
        raise ValueError('Invalid ordinal')
    headings = result.get('headingPath', [])
    if not isinstance(headings, list) or any(not isinstance(v, str) for v in headings):
        raise ValueError('Invalid headingPath')
    # Preserve valid provenance, omit unsupported nulls and empty lists.
    result = {k: v for k, v in result.items() if v is not None and v != []}
    for value in result.values():
        if isinstance(value, (dict, list)) and (not isinstance(value, list) or any(not isinstance(v, str) for v in value)):
            raise ValueError('Unsupported metadata type')
    if len(json.dumps(result, ensure_ascii=False).encode()) > 40 * 1024:
        raise ValueError('Metadata exceeds budget')
    if len(json.dumps({k:v for k,v in result.items() if k != 'source_text'}, ensure_ascii=False).encode()) > 2048:
        raise ValueError('Filterable metadata exceeds budget')
    return result


def fingerprint(metadata):
    return hashlib.sha256(json.dumps(metadata, sort_keys=True, ensure_ascii=False, separators=(',', ':')).encode()).hexdigest()


def validate_vector(values):
    if not isinstance(values, list) or len(values) != 1024 or any(type(v) not in (int, float) or not math.isfinite(v) for v in values):
        raise ValueError('Invalid Titan embedding')
    if sum(v*v for v in values) == 0:
        raise ValueError('Zero vector')
    return values


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--source', type=Path, default=Path(__file__).resolve().parent.parent / 'knowledge/struct-data/r4r-s3vectors-import.json')
    parser.add_argument('--ingest', action='store_true', help='Explicitly enable AWS writes and Titan charges')
    args = parser.parse_args()
    vectors = json.loads(args.source.read_text())['vectors']
    records = {v['key']: normalize(v['metadata']) for v in vectors}
    if len(records) != len(vectors) or any(not k for k in records):
        raise ValueError('Duplicate/missing keys')
    manifest = fingerprint(records)
    print(json.dumps({'documents': len(records), 'manifestSha256': manifest, 'ingest': args.ingest}))
    if not args.ingest:
        return
    identity = aws('sts', 'get-caller-identity')
    if identity['Arn'] != 'arn:aws:iam::124021120482:user/r4r-codex-deployer':
        raise ValueError('Unexpected identity')
    config = aws('s3vectors', 'get-index', '--vector-bucket-name', BUCKET, '--index-name', INDEX)['index']
    if (config['dimension'],config['dataType'],config['distanceMetric']) != (1024,'float32','cosine') or 'source_text' not in config.get('metadataConfiguration',{}).get('nonFilterableMetadataKeys',[]):
        raise ValueError('Unexpected target index')
    existing = aws('s3vectors', 'list-vectors', '--vector-bucket-name', BUCKET, '--index-name', INDEX, '--return-metadata')['vectors']
    # CLI automatically paginates. Unexpected remote keys require manual reconciliation; never delete.
    if set(v['key'] for v in existing) - records.keys():
        raise ValueError('Unexpected target keys; no deletion authorized')
    ready = {v['key']: v.get('metadata', {}) for v in existing}
    with tempfile.TemporaryDirectory(prefix='r4r-titan-') as directory:
        folder = Path(directory)
        for key, metadata in records.items():
            tagged = dict(metadata, titan_content_sha256=fingerprint(metadata), embedding_model=MODEL)
            if ready.get(key) == tagged:
                continue  # Successful remote writes are the durable checkpoint, including after local crashes.
            body = folder / 'request.json'
            body.write_text(json.dumps({'inputText': metadata['source_text'], 'dimensions':1024, 'normalize':True}))
            response = folder / 'embedding.json'
            aws('bedrock-runtime', 'invoke-model', '--model-id', MODEL, '--body', 'fileb://' + str(body), str(response))
            data = validate_vector(json.loads(response.read_text())['embedding'])
            payload = folder / 'vectors.json'
            payload.write_text(json.dumps([{'key':key,'metadata':tagged,'data':{'float32':data}}]))
            aws('s3vectors','put-vectors','--vector-bucket-name',BUCKET,'--index-name',INDEX,'--vectors','file://' + str(payload))
            # Read-after-write verifies the full vector and metadata, not a local success marker.
            actual = aws('s3vectors','get-vectors','--vector-bucket-name',BUCKET,'--index-name',INDEX,'--keys',key,'--return-data','--return-metadata')['vectors']
            if len(actual) != 1 or actual[0]['metadata'] != tagged:
                raise ValueError('Write verification failed')
            import struct
            expected = [struct.unpack('f',struct.pack('f',v))[0] for v in data]
            received = validate_vector(actual[0]['data']['float32'])
            if any(abs(a-b) > 1e-7 for a,b in zip(expected, received)):
                raise ValueError('Vector verification failed')
            print(json.dumps({'verifiedKey':key}))
    final = aws('s3vectors','list-vectors','--vector-bucket-name',BUCKET,'--index-name',INDEX,'--return-metadata')['vectors']
    if {v['key']:v['metadata'] for v in final} != {key:dict(m,titan_content_sha256=fingerprint(m),embedding_model=MODEL) for key,m in records.items()}:
        raise ValueError('Final corpus mismatch')
    print(json.dumps({'verifiedDocuments':len(final),'manifestSha256':manifest}))

if __name__ == '__main__':
    main()
