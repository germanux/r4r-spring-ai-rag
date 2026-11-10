# Titan V2: integración con la recuperación R4R

## Estado comprobado el 10 de noviembre de 2026

Rama de partida `feature/aws-bedrock-embed-titan`, HEAD `5cd8754`; árbol limpio antes
del trabajo. Tras la espera solicitada de cinco horas, a las **05:42 CET** se realizó
**una sola invocación mínima**, con `r4r-codex`, región `eu-north-1` y un intento total.
AWS devolvió `ThrottlingException: Too many requests`. No se repitió la invocación,
ni se enviaron preguntas válidas mediante Lambda para volver a invocar Bedrock.
Esto **no confirma** que haya terminado la verificación de la cuenta.

AWS confirmó la Lambda `r4r-titan-retrieval` activa, Java 21, 1024 MB, 30 segundos,
x86_64, rol `r4r-lambda-execution`. El índice `r4r-titan-v2-1024` existe, usa
float32/coseno/1024 dimensiones y tenía **0 vectores**. Nomic sigue en su índice
independiente `amzn-r4r-vector-index` de 768 dimensiones.

**Titan permanece desactivado por defecto.** La integración de código y sus pruebas
offline están completas; ingesta, calibración y recuperación semántica real siguen
pendientes de Bedrock. No se ha desplegado el backend ni cambiado la configuración
activa del NAS. No se modificaron IAM, concurrencia, índices ni datos Nomic.

## Auditoría y reutilización

| Necesidad | Implementación existente | Decisión Titan |
|---|---|---|
| Extracción y chunking | `MarkdownDocumentLoader`, `HeadingMarkdownChunker`, `MarkdownChunk` | No volver a ejecutar extracción/chunking: ya existen los 282 fragmentos exportados. Se reutiliza `MarkdownChunk`. |
| Ingesta PostgreSQL | `KnowledgeIngestionService`, `KnowledgeIngestionOrchestration`, `KnowledgeIngestionCli`, `IngestionConfiguration`, `KnowledgeIngestionResult` | Se conservan: gestionan documentos fuente, JDBC y transacciones. Invocarlos para Titan exigiría PG y volvería a fragmentar. El adaptador Java lee exclusivamente el export ya validado. |
| Metadatos y espacio vectorial | `S3VectorMetadataMapper`, `S3VectorScoreMapper` | Mismas clases Java en Lambda, importador y backend. Se conserva el marcador `r4r_context` y la validación de procedencia/presupuestos de bytes. |
| Recuperación y failover | `KnowledgeVectorStore`, `FailoverKnowledgeVectorStore`, `PostgresAccessGate`, `KnowledgeVectorStoreConfiguration`, `S3VectorsKnowledgeStore` | Se añade un adaptador de transporte Lambda y un tercer proveedor opcional al router existente. |
| Generación y citas | `CitedRagService`, `GenerationRouter`, `Citation`, DTO REST/SSE, `RagProgress` | Reutilizados. No se crea otra generación LLM, API ni estructura de citas. |
| Evaluación ES/EN | `questions-es.json`, `questions-en.json` | Una fuente por idioma, compartida por los tests Java, `r4r_live_failover_validation.py` y la herramienta Lambda mediante `r4r_evaluation_cases.py`. |
| Herramientas operativas | `scripts/deploy-synology.sh`, `scripts/export-docker-spring.sh`, diagnósticos de `py-scripts/` | Se conservan. No se ejecuta el despliegue NAS. `scripts-ai/export-evaluation.sh` exporta evidencia de agentes, no es una ingesta ni una batería semántica RAG. |

Duplicaciones eliminadas:

- `corpus.py` deja de implementar validación, metadatos, embeddings e importación.
  Es un lanzador compatible del importador Java. Sus antiguos invariantes se prueban
  ahora en `CorpusImporterTest` y las pruebas Java de metadatos/embeddings.
- `RetrievalHandler.document()` utiliza el mapper existente, sin interpretar
  `Document.toString()` ni mantener otra validación de citas.
- El adaptador `TitanEmbeddingClient` comparte solicitud y validación de embeddings
  entre Lambda e importador; no transforma vectores Nomic en vectores Titan.
- Se eliminan las 12 preguntas duplicadas de `evaluate.py`; se leen las 30 originales.
- `deploy.py` actualiza solamente el código de la función existente. Se elimina la
  solicitud de concurrencia reservada 2 y el aprovisionamiento de índices/logs/IAM.

