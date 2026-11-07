# Seguimiento de consultas RAG por SSE (Spring MVC)

## Compatibilidad y uso

Se mantiene `POST /api/rag/answers`, con su contrato JSON original. El nuevo endpoint
`POST /api/rag/answers/stream` acepta el mismo `RagQueryRequest`:

```bash
curl -N -i http://192.168.1.203:18080/api/rag/answers/stream \
  -H 'Origin: http://127.0.0.1:8000' \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  --data '{"question":"¿Qué tipo de reformas hace esta empresa?","responseLanguage":"es"}'
```

Este comando es para verificación manual una vez aplicado el cambio por el
procedimiento autorizado. No se ha desplegado esta implementación en Synology.

Se utiliza `SseEmitter`, sin WebFlux ni WebSocket. El frontend consume el POST con
`fetch` y `ReadableStream`. MyMemory sigue preparándose en el frontend antes del
POST; se envían `question`, `retrievalQuestion` y `responseLanguage` como antes.
La traducción del backend solo se ejecuta si sigue siendo necesaria.

Los POST REST y SSE comparten la protección de origen y las cuotas existentes.
Se comprueban los preflight con `Content-Type`/`Accept`, producción, desarrollo y
el origen del frontend Synology. El filtro admite dispatch asíncrono.

## Eventos reales

- `REQUEST_RECEIVED`.
- `TRANSLATION_STARTED` / `TRANSLATION_COMPLETED`: solo al ejecutar el traductor del backend.
- `RETRIEVAL_STARTED` / `RETRIEVAL_COMPLETED`: llamada real al almacén vectorial.
- `EMBEDDING_STARTED` / `EMBEDDING_COMPLETED`: llamada real al modelo de embeddings,
  anidada dentro de esa recuperación en Spring AI 1.0.
- `GENERATION_STARTED`: cada intento que realmente llega al modelo, después de su sondeo.
- `PROVIDER_FAILED`: fallo técnico o sondeo negativo del proveedor intentado.
- `FAILOVER_STARTED`: intento del siguiente proveedor configurado.
- `GENERATION_COMPLETED`: el modelo devuelve una respuesta que pasa a validación.
- `VALIDATION_COMPLETED`: comprobación de evidencias, abstención y/o citas.
- `ANSWER_READY`: respuesta completa.
- `ERROR`: error controlado o timeout; nunca contiene la excepción original.

Los eventos JSON incluyen `stage`, `elapsedMs` acumulado y, cuando se conocen,
`provider`, `model`, `documentsFound`, `durationMs` y `message`. Los nombres de fase
son estables y el frontend los traduce. No se envían prompts, fragmentos privados,
URLs internas ni credenciales. Los proveedores se describen como PC principal,
Portátil ASUS u OpenAI.

`documentsFound` cuenta los documentos/fragmentos devueltos por Spring AI después
 de Top-K y score mínimo; no es el número de archivos distintos ni de citas finales.
La abstención por cero evidencias no emite ninguna fase de generación.

`ANSWER_READY` conserva en el nivel superior `answer`, `abstained`, `citations` y
`provider`, con los mismos tipos del REST original. Añade `stage`, `elapsedMs`, el
recuento recuperado y `metrics`. Los eventos anteriores usan un nombre público de
proveedor; el evento final usa el objeto de proveedor del contrato REST.

## Métricas y limitación de la búsqueda

Se inspeccionó la implementación de `PgVectorStore.doSimilaritySearch` de Spring AI
1.0.0: llama a `getQueryEmbedding`, después ejecuta `JdbcTemplate.query` y convierte
las filas a documentos. No ofrece un callback público que delimite solo el SQL.

- `embeddingMs`: tiempo observado de la única llamada Spring AI al embedding de
  consulta, incluyendo su transporte/procesamiento. No es tiempo interno de GPU.
- `retrievalIncludingEmbeddingMs`: tiempo de recuperación completo, con embedding,
  consulta vectorial, conversión y costes de observación.
- `generationMs`: tiempo de la fase de generación, incluidos sondeos, intentos y
  failover. No es una medida aislada de inferencia del servidor LLM.
- `totalMs`: tiempo del pipeline backend desde la creación del seguimiento, sin la
  traducción externa previa ni el transporte final al navegador.
