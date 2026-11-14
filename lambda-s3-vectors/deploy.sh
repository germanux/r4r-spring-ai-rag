#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
vectors=""; replace_index=false; backup_dir=""; profile="r4r-codex"
while (( $# )); do
  case "$1" in
    --vectors) vectors="$2"; shift 2 ;;
    --profile) profile="$2"; shift 2 ;;
    --backup-dir) backup_dir="$2"; shift 2 ;;
    --replace-index) replace_index=true; shift ;;
    *) printf 'Unknown argument: %s\n' "$1" >&2; exit 2 ;;
  esac
done
if "$replace_index"; then [[ -n "$vectors" && -n "$backup_dir" ]]; fi
if [[ -n "$vectors" ]]; then [[ -s "$vectors" ]]; fi
aws_r4r() { aws "$@" --profile "$profile" --region eu-north-1; }
identity=$(aws_r4r sts get-caller-identity --query Arn --output text)
if [[ "$profile" == r4r-codex ]]; then
  [[ "$identity" == arn:aws:iam::124021120482:user/r4r-codex-deployer ]]
else
  [[ "$(aws_r4r sts get-caller-identity --query Account --output text)" == 124021120482 ]]
fi
# Only ResourceNotFound allows creation. AccessDenied always stops deployment.
config=$(mktemp); errors=$(mktemp)
trap 'rm -f "$config" "$errors"' EXIT
exists=true
if ! aws_r4r lambda get-function-configuration --function-name r4r-s3-vectors-retrieval > "$config" 2> "$errors"; then
  if ! grep -q ResourceNotFoundException "$errors"; then cat "$errors" >&2; exit 1; fi
  exists=false
fi
mvn -f lambda-s3-vectors/pom.xml clean verify
# Optional, explicit replacement/import. Never reads historical exports as current data.
if [[ -n "$vectors" ]]; then
  python3 -E - "$vectors" "$replace_index" "$backup_dir" "$profile" <<'PY_SYNC'
import sys,json,subprocess,math,hashlib,struct,time,tempfile
from pathlib import Path
path=Path(sys.argv[1]);replace=sys.argv[2]=='true';backup=Path(sys.argv[3]) if sys.argv[3] else None
payload=json.loads(path.read_text());rows=payload['vectors'];assert rows
keys=[r['key'] for r in rows];assert len(set(keys))==len(keys)
for row in rows:
    values=row['data']['float32'];assert len(values)==1024 and all(math.isfinite(v) for v in values) and any(v!=0 for v in values)
    metadata=row['metadata'];assert metadata.get('source') and metadata.get('source_text') and isinstance(metadata.get('ordinal'),int)
    assert not any(isinstance(v,list) and not v for v in metadata.values()), 'S3 metadata cannot contain empty arrays'
assert {'es','en'} <= {r['metadata']['source'].split('/',1)[0] for r in rows}, 'Full bilingual export required'
base=['aws','--profile',sys.argv[4] if len(sys.argv)>4 else 'r4r-codex','--region','eu-north-1','--output','json']
index_args=['--vector-bucket-name','amzn-r4r-vectors','--index-name','amzn-r4r-vector-index']
def aws(*args,data=None):
    command=[*base,*args]
    if data is not None:
        with tempfile.NamedTemporaryFile(mode='w',suffix='.json') as body:
            json.dump(data,body);body.flush()
            result=subprocess.run(command+['--cli-input-json','file://'+body.name],capture_output=True,text=True,timeout=120)
    else:
        result=subprocess.run(command,capture_output=True,text=True,timeout=120)
    if result.returncode:raise RuntimeError('AWS operation failed: '+args[0]+' '+args[1]+'; stop and use the verified rollback backup')
    return json.loads(result.stdout) if result.stdout.strip() else {}
current=aws('s3vectors','get-index',*index_args)['index']
existing=aws('s3vectors','list-vectors',*index_args,*(['--return-data','--return-metadata'] if replace else []))['vectors']
def canonical(values):
    return {r['key']:(struct.pack('<1024f',*r['data']['float32']),r['metadata']) for r in values}
if replace:
    assert backup and backup.is_dir(), 'Verified backup directory required'
    pg=json.loads((backup/'postgres-manifest.json').read_text())
    assert hashlib.sha256((backup/'r4r_rag-full-nomic.dump').read_bytes()).hexdigest()==pg['sha256']
    manifest=json.loads((backup/'s3-manifest.json').read_text())
    for name,digest in manifest['sha256'].items():assert hashlib.sha256((backup/name).read_bytes()).hexdigest()==digest
    old=json.loads((backup/'s3-vectors.json').read_text())['vectors']
    restore=json.loads((backup/'s3-restore-index.json').read_text())
    assert current['dimension']==restore['dimension'] and current['distanceMetric']==restore['distanceMetric']=='cosine'
    assert {r['key']:r for r in existing}=={r['key']:r for r in old}, 'Current vectors differ from backup; take a new backup'
    # This is the only destructive step; bucket and all other AWS resources remain untouched.
    aws('s3vectors','delete-index',*index_args)
    request={**restore,'dimension':1024,'dataType':'float32','distanceMetric':'cosine'}
    aws('s3vectors','create-index',data=request)
else:
    assert current['dimension']==1024 and current['distanceMetric']=='cosine', 'Explicit --replace-index and backups required'
    assert {r['key'] for r in existing} <= set(keys), 'Obsolete keys require a backed-up explicit replacement'
for start in range(0,len(rows),100):
    aws('s3vectors','put-vectors',data={'vectorBucketName':'amzn-r4r-vectors','indexName':'amzn-r4r-vector-index','vectors':rows[start:start+100]})
    print('S3 imported',min(start+100,len(rows)),'/',len(rows),flush=True)
# Verify every float32 value and metadata record, not just a remote count.
for attempt in range(5):
    remote=aws('s3vectors','list-vectors',*index_args,'--return-data','--return-metadata')['vectors']
    if canonical(remote)==canonical(rows):break
    if attempt<4:time.sleep(2)
else:raise RuntimeError('S3 data or metadata verification failed')
print('S3 verified:',len(remote),'vectors, 1024 dimensions, cosine')
PY_SYNC
fi
aws_r4r s3vectors get-index --vector-bucket-name amzn-r4r-vectors --index-name amzn-r4r-vector-index > /tmp/r4r-s3-index.json
python3 - <<'PY'
import json
x=json.load(open('/tmp/r4r-s3-index.json'))['index']
assert (x['dimension'],x['dataType'],x['distanceMetric'])==(1024,'float32','cosine')
PY
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
