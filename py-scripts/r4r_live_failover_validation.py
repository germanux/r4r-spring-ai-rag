"""Opt-in real validation controller. Runtime credentials must come from LiveFailoverExecution, never file parsing."""
import argparse, hashlib, http.client, http.server, html, io, json, os, pathlib, re, select, signal, socket, socketserver
import subprocess, threading, time, urllib.request, urllib.error, urllib.parse, shutil
ROOT=pathlib.Path(__file__).resolve().parents[1]
OUT=ROOT/'target/live-failover'
OUT.mkdir(parents=True,exist_ok=True)
SECRETS=[v for k,v in os.environ.items() if k in ('POSTGRES_PASSWORD','OPENAI_API_KEY','AWS_ACCESS_KEY_ID','AWS_SECRET_ACCESS_KEY','AWS_SESSION_TOKEN') and len(v)>3]
SECRETS=list({form for value in SECRETS for form in (value,html.escape(value),json.dumps(value)[1:-1])})
def redact(text):
 for value in SECRETS:text=text.replace(value,'[REDACTED]')
 return text

def cases():
 result=[]
 for lang,filename in [('es','RagLiveQuestionsIT.java'),('en','RagLiveEnglishQuestionsIT.java')]:
  text=(ROOT/'src/test/java/com/riansares/r4r/llm_tests'/filename).read_text()
  entries=re.findall(r'new QuestionCase\("([^"\n]*)", "([^"\n]*)"(?:, "([^"\n]*)")?, (true|false)\)',text)
  entries=entries[:15]
  for i,entry in enumerate(entries):
   name,question,retrieval,evidence=entry
   if i==0 or name in ('Humedades','Damp') or 'fotosíntesis' in name or 'photosynthesis' in name:
    result.append(dict(source=filename,number=i+1,name=name,question=question,lang=lang,evidence=evidence=='true'))
 assert len(result)==6
 return result

def capture_model_response(buffer,proxy):
 class Stream(io.BytesIO):
  def close(self):pass
 class Socket:
  def __init__(self,stream):self.stream=stream
  def makefile(self,*args):return self.stream
 while b'\r\n\r\n' in buffer:
  stream=Stream(buffer)
  try:
   response=http.client.HTTPResponse(Socket(stream));response.begin();body=response.read()
   consumed=stream.tell();payload=json.loads(body)
   if isinstance(payload,dict) and 'message' in payload:
    record={'time':time.time(),'proxy':proxy,'model':payload.get('model'),'content':payload['message'].get('content'),
            'promptTokens':payload.get('prompt_eval_count'),'outputTokens':payload.get('eval_count'),
            'loadDurationNs':payload.get('load_duration')}
    with (OUT/'ollama-outputs.jsonl').open('a') as output:output.write(redact(json.dumps(record,ensure_ascii=False))+'\n')
   buffer=buffer[consumed:]
  except (ValueError,http.client.HTTPException,OSError):break
 return buffer[-1048576:]

