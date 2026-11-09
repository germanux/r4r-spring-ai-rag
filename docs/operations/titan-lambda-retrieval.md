# R4R: retrieval privado con Titan V2 y Lambda

Estado comprobado: 9 de noviembre de 2026. **Despliegue parcial; retrieval real pendiente de desbloqueo de Bedrock.** No implica recuperación completa del backend NAS ni de la generación.

## Arquitectura e implementación

Invocación privada síncrona IAM → `RetrievalHandler` Java 21 → `BedrockRuntimeClient.InvokeModel` → `S3VectorsClient.QueryVectors` → textos/citas. Sin Spring Boot, PostgreSQL, Nomic, Ollama, VPC, API pública, DNS ni dependencia del NAS en este servicio. El backend publicado sigue dependiendo de Synology.

Módulo Maven independiente `lambda-titan/`, AWS SDK 2.49.1, clientes estáticos reutilizados, HTTP URLConnection, timeout por API de 10 s, intento de 8 s, conexión de 2 s, máximo 2 intentos SDK. Las dos APIs secuenciales tienen un presupuesto nominal de 20 s dentro del timeout Lambda de 30 s; paginaciones adicionales siguen limitadas por el timeout Lambda. Pregunta de 1–10000 caracteres, payload ≤64 KiB, topK entero entre 1 y 20. Errores AWS no se convierten en resultados vacíos. Los logs propios contienen tipo de error, identificador y tiempos, nunca pregunta, documentos o mensajes SDK. El runtime añade su stack trace saneado.

`amazon.titan-embed-text-v2:0`, `dimensions=1024`, `normalize=true`. Se rechazan vectores vacíos, de dimensión incorrecta o no finitos. Respuesta: `documents` con `id`, `text`, `source`, `headingPath`, `ordinal`, `score` y metadatos completos; `metrics` con `embeddingMs`, `vectorSearchMs`, `totalMs`; proveedor, `abstained` y estado de calibración. Score = similitud coseno `1-distance`, rango [-1,1], independiente de la conversión/umbral Nomic. No representa probabilidad de respuesta correcta.

Por defecto no hay umbral Titan: `calibration=uncalibrated`. Una lista no vacía no demuestra relevancia. `TITAN_MIN_SIMILARITY` permite configurar un umbral propio después de evaluación humana ES/EN; no se ha calibrado ni habilitado en AWS. No hay generación ni evaluación automática fiable de abstención semántica todavía.

## Recursos y resultados AWS reales

Todos en cuenta `124021120482`, región `eu-north-1`. Identidad comprobada exactamente: `arn:aws:iam::124021120482:user/r4r-codex-deployer`. CLI siempre con `--profile r4r-codex --region eu-north-1`. No se inspeccionaron archivos de credenciales.

| Recurso | Resultado |
| --- | --- |
| Bucket existente `amzn-r4r-vectors` | Confirmado por GetIndex/ListIndexes, coincide con configuración/export del repositorio |
| Índice original `amzn-r4r-vector-index` | 768, float32, cosine, 282 claves únicas. Solo lectura; textos/metadatos comparados antes/después y exactamente iguales |
| Nuevo índice `r4r-titan-v2-1024` | Creado y confirmado: 1024, float32, cosine, `source_text` no filtrable, AES256; **0 vectores** |
| Lambda `r4r-titan-retrieval` | Creada; Active / Successful; Java 21 x86_64, 1024 MB, 30 s, sin VPC |
| Rol existente `r4r-lambda-execution` | Usado sin modificaciones IAM |
| Logs `/aws/lambda/r4r-titan-retrieval` | Creados, consultables mediante FilterLogEvents, retención 7 días confirmada |
| Concurrencia reservada 2 | **No aplicada**: PutFunctionConcurrency rechazado por límite de capacidad de cuenta |

