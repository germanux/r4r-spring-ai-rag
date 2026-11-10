#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
aws_r4r() { aws "$@" --profile r4r-codex --region eu-north-1; }
identity=$(aws_r4r sts get-caller-identity --query Arn --output text)
[[ "$identity" == arn:aws:iam::124021120482:user/r4r-codex-deployer ]]
aws_r4r s3vectors get-index --vector-bucket-name amzn-r4r-vectors --index-name amzn-r4r-vector-index > /tmp/r4r-s3-index.json
python3 - <<'PY'
import json
x=json.load(open('/tmp/r4r-s3-index.json'))['index']
assert (x['dimension'],x['dataType'],x['distanceMetric'])==(768,'float32','cosine')
PY
# Only ResourceNotFound allows creation. AccessDenied always stops deployment.
config=$(mktemp); errors=$(mktemp)
trap 'rm -f "$config" "$errors"' EXIT
exists=true
if ! aws_r4r lambda get-function-configuration --function-name r4r-s3-vectors-retrieval > "$config" 2> "$errors"; then
  if ! grep -q ResourceNotFoundException "$errors"; then cat "$errors" >&2; exit 1; fi
  exists=false
fi
mvn -f lambda-s3-vectors/pom.xml clean verify
printf '%s\n' 'Private Java21 x86_64, 512 MB, 15s, no VPC or public endpoint; existing r4r-lambda-execution role.'
jar=fileb://lambda-s3-vectors/target/r4r-s3-vectors-retrieval-1.0.0.jar
if "$exists"; then
  revision=$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["RevisionId"])' "$config")
  aws_r4r lambda update-function-code --function-name r4r-s3-vectors-retrieval --revision-id "$revision" --zip-file "$jar" > /dev/null
else
  aws_r4r iam get-role --role-name r4r-lambda-execution --query Role.Arn
  aws_r4r lambda create-function --function-name r4r-s3-vectors-retrieval --runtime java21 --architectures x86_64 \
    --role arn:aws:iam::124021120482:role/r4r-lambda-execution --handler com.riansares.r4r.s3lambda.Handler::handleRequest \
    --memory-size 512 --timeout 15 --zip-file "$jar" > /dev/null
fi
aws_r4r lambda wait function-active-v2 --function-name r4r-s3-vectors-retrieval
aws_r4r lambda wait function-updated-v2 --function-name r4r-s3-vectors-retrieval
printf '%s\n' 'Deploy complete. Validate execution-role QueryVectors/GetVectors using a controlled invocation before enabling the NAS transport.'
