# Migración Nomic → Qwen3, 2026-11-14

## Estado real

**PostgreSQL migrado realmente el 2026-11-14.** Ingesta completa terminada:
78 fuentes, 1.375 chunks y 1.375 vectores Qwen3/1024 (688 EN, 687 ES), sin diferencias
entre contenido/metadatos documentales y vectoriales. Cuatro consultas vectoriales
reales ES/EN recuperan fuentes pertinentes; métricas en el directorio de respaldo.

**S3 y Lambda migrados y verificados.** El índice original contiene ahora 1.375
vectores Qwen3/1024, float32/cosine. Se compararon todos los valores y metadatos con
la exportación actual. Lambda está Active / Successful y las dos consultas reales
ES/EN de `S3LambdaLiveIT` coinciden con QueryVectors directo (40 candidatos).

El intento inicial eliminó el índice pero falló la lectura JSON de `/dev/stdin`.
Se corrigió el script para usar archivos temporales, se recreó el mismo índice y
se completó la carga; no se reutilizaron embeddings Nomic.

**Solo falta desplegar la API nueva**, por el usuario:

```bash
bash scripts/deploy-synology.sh
```

El script despliega `.env.base` y carga después el `.env` privado existente.
No copia, respalda ni modifica `.env`. La configuración Compose efectiva del PC
confirma Qwen3/1024 y failover S3 habilitado mediante Lambda. No hay overrides de
embeddings en el `.env` privado del PC, que el usuario confirma igual al del NAS.
No se ha probado todavía el failover end-to-end con la API nueva y PostgreSQL detenido.

- Qwen disponible y probado con embedding real de 1024 componentes en portátil
  `.209`, PC `.207` y NAS `.203`. Se descargó en el portátil; ya existía en los otros.
- Digest verificado en los tres: `ac6da0dfba84a81fdbfbaf330198c33cd77c4cdfc53e8bc50eb581914a15621d`.
- El failover de embeddings sigue portátil → PC → NAS. El failover de almacenes
  sigue PostgreSQL → S3/Lambda; S3 no sustituye al servidor de embeddings.

Respaldo nuevo, ignorado por Git y sin sobrescribir los antiguos:
`docker-postgres/backups/migration-nomic-20261114T120713Z/`.
Incluye dump completo, TOC/manifiesto PostgreSQL, vectores/metadatos/configuración
S3 y código/configuración no sensible de Lambda. El dump se descomprimió entero y
se comprobaron todos los recuentos COPY; **no se ha probado una restauración real**.
Los 282 vectores S3 se compararon íntegramente con GetVectors. ZIP Lambda verificado
contra el hash publicado. Conservar otra copia del directorio antes del corte.

## Flujo y cambios

`KnowledgeIngestionCli → KnowledgeIngestionService → MarkdownDocumentLoader /
HeadingMarkdownChunker → PgVectorKnowledgeStore.index → Spring AI →
ObservedEmbeddingModel / EmbeddingProviderRouter` regenera desde el Markdown.
La ingesta completa existente conserva chunking y valida fuentes/chunks/vectores.
No se importan los JSON históricos ni se rellenan/truncan embeddings Nomic.

Flyway V4 solo convierte una `vector_store` **vacía** de 768 a 1024 y recrea su
HNSW cosine. Rechaza una tabla con vectores antiguos; nunca borra filas por sí misma.
Las migraciones anteriores permanecen intactas.

El modo `--r4r.ingestion.export-s3=...` del mismo CLI exporta los floats existentes
sin pedir embeddings. Reutiliza la expansión de encabezados de PostgreSQL y el
mapper S3; preserva claves, fuentes, ordinales, encabezados, texto y contexto.
El script AWS existente añade importación por lotes y reemplazo explícito con
verificación de respaldos. No modifica IAM, bucket ni recursos Titan.