Para mantener el JAR Lambda ligero, su POM incorpora mediante `build-helper` y una
lista explícita del compilador **tres fuentes Java existentes**: `MarkdownChunk`,
`S3VectorMetadataMapper`, `S3VectorScoreMapper`. Hay una sola implementación física;
no se copia código y no se incorpora Spring. Esto conserva el build Maven ordinario
del backend sin convertir el repositorio en un reactor nuevo ni exigir `mvn install`.
Cualquier cambio compartido debe validarse con ambos comandos Maven.

## Comportamiento Spring

Orden normal: **PGVector → S3/Nomic → Lambda Titan**. Se intenta el siguiente proveedor
ante una indisponibilidad técnica. Una búsqueda válida vacía termina la recuperación
sin forzar otro proveedor. Los errores de esquema, autenticación SQL, configuración,
metadatos y cancelación conservan las reglas anteriores.

Si Nomic está inaccesible en la consulta PG, Titan puede recuperar directamente:
PGVector y el respaldo S3 utilizan el mismo embedding Nomic, por lo que repetir
Nomic en S3 no resolvería esa indisponibilidad. Esto solo se habilita cuando Titan
está configurado y el failover existente está activo. Las escrituras siguen en PG.
El modo `s3-readonly` también permite Titan tras un fallo técnico de S3/Nomic.

`TitanLambdaKnowledgeStore` envía **texto + topK**, nunca embeddings Nomic. Invoca
sincrónicamente mediante AWS SDK, sin reintentos, con tiempo máximo configurado.
Valida `FunctionError` además del HTTP 200: un HTTP 200 de Lambda puede contener
un fallo de la función. Se rechazan otro modelo/índice/dimensión, resultados
sobrantes/duplicados, scores inválidos, procedencia contradictoria y métricas
inválidas. Los errores producen la respuesta controlada existente; no son resultados
vacíos exitosos ni exponen mensajes privados de AWS.

El handler genera el embedding Titan 1024, consulta solo el índice Titan y devuelve
IDs originales, scores, textos y metadatos. Spring aplica **un umbral propio Titan**,
reconstruye `MarkdownChunk` y utiliza la generación/citación existentes.
La calibración Nomic no se transfiere a Titan. Para activar se exige un umbral
explícito; la variable Lambda heredada `TITAN_MIN_SIMILARITY`, si se utiliza, debe
ser coherente con esa calibración. La activación recomendada filtra en Spring y deja
esa variable Lambda sin establecer.

Métricas:

- `embeddingMs`: tiempo informado por Lambda, sumado al de intentos locales previos.
- `vectorSearchMs`: búsqueda remota aislada; sigue siendo nulo para PG/Nomic donde
  Spring AI no facilita ese temporizador separado.
- `lambdaInvokeMs`: tiempo del SDK en Spring, también si falla o expira la llamada;
  incluye transporte y cold start, se solapa con los tiempos internos y **no se suma** a ellos.
- `totalMs`: tiempo total de la petición RAG en Spring. Lambda también devuelve su
  propio `metrics.totalMs`, validado por el adaptador, con un ámbito diferente.

En simulaciones, los tiempos internos suministrados por los mocks no son mediciones
AWS y pueden superar el tiempo de ejecución local del test. Ni estas métricas ni la
antigua validación de entrada de 318 ms demuestran recuperación semántica real.

## Configuración preparada, sin activar

Se sigue la convención existente: `application.yml`, `docker-spring/.env.base`
(público) y defaults del Compose autocontenido. No existe un `.env.example` canónico;
no se introduce otra plantilla divergente. No se modifica el `.env` privado.

```properties
RAG_TITAN_ENABLED=false
RAG_TITAN_FUNCTION=r4r-titan-retrieval
RAG_TITAN_REGION=eu-north-1
RAG_TITAN_TIMEOUT=5s
RAG_TITAN_MAX_TOP_K=20
RAG_TITAN_MIN_SIMILARITY=
```

El umbral vacío es intencionado mientras esté desactivado. Activar exige un valor
finito `[0,1]` elegido con evaluación real; timeout `[100ms,30s]`, topK `[1,20]`.
El topK solicitado por el servicio se limita al máximo Titan configurado.
Solo se admite la función indicada (nombre o ARN exacto) en `eu-north-1`.
Titan requiere S3 como principal o `RAG_VECTOR_FAILOVER_ENABLED=true` con
`RAG_S3_VECTORS_ENABLED=true`. Por defecto no se crea el cliente Lambda ni se
resuelven credenciales Titan. El perfil test bloquea también `RAG_TITAN_ENABLED`
heredado del entorno del desarrollador.

