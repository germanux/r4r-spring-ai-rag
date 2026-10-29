"""Comprueba el prefijo search_query: sobre el índice existente.

Desde la raíz: python3 py-scripts/r4r-prefix-probe.py
Envía las seis preguntas a Ollama y hace SELECT de solo lectura contra
PostgreSQL. No altera el índice ni añade prefijos a los documentos guardados.
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

# Se cambia solo el texto de esta prueba; no se reindexan los documentos.
for topic,lang,text in questions:
 vector,elapsed=embed("search_query: "+text)
 literal='['+','.join(map(str,vector))+']'
 sql="SELECT json_agg(row_to_json(t)) FROM (SELECT id, metadata->>'source' source, 1-(embedding <=> '"+literal+"'::vector) score FROM vector_store ORDER BY embedding <=> '"+literal+"'::vector LIMIT 5) t;"
 results=query(sql)
 print(json.dumps({'topic':topic,'language':lang,'best':results[0]['score'],'passed':sum(x['score']>0.64 for x in results)},ensure_ascii=False),flush=True)