ARN función: `arn:aws:lambda:eu-north-1:124021120482:function:r4r-titan-retrieval`.
ARN índice: `arn:aws:s3vectors:eu-north-1:124021120482:bucket/amzn-r4r-vectors/index/r4r-titan-v2-1024`.
SHA256 base64 del código desplegado: `6jAUdmzFtVk8YmKTiyurpaWM/1uGljEgZAv2p989ya0=`.

No se ha activado provisioned concurrency ni SnapStart. La función conserva concurrencia compartida de cuenta: la reserva de 2 **no limita actualmente sus ejecuciones**. No se han creado triggers ni endpoints públicos.

### Bloqueos externos verificables

1. Una única llamada real `bedrock-runtime invoke-model` devolvió `AccessDeniedException`: **“Your account is currently being verified. Verification normally takes less than 2 hours.”** Acción: `bedrock:InvokeModel`, recurso `arn:aws:bedrock:eu-north-1::foundation-model/amazon.titan-embed-text-v2:0`. AWS no identifica un permiso IAM ausente; requiere completar la verificación de cuenta. El mensaje indica contactar `aws-verification@amazon.com` si persiste después de dos horas. No se envió ningún mensaje ni se repitió la llamada. El acceso efectivo de Bedrock desde el rol Lambda sigue sin probar.
2. `lambda:PutFunctionConcurrency` sobre la función autorizada devolvió `InvalidParameterValueException`: **“Specified ReservedConcurrentExecutions for function decreases account's UnreservedConcurrentExecution below its minimum value of [10].”** Requiere capacidad de concurrencia disponible en la cuenta. No se modificaron otras funciones, reservas ni cuotas. No es una denegación IAM.

## Corpus y actualización reanudable

Se inspeccionaron Markdown de `knowledge/`, exportaciones `knowledge/struct-data/r4r-s3vectors*.json`, utilidades `py-scripts/r4r-vector-diagnostic.py`, mapper y adaptador S3 existentes. Reconstruir con el chunker actual podría cambiar límites y contexto de headings. Se seleccionó **`r4r-s3vectors-import.json`**, porque sus 282 claves y todos sus metadatos coinciden exactamente con la exportación remota actual (incluye expansiones `r4r_context`). El otro export `r4r-s3vectors-aws.json` tiene las mismas claves pero metadatos diferentes: no se seleccionó. Ocho fuentes, 141794 caracteres / 144768 bytes UTF-8 de texto.

Manifest SHA256 canónico de claves/metadatos normalizados según `corpus.fingerprint`:
`034703cf21bad0d54c6c4d246ad473188ef9138c6de2d537c0a17de44e0b3ed6`.

`corpus.py` no usa PostgreSQL ni modifica origen. Conserva IDs, texto exacto, source, ordinal, headings y contexto. Omite arrays vacíos/null; valida procedencia y presupuestos de metadatos. Añade hash del contenido y modelo. El registro remoto con misma clave/metadatos actúa como checkpoint durable: permite continuar tras interrupción sin repetir embeddings completados. PutVectors sobrescribe la misma clave sin duplicados. Tras cada escritura compara metadatos y float32 leídos; al finalizar verifica conjunto exacto y conteo. Reintentos CLI standard, máximo 3; AccessDenied no se sortea. Claves remotas inesperadas causan parada, nunca eliminación.

Para actualizar: revisar un nuevo export contra el corpus vigente, ejecutar primero dry-run, usar `--source archivo.json --ingest` y conservar el manifest de salida. Si hay documentos eliminados, reconciliar manualmente: esta fase no autoriza borrar vectores. La reanudación confía en checkpoints remotos creados por este importador; no es una auditoría criptográfica contra modificaciones externas de sus vectores.

## Reproducción

Desde raíz, con AWS CLI v2, Python 3, Java 21 y Maven:

```bash
aws sts get-caller-identity --profile r4r-codex --region eu-north-1
mvn clean verify
mvn -f lambda-titan/pom.xml clean verify
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s lambda-titan -p 'test_*.py'
python3 lambda-titan/corpus.py
```