class Proxy:
 def __init__(self,name,host,port):
  self.name,self.target,self.enabled=name,(host,port),True;self.sockets=set();self.lock=threading.Lock();self.connections=0
  owner=self
  class Handler(socketserver.BaseRequestHandler):
   def handle(self):
    if not owner.enabled:return
    try:remote=socket.create_connection(owner.target,3)
    except OSError:return
    with owner.lock:owner.sockets.update((self.request,remote));owner.connections+=1
    response_buffer=b'';request_buffer=b''
    try:
     while owner.enabled:
      ready,_,_=select.select((self.request,remote),(),(),1)
      for source in ready:
       data=source.recv(65536)
       if not data:return
       (remote if source is self.request else self.request).sendall(data)
       if source is remote and owner.name.startswith('gen_'):
        response_buffer=capture_model_response(response_buffer+data,owner.name)
       elif source is self.request and owner.name.startswith('gen_'):
        request_buffer+=data
        while b'\r\n\r\n' in request_buffer:
         headers,body=request_buffer.split(b'\r\n\r\n',1)
         length=re.search(rb'(?im)^content-length:\s*(\d+)',headers)
         if not length:request_buffer=b'';break
         size=int(length[1])
         if len(body)<size:break
         request_buffer=body[size:]
         try:
          payload=json.loads(body[:size]);messages=payload.get('messages',[])
          system='\n'.join(m.get('content','') for m in messages if m.get('role')=='system')
          metadata=dict(time=time.time(),proxy=owner.name,model=payload.get('model'),options=payload.get('options'),
           think=payload.get('think'),roles=[m.get('role') for m in messages],systemCharacters=len(system),
           englishRuleTransmitted='Write the entire answer in English' in system,
           terminologyTransmitted='Translation vocabulary only' in system)
          with (OUT/'ollama-request-metadata.jsonl').open('a') as output:output.write(redact(json.dumps(metadata))+'\n')
         except (ValueError,TypeError):pass
    except OSError:pass
    finally:
     with owner.lock:owner.sockets.discard(self.request);owner.sockets.discard(remote)
     remote.close()
  class Server(socketserver.ThreadingTCPServer):allow_reuse_address=True;daemon_threads=True
  self.server=Server(('127.0.0.1',0),Handler);self.port=self.server.server_address[1]
  threading.Thread(target=self.server.serve_forever,daemon=True).start()
 def toggle(self,value):
  self.enabled=value
  if not value:
   with self.lock:
    for conn in list(self.sockets):
     try:conn.shutdown(socket.SHUT_RDWR);conn.close()
     except OSError:pass
 def close(self):self.toggle(False);self.server.shutdown();self.server.server_close()

