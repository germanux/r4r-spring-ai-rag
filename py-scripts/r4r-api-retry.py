"""Repite las tres llamadas de la prueba original que recibieron HTTP 429.

Desde la raíz: python3 py-scripts/r4r-api-retry.py
Realiza solicitudes reales a la API; espera el Retry-After antes de reintentar.
El endpoint y los tres casos reflejan el diagnóstico de octubre de 2026.
"""
import json,time,urllib.request,urllib.error
cases=[('Baño','EN','I want to repair my bathroom, how much can it cost?'),('Cocina','EN','I want to repair my kitchen, how much can it cost?'),('Humedades','ES','Tengo un problema de humedades en una cueva, ¿es posible solucionarlo?')]
# Un 429 no es una respuesta RAG: respetar Retry-After y repetir la consulta.
for topic,lang,text in cases:
 for attempt in range(4):
  request=urllib.request.Request('http://192.168.1.203:18080/api/rag/answers',data=json.dumps({'question':text},ensure_ascii=False).encode(),headers={'Content-Type':'application/json','Origin':'https://athagon.tech'})
  started=time.perf_counter()
  try:
   with urllib.request.urlopen(request,timeout=180) as r: body=json.load(r);status=r.status
   print(json.dumps({'topic':topic,'language':lang,'http':status,'seconds':round(time.perf_counter()-started,2),'abstained':body.get('abstained'),'citations':len(body.get('citations',[]))},ensure_ascii=False),flush=True)
   break
  except urllib.error.HTTPError as e:
   if e.code==429:
    wait=int(e.headers.get('Retry-After','60'))+1
    print(json.dumps({'topic':topic,'language':lang,'http':429,'retry_after_seconds':wait-1,'minimum_interval_seconds':e.headers.get('X-R4R-RateLimit-Min-Interval-Seconds')}),flush=True)
    time.sleep(wait)
   else:
    print(json.dumps({'topic':topic,'language':lang,'http':e.code}),flush=True);break
  except Exception as e:
   print(json.dumps({'topic':topic,'language':lang,'error':str(e)[:120]}),flush=True);break
 else: print(json.dumps({'topic':topic,'language':lang,'error':'rate-limit retries exhausted'}),flush=True)
