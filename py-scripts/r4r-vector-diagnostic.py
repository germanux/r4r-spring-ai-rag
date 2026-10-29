"""Compara búsquedas ES/EN e inspecciona el índice RAG existente.

Desde la raíz: python3 py-scripts/r4r-vector-diagnostic.py vector
O, para consultar la API desplegada: python3 py-scripts/r4r-vector-diagnostic.py api

El modo vector lee docker-spring/.env y hace SELECT contra producción con la
sesión PostgreSQL en solo lectura. Envía preguntas y tres textos a Ollama; no
imprime los textos de los documentos ni la contraseña. El modo api realiza seis
llamadas reales a la API y puede invocar el modelo generativo.
"""
import os,sys,json,subprocess,urllib.request,time
from pathlib import Path
# Leer el archivo compartido sin publicar credenciales en la salida.
# Esta lectura supone la sintaxis KEY=valor sin comillas del .env del proyecto.
v={}
for line in Path('docker-spring/.env').read_text().splitlines():
 if line and not line.startswith('#') and '=' in line:
  k,x=line.split('=',1);v[k]=x
questions=[('Baño','ES','Quiero reparar mi baño, ¿cuánto me podría costar?'),('Baño','EN','I want to repair my bathroom, how much can it cost?'),('Cocina','ES','Quiero reparar mi cocina, ¿cuánto me podría costar?'),('Cocina','EN','I want to repair my kitchen, how much can it cost?'),('Humedades','ES','Tengo un problema de humedades en una cueva, ¿es posible solucionarlo?'),('Humedades','EN','I have a damp problem in a cave, is it possible to solve it?')]
# El servidor rechazará escrituras en esta sesión; psql recibe la clave por entorno.
pg=os.environ.copy();pg.update(PGPASSWORD=v['POSTGRES_PASSWORD'],PGOPTIONS='-c default_transaction_read_only=on',PGCONNECT_TIMEOUT='6')
# Ejecutar SQL y devolver el JSON sin mostrar el contenido de los documentos.
def query(sql):
 p=subprocess.run(['psql','-X','-q','-A','-t','-v','ON_ERROR_STOP=1','-h',v['POSTGRES_HOST'],'-p',v['POSTGRES_PORT'],'-U',v['POSTGRES_USER'],'-d',v['POSTGRES_DB']],input=sql,env=pg,capture_output=True,text=True,timeout=20)
 if p.returncode: raise RuntimeError('PostgreSQL: '+p.stderr[:300])
 return json.loads(p.stdout)
# Mismo endpoint /api/embed que utiliza OllamaEmbeddingModel de Spring AI.
# El tiempo incluye transporte y procesamiento del servidor.
def embed(text):
 request=urllib.request.Request(v['RAG_OLLAMA_BASE_URL'].rstrip('/')+'/api/embed',data=json.dumps({'model':v['RAG_EMBEDDING_MODEL'],'input':text},ensure_ascii=False).encode(),headers={'Content-Type':'application/json'})
 start=time.perf_counter()
 with urllib.request.urlopen(request,timeout=60) as response: result=json.load(response)
 return result['embeddings'][0],(time.perf_counter()-start)*1000
if len(sys.argv) != 2 or sys.argv[1] not in ('vector', 'api'):
 raise SystemExit('Uso: python3 py-scripts/r4r-vector-diagnostic.py {vector|api}')

if sys.argv[1]=='vector':
 for topic,lang,text in questions:
  vector,elapsed=embed(text)
  literal='['+','.join(map(str,vector))+']'
  sql="SELECT json_agg(row_to_json(t)) FROM (SELECT id, metadata->>'source' source, 1 - (embedding <=> '"+literal+"'::vector) score FROM vector_store ORDER BY embedding <=> '"+literal+"'::vector LIMIT 5) t;"
  results=query(sql)
  # Reproducir el filtro estricto de Spring AI: distancia coseno < 1 - 0,64.
  filtered=query("SELECT coalesce(json_agg(row_to_json(t)), '[]'::json) FROM (SELECT id, 1-(embedding <=> '"+literal+"'::vector) score FROM vector_store WHERE embedding <=> '"+literal+"'::vector < 0.36 ORDER BY embedding <=> '"+literal+"'::vector LIMIT 5) t;")
  print(json.dumps({'topic':topic,'language':lang,'embedding_ms':round(elapsed,2),'dimensions':len(vector),'results':results,'above_064':sum(r['score']>0.64 for r in results),'filtered_results':filtered},ensure_ascii=False),flush=True)
 # Comparar tres textos reales con sus vectores ya guardados, sin imprimirlos.
 samples=query("SELECT json_agg(row_to_json(t)) FROM (SELECT id,content FROM vector_store ORDER BY id LIMIT 3) t;")
 for sample in samples:
  vector,elapsed=embed(sample['content']);literal='['+','.join(map(str,vector))+']'
  similarity=query("SELECT row_to_json(t) FROM (SELECT id,1-(embedding <=> '"+literal+"'::vector) score FROM vector_store WHERE id='"+sample['id']+"') t;")
  print(json.dumps({'index_probe_id':sample['id'],'similarity_to_current_model':similarity['score'],'embedding_ms':round(elapsed,2)}),flush=True)
else:
 url='http://'+v['POSTGRES_HOST']+':18080/api/rag/answers'
 for i,(topic,lang,text) in enumerate(questions):
  if i: time.sleep(21)
  req=urllib.request.Request(url,data=json.dumps({'question':text},ensure_ascii=False).encode(),headers={'Content-Type':'application/json','Origin':'https://athagon.tech'})
  start=time.perf_counter()
  try:
   with urllib.request.urlopen(req,timeout=180) as response: body=json.load(response);status=response.status
   print(json.dumps({'topic':topic,'language':lang,'http':status,'seconds':round(time.perf_counter()-start,2),'abstained':body.get('abstained'),'citations':len(body.get('citations',[]))},ensure_ascii=False),flush=True)
  except Exception as e: print(json.dumps({'topic':topic,'language':lang,'error':str(e)[:200],'seconds':round(time.perf_counter()-start,2)},ensure_ascii=False),flush=True)