- `vectorSearchMs`: **null**. No se obtiene restando tiempos ni se presenta una
  estimación como SQL exacto. Se conserva la métrica conjunta para evitar copiar o
  sustituir internamente la búsqueda de Spring AI.

Las métricas no ejecutadas/no observadas son `null`; cero solo puede proceder de
una duración realmente medida. Se registran también en logs sin datos de preguntas.
El frontend muestra el tiempo total observado en el navegador y las duraciones de
fase recibidas, sin porcentajes o progreso temporal inventado.

La instrumentación conserva el tipo de bean `OllamaEmbeddingModel`, sus opciones,
servidor y dimensiones, y evita una segunda instancia de autoconfiguración. Los
tests usan el `PgVectorStore` real de Spring AI y comprueban exactamente una llamada
al API de embeddings y una consulta JDBC por recuperación.

## Concurrencia, cancelación y límites

- Executor SSE dedicado: cuatro workers, sin cola. Saturación devuelve HTTP 503
  antes de iniciar otro trabajo. No comparte executor con la precarga de modelos.
- Cada sesión posee su tarea, deadline y heartbeat. La desconexión/cierre/timeout
  cancela su `FutureTask`, interrumpe el worker y elimina la sesión y los timers.
  Los timers cancelados se retiran de la cola del scheduler.
- Se comprueba la cancelación entre fases, antes de generar y antes de cada nuevo
  proveedor. Una cancelación no dispara failover ni otro gasto remoto.
- La interrupción es cooperativa: una llamada bloqueante ya enviada depende también
  de los límites de su cliente/driver. No se promete detener instantáneamente una
  inferencia ya iniciada en Ollama/OpenAI ni recuperar su coste. No hay retries SSE.
- `RagProgress` se pasa explícitamente al worker y al router. El único contexto
  auxiliar `ThreadLocal` se instala y retira con `finally` alrededor de la llamada
  síncrona al VectorStore, en ese mismo worker. No se propaga desde el hilo MVC.
  Los contextos del router y del traductor se limpian antes/después de la petición.

Configuración nueva, sin modificar Compose ni el `.env` activo:

| Variable | Predeterminado |
| --- | --- |
| `RAG_SSE_TIMEOUT` | `180s` |
| `RAG_SSE_HEARTBEAT_INTERVAL` | `10s` |
| `RAG_EMBEDDING_CONNECT_TIMEOUT` | `2s` |
| `RAG_EMBEDDING_READ_TIMEOUT` | `30s` |

Los límites de generación y el orden PC → portátil → OpenAI continúan configurados
como antes. Los nuevos límites del cliente de embeddings acotan su espera; no
cambian el modelo, las opciones de embeddings ni realizan una segunda operación.

## Frontend y proxies

Se muestra una lista ES/EN de las fases recibidas. Un fallo y el siguiente intento
se consolidan en un único aviso comprensible. La respuesta y las citas usan el
renderizado existente. No se recarga ni se pierde contenido al cambiar idioma.

El parser soporta UTF-8 dividido entre chunks, LF/CRLF, `data` multilínea y
comentarios heartbeat. EOF sin `ANSWER_READY`, JSON inválido, `ERROR` y timeout
terminan con error controlado y desbloquean la UI. `pagehide` aborta la petición.

Solo un HTTP 404/405/406/501 de la ruta SSE permite consultar el REST del mismo
endpoint seleccionado. Una consulta empezada o interrumpida nunca se repite
por REST automáticamente. No hay fallback entre servidores o protocolos.

Se envían `Cache-Control: no-cache, no-transform` y `X-Accel-Buffering: no`.
La compatibilidad del proxy real de Synology/Cloudflare queda pendiente de la
validación manual con `curl -N`: un proxy que almacene el cuerpo podría retrasar
los eventos aunque la aplicación los emita inmediatamente. No se modificó despliegue.

La precarga no se modifica ni emite eventos de consultas. Sus llamadas directas a
Ollama no entran en el contexto de recuperación observado. Consultas y precarga
siguen compartiendo los recursos físicos de Ollama y pueden competir al cargar
un modelo frío, pero no comparten bloqueos, tareas ni estados de petición.

## Verificación de esta entrega