## Importador Java reanudable

Fuente única: `knowledge/struct-data/r4r-s3vectors-import.json`.

- SHA-256 del archivo: `89ab0be789a5399b58697d477169b577d21d11558cac13400d70d2f590e5c634`.
- 282 IDs únicos, 8 fuentes, 141794 caracteres originales.
- Manifiesto normalizado: `034703cf21bad0d54c6c4d246ad473188ef9138c6de2d537c0a17de44e0b3ed6`,
  **idéntico al de la herramienta anterior**.

El importador verifica el checksum completo antes de construir clientes AWS.
Ignora los embeddings Nomic del export, conserva keys/texto/metadatos y solo genera
embeddings Titan reales. Omite nulos/listas vacías incompatibles; no aparecen listas
headingPath vacías en este export. Añade `embedding_model` y `titan_content_sha256`.
Verifica dimensión, float32, coseno y configuración de metadatos del índice destino.

Cada upsert se verifica con GetVectors, comparando metadatos y los 1024 floats.
Al reanudar, incluso una fila con tags correctos debe devolver un vector válido de
1024 dimensiones. No se eliminan filas: keys inesperadas detienen la ejecución.
Las filas remotas son el checkpoint duradero, también si el proceso se interrumpe
tras la escritura. Un informe local se sustituye atómicamente después de cada éxito
y ante el primer error, con contadores, key y tipo de error, sin texto ni mensajes
SDK. Se detiene ante el primer fallo, incluidos throttling/denegación; un nuevo
intento solo se inicia explícitamente. El resultado final exige exactamente las
keys y los metadatos esperados. No se consulta PG ni se escribe en Nomic.

```bash
mvn -f lambda-titan/pom.xml clean verify
# Auditoría offline; no crea clientes ni llama AWS:
python3 lambda-titan/corpus.py
# Alternativa Java equivalente:
java -cp lambda-titan/target/r4r-titan-retrieval-1.0.0.jar \
  com.riansares.r4r.titan.CorpusImporter
# SOLO cuando Bedrock esté confirmado operativo; también sirve para reanudar:
python3 lambda-titan/corpus.py --ingest --report lambda-titan/target/import-report.json
```

El SDK del importador usa explícitamente `ProfileCredentialsProvider("r4r-codex")`
y `eu-north-1`, valida STS contra el deployer esperado y usa un solo intento AWS.
El handler utiliza exclusivamente el rol de ejecución de Lambda.

## IAM e identidad del NAS

Spring conserva la cadena de credenciales del SDK utilizada por S3. Compose pasa
credenciales de entorno al servicio `api`; no monta perfiles AWS ni copia las
credenciales del deployer. **El ARN efectivo del NAS no está verificado**: el intento
SSH en modo no interactivo devolvió `Permission denied (publickey,password)`.
No se inspeccionaron secretos ni se cambió configuración para resolverlo.

La política preparada [backend-invoke-titan.json](../../lambda-titan/iam/backend-invoke-titan.json)
concede únicamente `lambda:InvokeFunction` al ARN:
`arn:aws:lambda:eu-north-1:124021120482:function:r4r-titan-retrieval`.
Un administrador debe aplicarla al usuario/rol **real del backend**, si carece de
ese permiso. No al deployer como sustituto de la identidad de producción.
No se ha aplicado ninguna política ni repetido inspecciones administrativas IAM.

Diagnóstico preparado para el operador, en el NAS, con el JAR verificado disponible:

```bash
sudo docker cp r4r-titan-retrieval-1.0.0.jar r4r-api:/tmp/r4r-titan-diagnostic.jar
sudo docker exec r4r-api java -cp /tmp/r4r-titan-diagnostic.jar \
  com.riansares.r4r.titan.RuntimeIdentity
```

Este comando hace solo STS y muestra account/ARN. Debe ejecutarse **dentro del
contenedor existente**, heredando sus credenciales de runtime; no con un perfil de
despliegue del PC. No se ejecutó aquí. El archivo temporal no activa Titan ni cambia
la configuración del backend. `RuntimeIdentity` no imprime claves ni tokens.

## Activación y validación real, pendiente