La consulta incorpora la instrucción de recuperación recomendada por la
[ficha oficial Qwen](https://huggingface.co/Qwen/Qwen3-Embedding-0.6B); los documentos
se embeben sin ese prefijo. No hay traducción ni cambios de prompts de generación.
TopK, umbral 0.63, pesos del reranker, frontend y auditoría permanecen iguales.
Las pruebas históricas Nomic siguen siendo regresiones de ranking/contrato,
no mediciones de precisión semántica de Qwen.

## Comandos de corte utilizados / recuperación operativa

Desde la raíz, con Java 21, Maven, herramientas PostgreSQL y AWS CLI ya configuradas.
No iniciar PostgreSQL local. El usuario autorizó migrar con la API antigua activa
y asumir indisponibilidad. **No repetir el DELETE ni la reingesta ya completados.**
SSH solo se requiere para el despliegue final, que ejecutará el usuario. Las operaciones siguientes son explícitas
y destructivas **solo para los vectores RAG indicados**.

```bash
mvn clean verify
python3 -m unittest discover -s lambda-s3-vectors -p test_deploy.py -v
export R4R_MIGRATION_BACKUP=docker-postgres/backups/migration-nomic-20261114T120713Z
python3 - <<'PY'
import os,json,hashlib,subprocess
from pathlib import Path
p=Path(os.environ['R4R_MIGRATION_BACKUP'])
m=json.loads((p/'postgres-manifest.json').read_text())
assert hashlib.sha256((p/'r4r_rag-full-nomic.dump').read_bytes()).hexdigest()==m['sha256']
for name,digest in json.loads((p/'s3-manifest.json').read_text())['sha256'].items():
    assert hashlib.sha256((p/name).read_bytes()).hexdigest()==digest
subprocess.run(['pg_restore','--list',str(p/'r4r_rag-full-nomic.dump')],check=True,stdout=subprocess.DEVNULL)
print('Backup hashes and archive TOC verified')
PY
ssh -t capi@192.168.1.203 'export PATH=/usr/local/bin:/var/packages/ContainerManager/target/usr/bin:$PATH; cd /volume1/docker/r4r && sudo docker-compose --env-file .env -f docker-compose.yml stop api'
```

Actualizar también **solo** las claves públicas siguientes en el `.env` existente
del NAS y en cualquier override privado del PC. No imprimirlo ni copiar credenciales:

```dotenv
RAG_EMBEDDING_MODEL=qwen3-embedding:0.6b
RAG_EMBEDDING_DIMENSIONS=1024
RAG_EMBEDDING_EXPECTED_DIGEST=ac6da0dfba84a81fdbfbaf330198c33cd77c4cdfc53e8bc50eb581914a15621d
RAG_EMBEDDING_PROVIDERS=http://192.168.1.209:11434|8s,http://192.168.1.207:11434|8s,http://192.168.1.203:11434|30s
```

Función de sesión para SQL, reutilizando las credenciales existentes sin mostrarlas:

```bash
nas_sql() {
python3 - "$1" <<'PY'
import os,sys,shlex,subprocess
from pathlib import Path
v={}
for name in ('docker-spring/.env.base','docker-spring/.env'):
    p=Path(name)
    if not p.exists(): continue
    for line in p.read_text().splitlines():
        if not line.strip() or line.lstrip().startswith('#') or '=' not in line: continue
        k,x=line.split('=',1); a=shlex.split(x); v[k.strip()]=a[0] if len(a)==1 else x.strip()
assert v['POSTGRES_HOST']=='192.168.1.203' and v['POSTGRES_DB']=='r4r_rag'
e=os.environ.copy();e.update(PGPASSWORD=v['POSTGRES_PASSWORD'],PGCONNECT_TIMEOUT='5')
subprocess.run(['psql','-X','-v','ON_ERROR_STOP=1','-h',v['POSTGRES_HOST'],'-p',v['POSTGRES_PORT'],
               '-U',v['POSTGRES_USER'],'-d',v['POSTGRES_DB'],'-c',sys.argv[1]],env=e,check=True)
PY
}
nas_sql "SELECT current_database(),current_user,inet_server_addr(); SELECT count(*),min(vector_dims(embedding)),max(vector_dims(embedding)) FROM vector_store;"
# Ejecutar SOLO después de detener todas las APIs/ingestas y verificar el respaldo:
nas_sql 'BEGIN; SET LOCAL lock_timeout = '\''5s'\''; LOCK TABLE vector_store IN ACCESS EXCLUSIVE MODE; DO $$ BEGIN IF current_database() <> '\''r4r_rag'\'' OR (SELECT count(*) FROM vector_store) <> 1375 OR EXISTS (SELECT 1 FROM vector_store WHERE vector_dims(embedding) <> 768) THEN RAISE EXCEPTION '\''Baseline changed; stop and take a new backup'\''; END IF; END $$; DELETE FROM vector_store; COMMIT;'
```

El DELETE anterior no borra fuentes/chunks. Flyway V4 cambia la columna al arrancar
el CLI; `replace-all` reemplaza el corpus completo en la transacción de ingesta.
Si falla, mantener la API detenida y repetir la ingesta o restaurar el respaldo.
No arrancar la API nueva contra vectores Nomic ni la antigua contra Qwen.

```bash
r4r_cli() {
RAG_EMBEDDING_MODEL=qwen3-embedding:0.6b RAG_EMBEDDING_DIMENSIONS=1024 \
RAG_EMBEDDING_EXPECTED_DIGEST=ac6da0dfba84a81fdbfbaf330198c33cd77c4cdfc53e8bc50eb581914a15621d \
java -Dloader.main=com.riansares.r4r.ingestion.KnowledgeIngestionCli \
  -cp target/r4r-spring-ai-rag-0.3.4.jar org.springframework.boot.loader.launch.PropertiesLauncher \
  --r4r.knowledge.root=knowledge --r4r.catalog-on-startup=false \
  --r4r.rag.warmup.on-startup=false --r4r.rag.vector.primary=pgvector \
  --r4r.rag.vector.failover-enabled=false --r4r.rag.vector.s3vectors.enabled=false \
  --r4r.rag.vector.titan.enabled=false "$@"
}
r4r_cli --spring.flyway.enabled=true --r4r.ingestion.index=true --r4r.ingestion.replace-all=true
nas_sql 'SELECT (SELECT count(*) FROM knowledge_sources) AS sources,(SELECT count(*) FROM knowledge_chunks) AS chunks,(SELECT count(*) FROM vector_store) AS vectors; SELECT vector_dims(embedding),count(*) FROM vector_store GROUP BY 1;'
r4r_cli --spring.flyway.enabled=false \
  --spring.datasource.hikari.read-only=true \
  '--spring.datasource.hikari.connection-init-sql=SET default_transaction_read_only=on' \
  --r4r.ingestion.export-s3="$R4R_MIGRATION_BACKUP/qwen3-current.json"
bash lambda-s3-vectors/deploy.sh \
  --vectors "$R4R_MIGRATION_BACKUP/qwen3-current.json" \
  --replace-index --backup-dir "$R4R_MIGRATION_BACKUP"
bash scripts/deploy-synology.sh
```

El script comprueba la identidad `r4r-codex-deployer`, cuenta y compara todos los
vectores y metadatos tras importar. Necesita los permisos ya existentes para
DeleteIndex/CreateIndex/PutVectors/ListVectors/GetIndex y UpdateFunctionCode.
El reemplazo real se ejecutó con `--profile default`, verificando la cuenta
124021120482. No se modificaron permisos ni IAM.
Si solo falla una importación parcial, repetir con `--vectors ...` **sin**
`--replace-index`: exige 1024/cosine y ausencia de claves obsoletas. Si la eliminación
termina pero CreateIndex falla, recrear explícitamente el índice vacío con la
configuración respaldada cambiando únicamente `dimension` a 1024 antes de reintentar.
El script no borra ni recrea el bucket. Lambda conserva nombre, rol, transporte y protocolo.

## Comprobaciones reales después del corte

```bash
RAG_EMBEDDING_LIVE_URL=http://192.168.1.207:11434 \
  mvn verify -Dr4r.s3.lambda.live=true -Dit.test=S3LambdaLiveIT
RAG_LIVE_TEST=true RAG_LIVE_EN_TEST=true RAG_TEST_BASE_URL=http://192.168.1.203:18080 \
  mvn verify -Pllm-it -Dr4r.test.llm.enabled=true \
  -Dit.test=RagLiveQuestionsIT,RagLiveEnglishQuestionsIT \
  -Dr4r.test.case-ids=case-01,case-02,case-11
RAG_TEST_BASE_URL=http://192.168.1.203:18080 \
  python3 py-scripts/r4r_capture_evaluation.py --http-only \
  --run-id qwen3-migration --case-ids case-01,case-02 --languages es,en
```

El último comando conserva RAG y auditoría si hay `auditId`; no habilita un auditor
que esté desactivado. Si la API está configurada con OpenAI, requiere consentimiento
explícito con `--allow-openai`; no cambia modelos ni cuota. Comprobar el proveedor
real y fuentes pertinentes, no solo HTTP 200. Estas pruebas **no se han ejecutado**
contra un corpus Qwen; la API antigua no demostraría la migración.

Para el failover, abrir sesión SSH interactiva y mantener la restauración en el mismo
shell. No detener Docker/PostgreSQL del PC. Confirmar antes que `r4r-postgres` es
el contenedor de esta base en el NAS y que la API tiene habilitado el transporte Lambda:

```bash
ssh -t capi@192.168.1.203
export PATH=/usr/local/bin:/var/packages/ContainerManager/target/usr/bin:$PATH
sudo -v
trap 'sudo docker start r4r-postgres' EXIT HUP INT TERM
sudo docker stop r4r-postgres
# Desde otra terminal del PC: repetir los casos seleccionados ES/EN anteriores.
# Comprobar provider=s3vectors y lambdaInvokeMs en las métricas del servidor/SSE.
sudo docker start r4r-postgres
trap - EXIT HUP INT TERM
exit
```

Si se pierde SSH de forma brusca, verificar manualmente que PostgreSQL se ha
restaurado. Una prueba directa de Lambda no demuestra por sí sola failover de la API.

## Recuperación

Mantener la API detenida. El respaldo contiene `s3-restore-index.json` (CreateIndex
768), `s3-vectors.json` (PutVectors en lotes máximos de 100), `lambda-code-nomic.zip`
(UpdateFunctionCode con RevisionId actual) y el dump PostgreSQL completo.
Restaurar únicamente el índice RAG citado, sin eliminar el bucket. Restaurar el dump
solo en `r4r_rag` durante mantenimiento, nunca en otra base: `pg_restore --clean
--if-exists --single-transaction --no-owner` con las mismas credenciales cargadas
por la función anterior. Esto devuelve también Flyway al estado respaldado; restaura
el conjunto de la base, por lo que exige que no haya escrituras ajenas desde el corte.
Restaurar después la configuración privada Nomic/768 y la imagen anterior de API.
No mezclar una restauración parcial 768 con la configuración Qwen/1024.

## Validaciones ejecutadas

- `mvn verify`: 547 unitarias y 86 de integración pasan; 27 opt-in omitidas,
  cero fallos. Las pruebas reales PostgreSQL/AWS/LLM permanecen opt-in.
- `mvn -f lambda-s3-vectors/pom.xml clean verify`: 9 pruebas pasan.
- `python3 -m unittest discover -s lambda-s3-vectors -p test_deploy.py -v`: 3 pasan, AWS simulado.
- `python3 -m unittest discover -s py-scripts -p test_compose_configuration.py -v`: pasa.
- Migración V4 en tablas **temporales** del NAS: conversión vacía correcta y rechazo
  con una fila antigua; rollback en ambos experimentos, producción sin cambios.
- Prueba real de embeddings en los tres hosts: 1024 componentes finitos/no nulos.
- Ejecutada: reingesta Qwen y consultas directas ES/EN en PostgreSQL; 0 discrepancias
  documentales/vectoriales. S3: 1.375 vectores/1024 y metadatos verificados; Lambda
  directa y remota equivalentes en ES/EN. Pendientes: despliegue API por el usuario
  y failover end-to-end. No confundir búsqueda vectorial con generación RAG.