| Comprobación | Resultado |
| --- | --- |
| `mvn verify`: unitarios Java, incluidos RAG y precarga | **159 PASSED** |
| Integraciones Maven (base dedicada de test + HTTP real) | **25 PASSED** |
| Suites live opt-in de LLM | **3 SKIPPED**; no activadas para esta tarea |
| Demo estática, suite canónica y alias legado | **81/81 PASSED**; es la misma batería, no 162 pruebas distintas |
| Angular existente | **19/19 PASSED** |
| Chromium headless, stream HTTP local controlado | **8/8 PASSED**; ES/EN a 320, 390, 768 y 1440 px |
| Synology/proxy desplegado y LLM de producción | **NOT RUN**; no se ha desplegado ni hecho llamadas reales de generación |

Las pruebas nuevas verifican: eventos ordenados, abstención sin generación,
PC fallido y portátil, todos los proveedores fallidos, traducción condicional,
una sola operación de embeddings, métricas observadas y limpieza de contexto.
Las MVC prueban respuesta final/citas, errores saneados, cancelación y timeout.
Los tests REST existentes siguen pasando sin modificar sus expectativas.

`RagStreamHttpIT` usa Tomcat y conexiones HTTP reales: recibe eventos antes de
liberar una generación simulada y cierra realmente otro stream mientras su worker
está bloqueado. El heartbeat detecta el cierre y se comprueba la liberación de
worker y sesión. Recuperación/generación están simuladas en estas dos pruebas;
no se presentan como validaciones live de los modelos.

La batería JS comprueba fases antes de la respuesta, traducción original ES/EN,
citas, cambio de idioma en curso, UTF-8/CRLF/multilínea, EOF prematuro, errores,
timeout, desbloqueo y ausencia de reenvío REST tras empezar un stream.
Chromium usa `fetch` nativo contra HTTP local: fases visibles antes del resultado,
failover, respuesta final, teclado, cambio de idioma y ausencia de overflow o
excepciones JavaScript. No equivale a probar un dispositivo físico.

Comandos de las suites:

```bash
mvn verify
node --check front-athagon-r4r-static/js/app.js
node --check front-athagon-r4r-static/js/i18n.js
node front-athagon-r4r-static/js/app.test.cjs
node src/test/js/rag-static.test.cjs
npm --prefix frontend run test:ci
```

Para la última se requiere un Chromium instalado/configurado mediante `CHROME_BIN`.
No se añaden dependencias ni se cambia el lockfile.

## Archivos de esta implementación

Modificados:

- `src/main/java/com/riansares/r4r/rag/CitedRagService.java`.
- `src/main/java/com/riansares/r4r/rag/GenerationRouter.java`.
- `src/main/java/com/riansares/r4r/rag/LocalRetrievalQueryTranslator.java`.
- `src/main/java/com/riansares/r4r/rag/api/RagQueryController.java`.
- `src/main/java/com/riansares/r4r/config/OllamaEmbeddingConfiguration.java`.
- `src/main/java/com/riansares/r4r/config/RagApiSecurityFilter.java`.
- `src/main/java/com/riansares/r4r/config/WebConfiguration.java`.
- `src/main/resources/application.yml`.
- `front-athagon-r4r-static/index.html`.
- `front-athagon-r4r-static/js/app.js`.
- `front-athagon-r4r-static/js/i18n.js`.
- `front-athagon-r4r-static/js/app.test.cjs`.
- `front-athagon-r4r-static/styles/styles.css`.
- `src/test/java/com/riansares/r4r/config/RagApiCorsTest.java`.

Nuevos:

- `src/main/java/com/riansares/r4r/rag/RagProgress.java`.
- `src/main/java/com/riansares/r4r/rag/ObservedEmbeddingModel.java`.
- `src/main/java/com/riansares/r4r/rag/api/RagStreamController.java`.
- `src/test/java/com/riansares/r4r/rag/RagStreamingTest.java`.
- `src/test/java/com/riansares/r4r/rag/api/RagStreamControllerTest.java`.
- `src/test/java/com/riansares/r4r/rag/api/RagStreamHttpIT.java`.
- `docs/operations/rag-sse.md` (este documento).

`OllamaModelPreloader` y `RagRuntimeController` no se modifican. No se cambian los
prompts, la recuperación, los umbrales, la selección de proveedores ni el contrato
REST, ni se modifican Docker, Compose o `.env`. Sin commit, push ni despliegue.
