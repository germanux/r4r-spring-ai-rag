#!/usr/bin/env python3
"""Update only the existing private Lambda's code. Never provisions resources or changes IAM/concurrency."""
import argparse
import base64
import hashlib
from pathlib import Path
import time
from aws_cli import aws

NAME = 'r4r-titan-retrieval'
ROLE = 'arn:aws:iam::124021120482:role/r4r-lambda-execution'
QUERY = '{FunctionName:FunctionName,Runtime:Runtime,Role:Role,Handler:Handler,Timeout:Timeout,MemorySize:MemorySize,Architectures:Architectures,VpcConfig:VpcConfig,State:State,LastUpdateStatus:LastUpdateStatus,CodeSha256:CodeSha256,RevisionId:RevisionId}'


def state():
    return aws('lambda', 'get-function-configuration', '--function-name', NAME, '--query', QUERY)


def update(jar):
    if aws('sts', 'get-caller-identity')['Arn'] != 'arn:aws:iam::124021120482:user/r4r-codex-deployer':
        raise RuntimeError('Unexpected deployment identity')
    current = state()
    if (current.get('Runtime'), current.get('Role'), current.get('Handler'), current.get('Timeout'),
        current.get('MemorySize'), current.get('Architectures')) != (
            'java21', ROLE, 'com.riansares.r4r.titan.RetrievalHandler::handleRequest', 30, 1024, ['x86_64']) \
            or (current.get('VpcConfig') or {}).get('VpcId'):
        raise RuntimeError('Unexpected existing Lambda configuration')
    digest = base64.b64encode(hashlib.sha256(jar.read_bytes()).digest()).decode()
    if current['CodeSha256'] != digest:
        aws('lambda', 'update-function-code', '--function-name', NAME, '--revision-id', current['RevisionId'],
            '--zip-file', 'fileb://' + str(jar), '--query', '{CodeSha256:CodeSha256,LastUpdateStatus:LastUpdateStatus}')
        for _ in range(25):
            current = state()
            if current.get('State') == 'Failed' or current.get('LastUpdateStatus') == 'Failed':
                raise RuntimeError('Lambda update failed')
            if current.get('State') == 'Active' and current.get('LastUpdateStatus') == 'Successful':
                break
            time.sleep(2)
        else:
            raise RuntimeError('Lambda update timed out; inspect status before retrying')
    if current.get('CodeSha256') != digest:
        raise RuntimeError('Deployed artifact digest mismatch')
    print('Verified existing Lambda code: ' + digest + '; shared concurrency unchanged')


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--update', action='store_true', help='Explicitly update the existing Lambda code')
    args = parser.parse_args(argv)
    jar = Path(__file__).resolve().parent / 'target/r4r-titan-retrieval-1.0.0.jar'
    if not jar.is_file():
        parser.error('Run mvn -f lambda-titan/pom.xml clean verify first')
    if args.update:
        update(jar)
    else:
        print('Offline preview; use --update to update only ' + NAME)


if __name__ == '__main__':
    main()
