# Evidencias RAG y auditoría independiente

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

Cada resultado incluye `httpResponse` íntegro y `capture`: pregunta, idioma solicitado,
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
traduce preguntas, no consulta PostgreSQL ni invoca a otro LLM. Espera dos segundos
entre preguntas y respeta `Retry-After` en 429. No reintenta generaciones fallidas.
Exige `minScore=0.63` y comprueba el proveedor real: no etiqueta un fallback como
Qwen3. Las opciones del proceso, no las variables del cliente HTTP, determinan la
configuración efectiva de la API. Terminar la instancia con captura al finalizar.

## Ejecutar una auditoría posteriormente

```bash
ollama run qwen3:8b < docs/evaluation/audit/qwen-evidence-2026-11-14/es-28.txt
```

Este comando **sí realiza una nueva inferencia**. Los prompts se han preparado,
pero el exportador y la ejecución de captura no ejecutan la auditoría. Las respuestas
guardadas son observaciones del RAG, no respuestas semánticamente aprobadas.

## Comprobaciones sin LLM

```bash
mvn verify
python3 -m unittest discover -s py-scripts -p test_r4r_capture_evaluation.py -v
```

Los tests comprueban contenido/etiquetas exactos frente al prompt, chunks no citados,
estados parciales y abstenciones, proveedor de la petición, exportación sin sobrescribir,
ausencia de evidencias en la respuesta pública y ausencia de juicios previos en auditoría.
