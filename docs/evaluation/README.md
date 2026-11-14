# Evidencias RAG y auditoría independiente

La auditoría pública opcional se describe en [Auditoría posterior del RAG](../operations/rag-audit.md).
La captura a disco de este documento sigue siendo explícita e independiente de esa funcionalidad.

- `runs/<run-id>/`: JSON de cada petición seleccionada y `summary.json`.
- `audit/<run-id>/`: un `.txt` por caso, listo para Ollama. Incluye pregunta,
  respuesta entregada, estado y **todos** los chunks suministrados al generador.
  No contiene valoraciones previas ni el estado esperado del test.

Los números de archivo son las posiciones actuales de las preguntas; cada JSON
conserva también su `caseId` original de `questions-es.json` / `questions-en.json`.
Estos bancos siguen siendo la única fuente de preguntas del exportador.

Ejecución conservada `qwen-evidence-2026-11-14`: 16 respuestas HTTP 200, 14
generaciones Qwen3:8b y dos abstenciones sin generación; 70 chunks (15 citados,
55 no citados), con `topK=5` y `minScore=0.63`. Se respetaron diez respuestas 429
antes de completar las consultas. Una petición inicial falló en JDBC antes de
generar; se comprobó el NAS mediante SELECT en solo lectura (1.375 vectores) y se
repitió la tanda. No se guardó ese fallo como una respuesta ni se inventó su contexto.
La instancia con captura se detuvo al terminar. No se ejecutó el auditor.

## Captura y formato

La captura está **desactivada por defecto**. Se activa únicamente para una API de
evaluación mediante `r4r.rag.evaluation.capture-directory`. No cambia la respuesta
HTTP ni publica chunks por un endpoint. No registra preguntas o documentos en los
logs operativos. Activarla exporta las peticiones de esa instancia: usarla sólo con
preguntas públicas seleccionadas y desactivarla al terminar.

`CitedRagService` usa la misma lista ordenada e inmutable de `MarkdownChunk` para
construir el prompt y crear `RagEvidenceSnapshot`. `RagEvidenceExporter` escribe un
JSON con nombre UUID por petición, sin hacer otra búsqueda. Los archivos definitivos
los conserva `py-scripts/r4r_capture_evaluation.py` bajo `runs/` tras comprobar que
coinciden con la respuesta HTTP. Una discrepancia o un error de exportación falla
explícitamente; no se reconstruyen evidencias a posteriori.

En modo captura, cada resultado incluye `httpResponse` (sin el token temporal `auditId`) y `capture`: pregunta, idioma solicitado,
respuesta validada, estado, citas, proveedor/modelo reales, salida original del modelo,
motivo del resultado, configuración `topK`/`minScore`/traducción y métricas disponibles.
`chunks` conserva `label`, `source`, `headingPath`, `ordinal`, `content` sin recortar,
`cited` (cita devuelta al usuario) y `referencedInGeneratedOutput` (etiqueta literal presente
en la salida original, incluso si fue rechazada). Las etiquetas siguen el orden del
prompt, sin renumerar sólo las citadas. `generatedOutput` puede ser JSON o texto.

`generationInvoked=false` implica cero chunks entregados, salida y proveedor nulos.
`outcome` distingue falta de evidencia y rechazo antes de generación. Si hubo una
generación posteriormente rechazada, se conservan sus chunks y salida original,
pero la respuesta HTTP sigue vacía y sin citas. `retrievedChunkCount` cuenta los
candidatos devueltos por el store antes del filtro opcional, no todos los candidatos
internos del reranker. El experimento mantiene ese filtro LLM desactivado.

Las métricas nulas significan que esa fase no se ejecutó o no dispone de temporizador
aislado. `retrievalIncludingEmbeddingMs` incluye embeddings; `totalMs` es del servicio
antes de escribir el archivo. `httpLatencyMs` incluye transporte y esperas por 429.

## Repetir la captura

Desde la raíz, compilar con `mvn verify`. Arrancar una instancia dedicada en un
puerto libre (detener antes la instancia de pruebas que ocupe 8080), reutilizando
los imports existentes de `.env.base` y `.env`; no copiar credenciales al comando:

```bash
java -jar target/r4r-spring-ai-rag-0.3.4.jar \
  --server.port=8080 \
  --spring.flyway.enabled=false --spring.sql.init.mode=never \
  --spring.ai.vectorstore.pgvector.initialize-schema=false \
  '--spring.datasource.hikari.connection-init-sql=SET default_transaction_read_only=on' \
  --r4r.catalog-on-startup=false --r4r.rag.warmup.on-startup=false \
  --r4r.rag.vector.primary=pgvector --r4r.rag.vector.failover-enabled=false \
  --r4r.rag.vector.s3vectors.enabled=false --r4r.rag.vector.titan.enabled=false \
  --r4r.rag.evidence-relevance.enabled=false --r4r.rag.openai.enabled=false \
  --r4r.rag.evaluation.capture-directory=/tmp/r4r-evidence-capture
```

En otra consola del mismo PC:

```bash
RAG_LIVE_TEST=true RAG_LIVE_EN_TEST=true \
RAG_TEST_BASE_URL=http://192.168.1.207:8080 RAG_OPENAI_ENABLED=false \
python3 py-scripts/r4r_capture_evaluation.py \
  --capture-dir /tmp/r4r-evidence-capture \
  --run-id nueva-ejecucion --include-controls
```