def serve(block_openai=False):
 proxies={key:Proxy(key,host,port) for key,host,port in [
  ('pg','192.168.1.203',5432),('gen_pc','192.168.1.207',11434),('gen_lp','192.168.1.209',11434),
  ('embed_lp','192.168.1.209',11434),('embed_pc','192.168.1.207',11434),('embed_nas','192.168.1.203',11434)]}
 public={line.split('=',1)[0]:line.split('=',1)[1] for line in (ROOT/'docker-spring/.env.base').read_text().splitlines() if line and not line.startswith('#')}
 generation_entries=[entry.split('|') for entry in public['RAG_GENERATION_PROVIDERS'].split(',')]
 generation=','.join(f'http://127.0.0.1:{proxies[k].port}|{entry[1]}|{entry[2]}' for k,entry in zip(['gen_pc','gen_lp'],generation_entries))
 embedding=','.join(f'http://127.0.0.1:{proxies[k].port}|{timeout}' for k,timeout in [('embed_lp','8s'),('embed_pc','8s'),('embed_nas','30s')])
 (OUT/'proxy-map.json').write_text(json.dumps({k:dict(target=p.target,port=p.port) for k,p in proxies.items()},indent=2))
 # Maven may rebuild the main artifact while tests run: pin a separate immutable snapshot.
 snapshot=OUT/'application-under-test.jar'
 shutil.copyfile(ROOT/'target/r4r-spring-ai-rag-0.3.2.jar',snapshot)
 remote_block=None;java_options=[]
 if block_openai:
  class RemoteFailure(http.server.BaseHTTPRequestHandler):
   def do_CONNECT(self):
    remote_block.attempts+=1
    self.send_response(503);self.end_headers()
   def log_message(self,*args):pass
  remote_block=http.server.ThreadingHTTPServer(('127.0.0.1',0),RemoteFailure);remote_block.attempts=0
  threading.Thread(target=remote_block.serve_forever,daemon=True).start()
  java_options=['-Dhttps.proxyHost=127.0.0.1','-Dhttps.proxyPort='+str(remote_block.server_port),
                '-Dhttp.nonProxyHosts=localhost|127.*|192.168.*']
 args=['java',*java_options,'-jar',str(snapshot),'--server.port=18781','--server.address=127.0.0.1',
  '--spring.profiles.active=default','--spring.flyway.enabled=false','--spring.sql.init.mode=never',
  '--spring.ai.vectorstore.pgvector.initialize-schema=false','--spring.datasource.hikari.read-only=true',
  '--spring.datasource.hikari.connection-init-sql=SET default_transaction_read_only=on',
  '--spring.datasource.hikari.connection-timeout=3000','--spring.datasource.hikari.validation-timeout=1000',
  '--spring.datasource.hikari.maximum-pool-size=2','--spring.datasource.hikari.minimum-idle=0',
  f'--spring.datasource.url=jdbc:postgresql://127.0.0.1:{proxies["pg"].port}/r4r_rag?connectTimeout=3&socketTimeout=5',
  '--r4r.catalog-on-startup=false','--r4r.rag.warmup.on-startup=false',
  '--r4r.api-security.min-interval=0s','--r4r.api-security.max-global-requests-per-minute=10000',
  '--RAG_GENERATION_PROVIDERS='+generation,'--RAG_EMBEDDING_PROVIDERS='+embedding]
 process=subprocess.Popen(args,cwd=ROOT,env=os.environ.copy(),stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
 log=OUT/'application.log'
 def output():
  with log.open('w') as handle:
   for line in process.stdout:handle.write(redact(line));handle.flush()
 threading.Thread(target=output,daemon=True).start()
 class Control(http.server.BaseHTTPRequestHandler):
  def do_POST(self):
   data=json.loads(self.rfile.read(int(self.headers.get('Content-Length',0))))
   for key,value in data.items():proxies[key].toggle(value)
   body=json.dumps({k:dict(enabled=p.enabled,connections=p.connections) for k,p in proxies.items()}).encode()
   self.send_response(200);self.end_headers();self.wfile.write(body)
  def log_message(self,*args):pass
 control=http.server.ThreadingHTTPServer(('127.0.0.1',18782),Control)
 def stop(*_):raise KeyboardInterrupt
 signal.signal(signal.SIGTERM,stop)
 signal.signal(signal.SIGINT,stop)
 print('Controller running; public proxy map saved.',flush=True)
 try:control.serve_forever()
 except KeyboardInterrupt:pass
 finally:
  if process.poll() is None:
   process.terminate()
   try:process.wait(12)
   except subprocess.TimeoutExpired:process.kill();process.wait()
  control.server_close()
  for proxy in proxies.values():proxy.close()
  if remote_block:
   (OUT/'blocked-remote-attempts.json').write_text(json.dumps({'connectAttempts':remote_block.attempts,'credentialsTransmitted':False}))
   remote_block.shutdown();remote_block.server_close()
  print('Application and all validation proxies stopped.',flush=True)


def validate(case,payload):
 errors=[]
 answer=payload.get('answer','');citations=payload.get('citations',[])
 if not case['evidence']:
  if payload.get('abstained') is not True or answer.strip() or citations:errors.append('Expected blank-answer abstention without citations')
 else:
  if payload.get('abstained') is not False:errors.append('Unexpected abstention')
  if not answer.strip() or re.fullmatch(r'(?:\[S\d+\][\s,;:.]*)+',answer.strip()):errors.append('No substantive answer')
  if not citations:errors.append('Missing citations')
  if case['lang']=='en' and not re.search(r'\b(the|a|an|is|are|you|your|to|of|for|with|can|will|may|should|it|and|or|in|on|before|depending|please)\b',answer,re.I):errors.append('English language check failed')
  labels={c.get('label') for c in citations};mentioned=set(re.findall(r'\[S\d+\]',answer))
  if not mentioned or not mentioned.issubset(labels):errors.append('Missing/unresolved citation references')
  for citation in citations:
   if not re.fullmatch(r'\[S\d+\]',citation.get('label','')) or not citation.get('source'):errors.append('Invalid citation metadata')
   source=citation.get('source','')
   matches=list((ROOT/'knowledge').rglob(pathlib.Path(source).name)) if source else []
   if not matches:errors.append('Citation source not found in repository corpus: '+source)
 return errors

def batch(scenario,transport='sse',selected=None,expected_vector=None,expected_embedding=None,expected_model=None,external_translation=False,base_url='http://127.0.0.1:18781'):
 deadline=time.monotonic()+45
 while True:
  try:
   check=urllib.request.Request(base_url+'/api/rag/runtime',headers={'Origin':'https://athagon.tech'})
   with urllib.request.urlopen(check,timeout=2) as response:
    if response.status==200:break
  except Exception:pass
  if time.monotonic()>=deadline:raise SystemExit('Validation application not ready; battery not executed')
  time.sleep(.25)
 results=[]
 for case in cases():
  if selected and (case['lang']+str(case['number'])) not in selected:continue
  local=base_url=='http://127.0.0.1:18781'
  before=(OUT/'application.log').stat().st_size if local else 0
  started=time.monotonic();events=[];payload={};http_status=None;errors=[];translation=None
  body=dict(question=case['question'],responseLanguage=case['lang'])
  if external_translation and case['lang']=='en':
   translating=time.monotonic()
   try:
    params=urllib.parse.urlencode(dict(q=case['question'],langpair='en|es'))
    query=urllib.request.Request('https://api.mymemory.translated.net/get?'+params,headers={'Origin':'https://athagon.tech'})
    with urllib.request.urlopen(query,timeout=5) as response:
     data=json.load(response);cors=response.headers.get('Access-Control-Allow-Origin')
    text=data.get('responseData',{}).get('translatedText','').strip()
    if int(data.get('responseStatus',0))!=200 or data.get('quotaFinished') or not text or text.lower()==case['question'].lower():
     raise ValueError('Invalid external translation')
    body['retrievalQuestion']=text
    translation=dict(provider='MyMemory',result='PASS',elapsedMs=(time.monotonic()-translating)*1000,text=text,cors=cors)
   except Exception as error:
    translation=dict(provider='MyMemory',result='FAIL',elapsedMs=(time.monotonic()-translating)*1000,error=type(error).__name__)
    errors.append('External translation failed; backend fallback measured instead')
  request=urllib.request.Request(base_url+'/api/rag/answers'+('/stream' if transport=='sse' else ''),
   data=json.dumps(body,ensure_ascii=False).encode(),
   headers={'Origin':'https://athagon.tech','Content-Type':'application/json','Accept':'text/event-stream' if transport=='sse' else 'application/json'})
  try:
   with urllib.request.urlopen(request,timeout=190) as response:
    http_status=response.status
    if transport=='sse':
     for raw in response:
      line=raw.decode().strip()
      if line.startswith('data:'):
       event=json.loads(line[5:].strip());events.append(event)
       if event.get('stage')=='ANSWER_READY':payload=event
       if event.get('stage')=='ERROR':errors.append('SSE '+event.get('message','ERROR'))
    else:payload=json.load(response)
   if http_status!=200:errors.append('Unexpected HTTP')
   errors+=validate(case,payload)
  except Exception as error:
   errors.append(type(error).__name__)
   if isinstance(error,urllib.error.HTTPError):http_status=error.code
  elapsed=time.monotonic()-started
  trace=''
  if local:
   with (OUT/'application.log').open() as handle:handle.seek(before);trace=handle.read()
  vector=re.findall(r'Vector retrieval provider=(\w+) result=(\w+) durationMs=([\d.]+)',trace)
  embedded=re.findall(r'Embedding id=([^ ]+) provider=([^ ]+) model=([^ ]+) outcome=([^ ]+) attemptMs=([\d.]+) totalMs=([\d.]+)',trace)
  generation=re.findall(r'Generation [^ ]+ completed by provider=([^\n]+)',trace)
  if expected_vector and not any(item[0]==expected_vector and item[1]=='success' for item in vector):
   errors.append('Expected vector store not observed: '+expected_vector)
  if expected_vector=='s3vectors' and not any(item[0]=='pgvector' and item[1]=='failed' for item in vector):
   errors.append('PostgreSQL failure before S3 not observed')
  if expected_embedding:
   import uuid
   mapping=json.loads((OUT/'proxy-map.json').read_text())
   endpoint='http://127.0.0.1:'+str(mapping[expected_embedding]['port'])
   identity='ollama-'+str(uuid.UUID(bytes=hashlib.md5(endpoint.encode()).digest(),version=3))
   if not any(item[1]==identity and item[3]=='success' for item in embedded):errors.append('Expected embedding provider not observed')
  if expected_model and payload.get('provider',{}).get('model')!=expected_model:
   errors.append('Unexpected generation model')
  result=dict(scenario=scenario,baseUrl=base_url,transport=transport,case=case,http=http_status,elapsed=round(elapsed,3),
   payload=payload,events=events,vector=vector,embedding=embedded,generation=generation,errors=errors,translation=translation,
   result='FAIL' if errors else 'PASS',trace=trace,expected=dict(vector=expected_vector,embedding=expected_embedding,model=expected_model))
  results.append(result)
  filename=OUT/f'{scenario}-{transport}-{case["lang"]}-{case["number"]:02}.json'
  filename.write_text(redact(json.dumps(result,ensure_ascii=False,indent=2)))
  print(scenario,transport,case['lang'],case['number'],result['result'],f'{elapsed:.2f}s',vector,errors,flush=True)
 return results

def verify_maven():
 process=subprocess.Popen(['mvn','-q','verify'],cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
 with (OUT/'maven-verify-final.log').open('w') as output:
  for line in process.stdout:output.write(redact(line));output.flush()
 code=process.wait()
 for directory in ['surefire-reports','failsafe-reports']:
  for file in (ROOT/'target'/directory).glob('*'):
   if file.is_file() and file.suffix in ('.xml','.txt','.log'):
    text=file.read_text(errors='replace');safe=redact(text)
    if text!=safe:file.write_text(safe)
 print('Maven verify exit:',code,flush=True)
 return code


def negative_checks():
 case=next(case for case in cases() if case['lang']=='es' and case['number']==1)
 def switches(values):
  request=urllib.request.Request('http://127.0.0.1:18782',data=json.dumps(values).encode(),headers={'Content-Type':'application/json'})
  with urllib.request.urlopen(request) as response:response.read()
 output=[]
 try:
  switches({'embed_lp':False,'embed_pc':False,'embed_nas':False})
  for transport in ['rest','sse']:
   start=time.monotonic();before=(OUT/'application.log').stat().st_size;events=[];body='';status=None
   request=urllib.request.Request('http://127.0.0.1:18781/api/rag/answers'+('/stream' if transport=='sse' else ''),
    data=json.dumps({'question':case['question'],'responseLanguage':'es'}).encode(),
    headers={'Origin':'https://athagon.tech','Content-Type':'application/json','Accept':'text/event-stream' if transport=='sse' else 'application/json'})
   try:
    with urllib.request.urlopen(request,timeout=45) as response:status=response.status;body=response.read().decode()
   except urllib.error.HTTPError as error:status=error.code;body=error.read().decode()
   if transport=='sse':
    events=[json.loads(line[5:].strip()) for line in body.splitlines() if line.startswith('data:')]
   with (OUT/'application.log').open() as stream:stream.seek(before);trace=stream.read()
   valid=(status==503 if transport=='rest' else status==200 and any(e.get('stage')=='ERROR' for e in events)
          and not any(e.get('stage')=='ANSWER_READY' for e in events))
   valid=valid and 'Vector fallback' not in trace and not re.search(r'192\.168\.|AWS_|api_key|password',body,re.I)
   record={'scenario':'all-embeddings-unavailable','case':case,'transport':transport,'http':status,
           'elapsed':round(time.monotonic()-start,3),'result':'PASS' if valid else 'FAIL','expected':'controlled error; no S3 fallback',
           'events':events,'trace':trace}
   (OUT/f'negative-{transport}.json').write_text(redact(json.dumps(record,ensure_ascii=False,indent=2)));output.append(record)
   print('Controlled error',transport,record['result'],status,flush=True)
 finally:switches({'embed_lp':True,'embed_pc':True,'embed_nas':True})
 for endpoint in ['/api/rag/answers','/api/rag/answers/stream','/api/rag/runtime']:
  method='GET' if endpoint.endswith('runtime') else 'POST'
  for origin,expected in [('http://127.0.0.1:8000',200),('https://athagon.tech',200),('https://not-authorized.invalid',403)]:
   request=urllib.request.Request('http://127.0.0.1:18781'+endpoint,method='OPTIONS',headers={
    'Origin':origin,'Access-Control-Request-Method':method,'Access-Control-Request-Headers':'content-type'})
   try:
    with urllib.request.urlopen(request,timeout=10) as response:status=response.status;allowed=response.headers.get('Access-Control-Allow-Origin')
   except urllib.error.HTTPError as error:status=error.code;allowed=error.headers.get('Access-Control-Allow-Origin')
   output.append({'scenario':'CORS','endpoint':endpoint,'origin':origin,'http':status,
                  'result':'PASS' if status==expected and (expected==403 or allowed==origin) else 'FAIL'})
 (OUT/'negative-cors-summary.json').write_text(json.dumps(output,ensure_ascii=False,indent=2))
 print('CORS checks:',[(r.get('endpoint'),r.get('http'),r['result']) for r in output if r['scenario']=='CORS'],flush=True)

def generation_error_checks():
 """Use only with serve --block-openai; remote TLS is rejected before any API credentials are sent."""
 case=next(case for case in cases() if case['lang']=='es' and case['number']==1)
 switch=urllib.request.Request('http://127.0.0.1:18782',data=b'{"gen_pc":false,"gen_lp":false}',headers={'Content-Type':'application/json'})
 with urllib.request.urlopen(switch,timeout=5) as response:response.read()
 rows=[]
 for transport in ['rest','sse']:
  before=(OUT/'application.log').stat().st_size;started=time.monotonic()
  request=urllib.request.Request('http://127.0.0.1:18781/api/rag/answers'+('/stream' if transport=='sse' else ''),
   data=json.dumps({'question':case['question'],'responseLanguage':'es'}).encode(),
   headers={'Origin':'https://athagon.tech','Content-Type':'application/json','Accept':'text/event-stream' if transport=='sse' else 'application/json'})
  try:
   with urllib.request.urlopen(request,timeout=45) as response:status=response.status;body=response.read().decode()
  except urllib.error.HTTPError as error:status=error.code;body=error.read().decode()
  events=[json.loads(line[5:].strip()) for line in body.splitlines() if line.startswith('data:')]
  with (OUT/'application.log').open() as stream:stream.seek(before);trace=stream.read()
  failed={event.get('model') for event in events if event.get('stage')=='PROVIDER_FAILED'}
  valid=status==503 if transport=='rest' else status==200 and any(e.get('stage')=='ERROR' for e in events) and not any(e.get('stage')=='ANSWER_READY' for e in events) and failed=={'qwen3:8b','qwen2.5:7b','gpt-6-luna'}
  valid=valid and 'model=gpt-6-luna error=' in trace and not re.search(r'192\.168\.|AWS_|api_key|password',body,re.I)
  row=dict(scenario='all-generation-unavailable',case=case,transport=transport,http=status,elapsed=round(time.monotonic()-started,3),
   result='PASS' if valid else 'FAIL',events=events,trace=trace,simulation='Local generation proxies down; OpenAI CONNECT rejected before TLS')
  (OUT/f'negative-generation-{transport}.json').write_text(redact(json.dumps(row,ensure_ascii=False,indent=2)));rows.append(row)
  print('All generation unavailable',transport,row['result'],status,flush=True)
 return rows

if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('mode',choices=['serve','batch','control','verify','negative']);parser.add_argument('--scenario',default='normal');parser.add_argument('--transport',default='sse');parser.add_argument('--switches',default='{}');parser.add_argument('--selected',default='');parser.add_argument('--block-openai',action='store_true')
 options=parser.parse_args()
 if options.mode=='verify':raise SystemExit(verify_maven())
 elif options.mode=='negative':negative_checks()
 elif options.mode=='serve':serve(options.block_openai)
 elif options.mode=='control':
  request=urllib.request.Request('http://127.0.0.1:18782',data=options.switches.encode(),headers={'Content-Type':'application/json'})
  print(urllib.request.urlopen(request).read().decode())
 else:
  results=batch(options.scenario,options.transport,options.selected.split(',') if options.selected else None)
  raise SystemExit(1 if any(row['result']=='FAIL' for row in results) else 0)
