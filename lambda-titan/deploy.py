#!/usr/bin/env python3
"""Repeatable private deployment; stops on denial and never edits Nomic or IAM."""
from pathlib import Path
import json
import time
from corpus import aws, BUCKET, INDEX

NAME = 'r4r-titan-retrieval'
ROLE = 'arn:aws:iam::124021120482:role/r4r-lambda-execution'


def optional(*args):
    try:
        return aws(*args)
    except RuntimeError as failure:
        if 'ResourceNotFoundException' not in str(failure) and 'NotFoundException' not in str(failure):
            raise
        return None


def wait():
    for _ in range(30):
        state = aws('lambda','get-function-configuration','--function-name',NAME)
        if state.get('State') == 'Failed' or state.get('LastUpdateStatus') == 'Failed':
            raise RuntimeError('Lambda deployment failed')
        if state.get('State') == 'Active' and state.get('LastUpdateStatus','Successful') == 'Successful':
            return
        time.sleep(2)
    raise RuntimeError('Lambda deployment timed out')


def main():
    if aws('sts','get-caller-identity')['Arn'] != 'arn:aws:iam::124021120482:user/r4r-codex-deployer':
        raise RuntimeError('Unexpected deployment identity')
    jar = Path(__file__).resolve().parent / 'target/r4r-titan-retrieval-1.0.0.jar'
    if not jar.is_file():
        raise RuntimeError('Run mvn -f lambda-titan/pom.xml clean verify first')
    index = optional('s3vectors','get-index','--vector-bucket-name',BUCKET,'--index-name',INDEX)
    if index is None:
        aws('s3vectors','create-index','--vector-bucket-name',BUCKET,'--index-name',INDEX,
            '--data-type','float32','--dimension','1024','--distance-metric','cosine',
            '--metadata-configuration',json.dumps({'nonFilterableMetadataKeys':['source_text']}))
    else:
        config = index['index']
        if (config['dimension'],config['dataType'],config['distanceMetric']) != (1024,'float32','cosine') or config.get('metadataConfiguration',{}).get('nonFilterableMetadataKeys') != ['source_text']:
            raise RuntimeError('Unexpected existing target index')
    try:
        aws('logs','create-log-group','--log-group-name','/aws/lambda/' + NAME)
    except RuntimeError as failure:
        if 'ResourceAlreadyExistsException' not in str(failure):
            raise
    aws('logs','put-retention-policy','--log-group-name','/aws/lambda/' + NAME,'--retention-in-days','7')
    current = optional('lambda','get-function-configuration','--function-name',NAME)
    config = ['--function-name',NAME,'--runtime','java21','--role',ROLE,
              '--handler','com.riansares.r4r.titan.RetrievalHandler::handleRequest','--timeout','30','--memory-size','1024']
    if current is None:
        aws('lambda','create-function',*config,'--architectures','x86_64','--zip-file','fileb://' + str(jar))
        wait()
    else:
        if current.get('VpcConfig',{}).get('VpcId') or current.get('Architectures') != ['x86_64']:
            raise RuntimeError('Unexpected existing Lambda architecture/VPC')
        aws('lambda','update-function-code','--function-name',NAME,'--revision-id',current['RevisionId'],'--zip-file','fileb://' + str(jar))
        wait()
        aws('lambda','update-function-configuration',*config)
        wait()
    aws('lambda','put-function-concurrency','--function-name',NAME,'--reserved-concurrent-executions','2')
    print('Private deployment complete; no public URL or production change')

if __name__ == '__main__':
    main()