1. Confirmar con AWS la disponibilidad de Bedrock. En una futura comprobación
   autorizada, realizar una única llamada mínima:

   ```bash
   AWS_MAX_ATTEMPTS=1 aws bedrock-runtime invoke-model \
     --profile r4r-codex --region eu-north-1 \
     --model-id amazon.titan-embed-text-v2:0 \
     --content-type application/json --accept application/json \
     --cli-binary-format raw-in-base64-out \
     --body '{"inputText":"Prueba Titan","dimensions":1024,"normalize":true}' \
     /tmp/r4r-titan-test.json
   ```

   Un throttling/AccessDenied no autoriza la ingesta ni demuestra activación. No se
   repitió esta llamada después del fallo observado en esta ejecución.

2. Ejecutar el importador anterior y exigir éxito del informe con 282 filas entre
   `imported` y `resumed`, cero errores. Recontar solo el índice Titan:

   ```bash
   aws s3vectors list-vectors --profile r4r-codex --region eu-north-1 \
     --vector-bucket-name amzn-r4r-vectors --index-name r4r-titan-v2-1024 \
     --query 'length(vectors)' --output json
   ```

3. Evaluar las **30 preguntas originales ES/EN**, seleccionar/calibrar el umbral y
   revisar textos, scores, procedencia, abstenciones y latencias reales. Ejemplo de
   umbral experimental (no una recomendación validada):

   ```bash
   python3 lambda-titan/evaluate.py  # solo enumera, offline
   python3 lambda-titan/evaluate.py --run --minimum-similarity 0.60 \
     --output lambda-titan/target/evaluation-real.json
   mvn verify -Ptitan-it -Dr4r.test.titan.minimum-similarity=0.60
   ```

   El evaluador se detiene si Lambda devuelve FunctionError; no continúa enviando
   preguntas tras un bloqueo. El tiempo `lambdaInvokeMs` de esta herramienta incluye
   además el arranque de AWS CLI, a diferencia del SDK del backend. Las pruebas
   `titan-it` ejercitan el adaptador Java real, exigen evidencia de dominio y
   abstención fuera del corpus; una calibración incorrecta hace fallar la batería.
   No generan respuestas LLM. La batería `llm-it` existente comprueba generación
   completa contra una API de pruebas dedicada.

4. Verificar la identidad real del backend y el permiso mínimo anterior. Mantener
   credenciales de despliegue fuera de producción.
5. Preparar la configuración del backend en un entorno de pruebas dedicado: activar
   el failover existente, Titan y su umbral validado. Ajustar su timeout con datos
   reales (por ejemplo 25s, dentro del límite y del presupuesto total del servicio).
6. Ejecutar REST/SSE con PG sano, PG caído y Nomic sano, PG/Nomic caídos con Titan
   sano, Lambda caída/timeout, todos fallidos y recuperación PG. Verificar citas,
   errores controlados y continuidad del proceso. Simular fallos en el entorno
   dedicado; no parar servicios de producción para esta prueba.
7. Ejecutar la batería completa y preparar el despliegue backend posterior:

   ```bash
   mvn clean verify -Ppostgres-it,aws-it,llm-it,titan-it \
     -Dr4r.test.titan.minimum-similarity=0.60
   mvn -f lambda-titan/pom.xml clean verify
   python3 -m unittest discover -s lambda-titan -p 'test_*.py'
   ```

   Requiere PG/pgvector de pruebas, credenciales/endpoints dedicados y LLM según
   [testing.md](../testing.md). Sustituir 0.60 por el umbral validado. No se ha
   ejecutado la batería real por el bloqueo actual ni se ha desplegado el backend.

Si cambia la Lambda, actualizar **solo su código** después de verificar el JAR:

```bash
python3 lambda-titan/deploy.py           # preview sin AWS
python3 lambda-titan/deploy.py --update  # perfil r4r-codex, eu-north-1
```

El despliegue valida identidad/configuración, usa revision-id y compara SHA-256
local/remoto. No crea recursos ni cambia configuración, roles, memoria, timeout,
retención de logs o concurrencia. Se mantiene concurrencia compartida.

## Fuentes de los contratos AWS

- [Invoke sincrónico y errores de función](https://docs.aws.amazon.com/lambda/latest/api/API_Invoke.html).
- [Parámetros Titan Embeddings](https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-titan-embed-text.html).
- [Titan V2 y dimensiones](https://docs.aws.amazon.com/bedrock/latest/userguide/titan-embedding-models.html).

Los recursos/estados de esta ejecución proceden de consultas AWS con el perfil
obligatorio, no de inferir la activación por la fecha ni de resultados simulados.