Tras resolver la verificación Bedrock, probar una sola llamada antes de ingesta:

```bash
aws bedrock-runtime invoke-model --model-id amazon.titan-embed-text-v2:0 \
  --body '{"inputText":"Información para una reforma","dimensions":1024,"normalize":true}' \
  --cli-binary-format raw-in-base64-out --profile r4r-codex --region eu-north-1 /tmp/titan-probe.json
python3 -c 'import json; x=json.load(open("/tmp/titan-probe.json")); assert len(x["embedding"]) == 1024'
python3 lambda-titan/deploy.py
python3 lambda-titan/corpus.py --ingest
python3 lambda-titan/evaluate.py --run --output /tmp/r4r-titan-evaluation.json
```

`deploy.py` verifica identidad, crea o actualiza exclusivamente índice/función/logs indicados, espera estado Active y configura reserva 2. Se detiene ante errores reales; actualmente el último paso seguirá bloqueado por capacidad de cuenta. En actualización usa RevisionId para el código; no altera umbrales existentes. No ejecutarlo simultáneamente desde varios procesos.

Invocación privada tras ingesta:

```bash
aws lambda invoke --function-name r4r-titan-retrieval \
  --payload '{"question":"¿Qué información hace falta para una reforma?","topK":5}' \
  --cli-binary-format raw-in-base64-out --profile r4r-codex --region eu-north-1 /tmp/retrieval.json
aws logs filter-log-events --log-group-name /aws/lambda/r4r-titan-retrieval \
  --profile r4r-codex --region eu-north-1
```

Comprobar `FunctionError`, además del StatusCode 200 del transporte. Los scripts usan perfil/región explícitos en cada llamada; dentro de Lambda el SDK usa el rol de ejecución, sin perfil local.

## Pruebas y mediciones

- Backend `mvn clean verify`: BUILD SUCCESS, Surefire 336 pruebas sin fallos; Failsafe 35, 24 omitidas por perfiles externos, sin fallos. Primer intento en sandbox falló por attach Mockito; ejecución fuera del sandbox pasó sin alterar producto ni tests.
- Lambda `mvn -f lambda-titan/pom.xml clean verify`: BUILD SUCCESS, 12 pruebas sin AWS: petición Titan, decodificación, float32, procedencia/citas, input inválido, ausencia de resultados, umbral independiente, errores Bedrock/S3, timeouts de ambas APIs y saneamiento de logs.
- Python: 5 pruebas offline de metadatos, vectores y manifest; dry-run valida 282 documentos.
- Una llamada Titan real: denegada por verificación de cuenta; sin embedding ni medida válida.
- Una invocación Lambda real con pregunta vacía: error esperado `Invalid retrieval request`, confirma empaquetado, carga del handler y logging; no llama Bedrock. RequestId `bc39096b-9335-4776-977c-927456cca490`. **No demuestra retrieval**.
- CloudWatch: Init 1314.02 ms; Duration 318.37 ms; Billed Duration 1633 ms; máximo de memoria 146 MB; validación propia totalMs 52.373091. No extrapolar estos tiempos a embeddings/búsqueda. `embeddingMs` y `vectorSearchMs` reales: pendientes.
- `evaluate.py`: 12 casos ES/EN (8 sobre presupuesto, humedad, cobertura y baños; 4 negativos sobre medicina/espacio). Preparados, no ejecutados. Revisar textos/citas manualmente; separar datos de calibración de validación antes de fijar umbral. No afirmar calidad ni abstención sin esa revisión.
- Presupuesto inicial: 1 intento Titan + 1 invocación Lambda consumidos; no se ejecutaron consultas semánticas. Máximo 50 llamadas iniciales de evaluación; los 282 embeddings de ingesta se contabilizan aparte. No repetir automáticamente la batería.