Se ejecutan 25–30 ES/EN; `--include-controls` añade 01 ES, 02 EN y 17 ES/EN.
Usar un `run-id` nuevo: no se sobrescriben ejecuciones anteriores. El script no
traduce preguntas ni consulta PostgreSQL. Si recibe `auditId`, solicita la auditoría
ya implementada en la API. Espera dos segundos
entre preguntas y respeta `Retry-After` en 429. No reintenta generaciones fallidas.
Exige `minScore=0.63` y comprueba el proveedor real: no etiqueta un fallback como
Qwen3. Las opciones del proceso, no las variables del cliente HTTP, determinan la
configuración efectiva de la API. Terminar la instancia con captura al finalizar.

## Ejecutar una auditoría posteriormente

```bash
ollama run qwen3:8b < docs/evaluation/audit/qwen-evidence-2026-11-14/es-28.txt
```

Este comando **sí realiza una nueva inferencia**, independiente del POST de auditoría
que puede ejecutar el exportador si la API ofrece `auditId`. Los archivos `.txt` no
se ejecutan automáticamente. Las respuestas
guardadas son observaciones del RAG, no respuestas semánticamente aprobadas.

## Comprobaciones sin LLM

```bash
mvn verify
python3 -m unittest discover -s py-scripts -p test_r4r_capture_evaluation.py -v
```

Los tests comprueban contenido/etiquetas exactos frente al prompt, chunks no citados,
estados parciales y abstenciones, proveedor de la petición, exportación sin sobrescribir,
ausencia de evidencias en la respuesta pública y ausencia de juicios previos en auditoría.

## Suites existentes con auditoría posterior

`RagLiveQuestionsIT` y `RagLiveEnglishQuestionsIT` mantienen sus comprobaciones RAG.
Después de cada consulta llaman al auditor sólo si existe `auditId`. Guardan el par
RAG/auditoría y un `summary.json` en `runs/<run-id>/`. Un fallo del auditor no falla
el test RAG; un fallo de las comprobaciones RAG sigue fallando como antes.

Selección por **caseId**, no por posición (por ejemplo, `case-23` ocupa la posición 28):

```bash
RAG_LIVE_TEST=true RAG_LIVE_EN_TEST=true \
RAG_TEST_BASE_URL=http://192.168.1.207:8080 \
mvn verify -Pllm-it -Dr4r.test.llm.enabled=true \
  -Dit.test=RagLiveQuestionsIT,RagLiveEnglishQuestionsIT \
  -Dr4r.test.case-ids=case-23 -Dr4r.test.run-id=audit-seleccion-nueva
```

Omitir `r4r.test.case-ids` ejecuta los 70 casos. `r4r.test.run-id` es opcional:
por defecto se crea un nombre único. `r4r.test.results-dir` permite cambiar el
directorio raíz de resultados. Las regresiones opt-in existentes se mantienen.

El exportador Python reutiliza los mismos bancos. Su modo original `--capture-dir`
mantiene la validación de contexto completo; `--http-only` no requiere captura de
chunks a disco en el servidor. Para los 70 casos:

```bash
RAG_LIVE_TEST=true RAG_LIVE_EN_TEST=true RAG_OPENAI_ENABLED=false \
RAG_TEST_BASE_URL=http://192.168.1.207:8080 \
python3 py-scripts/r4r_capture_evaluation.py --http-only \
  --all-cases --run-id audit-70-nuevo
```

Sustituir `--all-cases` por `--case-ids case-23,case-24` limita la selección;
`--languages es` o `en` limita el idioma. Sin selector se conservan 25–30 ES/EN,
y `--include-controls` añade los cuatro controles anteriores. Para una API con
OpenAI autorizado, usar `--allow-openai` expresamente. Ninguna variable del cliente
ni esa opción cambia la configuración de la API en ejecución. La auditoría debe
estar habilitada en el servidor; si no hay token se registra `SKIPPED` sin otra llamada.

Cada par conserva `httpResponse.status` original y `audit.recommendedStatus`,
`effectiveStatus`, `verdict`, `reason`, proveedor/modelo, duración interna y latencia
HTTP. Sólo se aplica una degradación coherente, concluyente y con motivo/veredicto
válidos. Nunca se transforma una recomendación de ascenso en un ascenso efectivo.
No se infieren metadatos ausentes: una API antigua que no los exponga queda señalada
como `ERROR`, conservando el RAG. Los tokens no se guardan en los archivos.

`summary.json` y la consola muestran resultados/contratos RAG, degradaciones propuestas
y aplicadas, auditorías fallidas/omitidas y tiempos medios separados. Un `PASS` RAG
significa las comprobaciones existentes, no una certificación semántica; el veredicto
del auditor permanece separado. Un `FAIL` del auditor no cambia las expectativas del test.

Las esperas de dos segundos y el `Retry-After` se conservan. El POST de auditoría sólo
se reintenta ante 429, nunca ante 503, timeout o transporte ambiguo: el token es de un
solo uso. La política anterior de reintentos de las consultas JUnit RAG no cambia.