## Integración Spring Boot: pendiente por bloqueo concreto

Se revisaron `KnowledgeVectorStoreConfiguration`, `FailoverKnowledgeVectorStore`, `S3VectorsKnowledgeStore` y `S3VectorMetadataMapper`. La condición solicitada —demostrar retrieval real antes de integrarlo— sigue bloqueada por InvokeModel y un índice Titan vacío. No se añadió un fallback no probado al backend publicado. Su configuración, generación, SSE, cuotas y rutas siguen intactas; nada desplegado en Synology.

Después de resolverlo: decorar el `KnowledgeVectorStore` de lectura existente con un adaptador Lambda activado por propiedad independiente `enabled=false`; escrituras siempre delegadas a PostgreSQL. Invocar solamente ante excepciones técnicas clasificadas de embeddings/vector store tras agotar la ruta vigente. Propagar cancelaciones, validación e interrupciones; una lista vacía retorna vacía y nunca activa fallback. Usar `LambdaClient` independiente del router Nomic, timeout dentro del presupuesto SSE, comprobar FunctionError y reconstruir `MarkdownChunk(source, headingPath, ordinal, text)` para las citas existentes. No pasar minScore Nomic al servicio Titan. Tests offline deben cubrir respuesta vacía sin fallback, caída técnica, cancelación, fallo Lambda, citas y escrituras. El principal de ejecución del backend necesitará `lambda:InvokeFunction` sobre el ARN exacto; no se comprobó ni cambió su identidad o configuración privada.

IAM declarado por el propietario: deployer con InvokeModel, lectura Nomic, creación/escritura Titan, operaciones Lambda/logs y PassRole al rol indicado. Rol Lambda solo InvokeModel, lectura del índice Titan y logs básicos. No se solicitó inspección IAM administrativa ni se cambiaron políticas.

## Costes y límites

No hay consumo provisionado permanente. Estimación ilustrativa de cómputo: 50 ejecuciones de 30 s a 1 GB = 1500 GB-s; usando $0.0000166667/GB-s y $0.20/millón de peticiones de los ejemplos oficiales, unos **$0.02501**, excluyendo Bedrock, S3 Vectors, logs, impuestos y free tier. El smoke test de 1.633 GB-s representa aproximadamente $0.0000274 con esas mismas tarifas de referencia, no una factura.

Ingesta: 282 peticiones Titan, unos 35448 tokens si se supone 4 caracteres/token (solo aproximación; registrar inputTextTokenCount real). Coste = tokens / 1000 × tarifa Titan V2 regional. Vectores float32: 1155072 bytes, más textos, claves y metadatos. Coste de S3 Vectors incluye almacenamiento lógico, PUT, consultas/procesamiento y devolución; logs retienen siete días. No se ha obtenido una cotización regional ni datos de facturación. Confirmar tarifas Stockholm antes de uso continuado.

Referencias oficiales consultadas: [formato Titan V2](https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-titan-embed-text.html), [empaquetado Lambda Java](https://docs.aws.amazon.com/lambda/latest/dg/java-package.html), [Bedrock precios](https://aws.amazon.com/bedrock/pricing/), [Lambda precios](https://aws.amazon.com/lambda/pricing/), [S3 Vectors precios](https://aws.amazon.com/s3/pricing/).

## Git y aceptación

Rama recibida `feature/aws-bedrock-embed-titan`, HEAD `355573c`; un worktree, inicialmente limpio. No se cambió rama ni se escribió historia: AGENTS.md reserva los commits al controlador determinista y prohíbe git add/commit/checkout en sesiones modelo. No merge/push. Cambios limitados a nuevo módulo y este documento; corpus y código backend intactos.

Pendientes de aceptación: embedding Titan real 1024, ingesta 282, recuperación ES/EN, calibración/abstención, tiempos de APIs, reserva de concurrencia, integración backend tras demostración. El despliegue por sí solo no completa esos criterios.
