# Restauración de MyMemory y verificación RAG — 6 de noviembre de 2026

## Alcance y revisión previa

Base: `1c59db7`, último cambio del usuario, posterior a `01ff840`.
El árbol y el índice estaban limpios antes de empezar. Se revisaron ambos commits,
el historial del 4 y 6 de noviembre, `docs/operations/REVISION.md`, el frontend,
el traductor local, `CitedRagService`, `RagQueryController` y `RagQueryRequest`.

Se conservan íntegramente los diagnósticos por etapa y las validaciones de salida
local introducidos por `1c59db7`. El contrato HTTP y los tres campos ya existentes
no necesitan modificaciones. No se cambia código Java de producción.

La retirada anterior de MyMemory excedió el alcance solicitado. Esta corrección
restaura el proveedor existente; no incorpora otro proveedor ni cambia arquitectura,
modelos, parámetros de recuperación, configuración operativa o política de citas.

## Flujo final

1. En la interfaz española, la pregunta se envía sin traducción, con
   `question` original y `responseLanguage: "es"`.
2. En la interfaz inglesa, JavaScript llama a
   `https://api.mymemory.translated.net/get`, `q=<pregunta original>`,
   `langpair=en|es`, sin credenciales, antes del POST RAG.
3. Con respuesta válida, envía `question` original, `retrievalQuestion` española
   y `responseLanguage: "en"`. El backend solo busca con `retrievalQuestion`;
   la pregunta original y el idioma siguen llegando al generador. No concatena textos.
4. MyMemory tiene un timeout de **5 segundos**, una sola llamada y ningún reintento.
   Se conserva el timeout global de 120 segundos de la UI, que incluye la traducción.
5. Si falla red/CORS/HTTP, el JSON es inválido, hay cuota agotada, respuesta vacía,
   texto sin traducir o longitud inválida, el POST **omite `retrievalQuestion`**.
   Esto activa el traductor local existente y su router PC → portátil.
6. No se trunca el original. Por encima del límite de **500 bytes UTF-8** de
   MyMemory se usa directamente el fallback local. No se hacen llamadas destinadas
   a fallar ni se dividen preguntas en múltiples peticiones.
7. Si falla también la traducción local, se conserva el error del backend;
   no se presenta como ausencia de evidencia ni se envía el inglés como traducción.

La selección del flujo ES/EN conserva el criterio del frontend existente: el idioma
seleccionado. No se añade un detector independiente del idioma escrito.

La nota ES/EN de privacidad explica el envío a MyMemory y el fallback local.
Las referencias versionadas de JS cambian para evitar cachés antiguas.

Límites del proveedor: [especificación GET](https://mymemory.translated.net/doc/spec.php)
y [cuota gratuita](https://mymemory.translated.net/doc/usagelimits.php), consultadas
el 6/11/2026: 500 bytes por segmento y 5.000 caracteres/día en uso anónimo.
No se añade correo, clave ni segundo proveedor. La calidad de traducción y la cuota
son dependencias externas, no garantías del producto.

## Infraestructura y naturaleza de las pruebas

- UI real servida por el Python HTTP Server existente en `127.0.0.1:8000`.
- API temporal de este checkout en `127.0.0.1:18081`; no es el backend desplegado.
- Embeddings reales: `192.168.1.203:11434`, `nomic-embed-text:latest`, 768 dimensiones.
- Generación real: `192.168.1.207:11434`, `qwen3:8b`.
- PostgreSQL/pgvector del corpus real: **solo lectura**, Flyway y creación automática
  de esquema desactivados. Comprobación antes/después: **282 filas**.
- Las pruebas de integración de escritura usan exclusivamente la base dedicada
  `192.168.1.203:5433/r4r_rag_test`, usuario `r4r_test`, con `TestDatabaseGuard`.
  Sus fixtures y limpiezas son de test, nunca del corpus de producción.
- Chromium headless, clics nativos por CDP. MyMemory, API, embeddings, base y
  generación reales en los casos satisfactorios; no se sustituyen por mocks.
- En el caso de fallo de MyMemory se bloquea **solo su URL en el navegador**.
  La traducción local, recuperación y respuesta posteriores son reales.
- Los tests unitarios usan mocks/vectores deterministas. Las integraciones de ingesta
  y pgvector usan PostgreSQL real con embeddings deterministas; no se presentan como
  pruebas de ingesta con Ollama real. La recuperación extremo a extremo sí lo utiliza.

## Resultados de navegador

Pregunta ES: «¿Qué tipos de reformas realiza esta empresa? ¿Trabaja en viviendas y oficinas?».
Pregunta EN: «What kinds of renovation work does this company do? Does it work on houses and offices?».

| Caso real | Traducción externa | HTTP / resultado | Citas visibles | Total clic → resultado | Evaluación |
|---|---:|---|---:|---:|---|
| ES | No utilizada | 200, respuesta española | 2 | 13,162 s | PASSED |
| EN → MyMemory | 0,549 s | 200, respuesta inglesa | 2 | 17,230 s | PASSED flujo; FAILED equivalencia sobre oficinas |
| EN → MyMemory bloqueado → local | Error inmediato simulado (0,005 s) | 200, respuesta inglesa sobre viviendas y oficinas | 2 | 21,695 s | PASSED |
| EN fuera de dominio: masa de Júpiter | 0,560 s | 200, abstención legítima | 0 | 3,734 s | PASSED |
| Endpoint local con conexión rechazada | No necesaria | Error de conexión visible ES/EN, controles recuperados | — | — | PASSED |

Se verificó además el fallback **real al portátil** mediante un proceso de prueba
independiente con la URL preferente sustituida por `127.0.0.1:1` únicamente en sus
argumentos de arranque. No se detuvo ni reconfiguró el PC. El portátil
`192.168.1.209:11434`, `qwen2.5:7b`, tradujo y generó una respuesta inglesa sobre
viviendas y oficinas con una cita válida. Synology siguió haciendo embeddings.
Resultado: **PASSED**, 35,031 s en una ejecución (traducción 22,760 s, embeddings
6,540 s, SQL/mapeo 0,049 s y generación 5,678 s). No es una media ni un benchmark
comparativo. Corpus antes/después: 282 filas, solo lectura.

No hubo excepciones JavaScript. El único fallo de red en los cuatro casos RAG fue
el bloqueo deliberado de MyMemory; en la prueba de conexión se esperan rechazos del
puerto cerrado. Estos fallos inducidos no se ocultan ni se cuentan como conexiones
satisfactorias. Las respuestas y metadatos citados se mostraron en la UI.

### Defecto funcional detectado: equivalencia de recuperación

MyMemory devuelve realmente:

> ¿Qué tipo de trabajos de renovación realiza esta empresa? ¿Funciona en casas y oficinas?

Con Top-K **3** y score mínimo **0,63**, sin cambios:

| Búsqueda | Scores Top-3 | Fragmentos (archivo abreviado #ordinal) |
|---|---|---|
| Español original | 0,769277 / 0,755139 / 0,740604 | `R4R_20_CASOS...#2`, `R4R_00_IDENTIDAD...#6` (Reformas), `R4R_30_METODO...#35` |
| Traducción MyMemory | 0,696506 / 0,696215 / 0,694234 | `R4R_00_IDENTIDAD...#15`, `R4R_00_IDENTIDAD...#7` (Rehabilitación), `R4R_30_METODO...#35` |
| Traducción local de fallback | 0,769277 / 0,755139 / 0,740604 | Los mismos tres fragmentos que el español original |

La respuesta con MyMemory afirma «There is no mention of office renovations in the
provided evidence». Es coherente con los fragmentos seleccionados, pero **no cubre
correctamente lo que sí contiene el corpus completo** sobre oficinas. La medición
instrumentada independiente reprodujo el mismo problema («There is no mention of
office work in the evidence»). HTTP 200 y dos citas no bastan para aprobar esta
regresión funcional.

La traducción local recupera el bloque de oficinas y el de servicios y contesta ambas
partes. Esto no justifica cambiar otra vez el proveedor principal sin aprobación.
No se ha activado traducción local sobre una traducción MyMemory aceptada, ni se ha
alterado el ranking, bajado el umbral o añadido términos ocultos para forzar el resultado.

Se comprobaron además **24 combinaciones reales de GET/OPTIONS** contra la API
temporal y Synology: `127.0.0.1:8000`, `localhost:8000`, `https://athagon.tech`
permitidos con ACAO exacto, y un origen ajeno rechazado con 403 sin ACAO. Todos
PASSED. Los POST positivos están cubiertos por las consultas reales anteriores y
las suites live; los negativos y demás casos de seguridad, por los tests Java.

### Otros fallos encontrados en la suite live española

La suite real `RagLiveQuestionsIT` terminó con **13 PASSED / 2 FAILED**, sin errores
HTTP ni casos omitidos. No se cambió el test para aceptar la abstención:

| Caso | Expectativa / observado | Recuperación | Etapa de abstención |
|---|---|---|---|
| 05 Presupuesto: «¿Qué datos debe aportar el cliente para solicitar un presupuesto?» | Respuesta con evidencia / abstención, 0 citas | 3 fragmentos; 0,753231 / 0,735968 / 0,719542 | `model_abstention`, tras generación |
| 12 Plazos: «¿Por qué es importante conocer el plazo previsto antes de presupuestar una reforma?» | Respuesta con evidencia / abstención, 0 citas | 3 fragmentos; 0,703056 / 0,701227 / 0,680209 | `model_abstention`, tras generación |

Una inspección adicional de solo lectura reprodujo esos rankings con embeddings de
Synology, sin ejecutar otra generación. Para presupuesto, el Top-3 contiene
«Cliente que aprieta precio», «Contenido útil para clientes» sobre humedades y un
encabezado de presupuestación; no el FAQ con los datos de primera orientación que sí
se recuperó en el caso 01. Para plazos, devuelve instalaciones, una enumeración de
lo que contiene un presupuesto y «Cliente que aprieta precio», sin explicar la causa
preguntada. No son fallos de MyMemory: estas peticiones son españolas y no se traducen.

Esto muestra un problema de precisión de recuperación y/o adecuación de la expectativa
al contexto disponible. La abstención conserva la protección cuando los fragmentos
seleccionados no permiten responder. No se debe forzar una respuesta ni culpar al
generador sin evaluar primero el contexto. Queda pendiente revisar estos dos casos
con aprobación; no se ha alterado la búsqueda ni los fixtures para ocultar el fallo.

## Latencias por etapa

Medición adicional con los componentes reales de Spring AI/pgvector y contadores
alrededor de cada llamada, mismo corpus de solo lectura. Son **ejecuciones únicas**,
no medias ni percentiles; carga, caché y longitud de respuesta influyen en los tiempos.
No se infiere una mejora general de rendimiento a partir de estos tres resultados.

| Caso | MyMemory | Traducción local | Embeddings | SQL + mapeo | Contexto + validación + sobrecoste | Generación | Total |
|---|---:|---:|---:|---:|---:|---:|---:|
| ES | 0 | 0 | 6.783,61 ms | 56,10 ms | 1,49 ms | 14.749,42 ms | 21.590,61 ms |
| EN externa | 585,89 ms | **0 llamadas** | 7.179,16 ms | 28,81 ms | 0,63 ms | 7.805,93 ms | 15.600,43 ms |
| EN local | 0 | **1 llamada, 8.878,86 ms** | 6.227,24 ms | 28,81 ms | 0,69 ms | 5.835,47 ms | 20.971,07 ms |

En la prueba HTTP de navegador, los logs existentes del backend también confirman:
normalización EN externa ~0,001 ms; normalización EN fallback ~9.465 ms.
No hay una inferencia de traducción local adicional cuando llega la traducción externa.
Los contadores instrumentados confirman vectores de 768 dimensiones obtenidos del
cliente configurado para Synology y generación en el PC.

## Suites automatizadas y comandos

```bash
node front-athagon-r4r-static/js/app.test.cjs
node src/test/js/rag-static.test.cjs
node --check front-athagon-r4r-static/js/app.js
node --check front-athagon-r4r-static/js/i18n.js
mvn test
mvn verify
```

El comando histórico `src/test/js/rag-static.test.cjs` apuntaba al archivo eliminado
`front-athagon-r4r-static/app.js` y esperaba el fallback incorrecto al inglés.
Ahora delega en la suite canónica vigente; sus 65 casos no son otros 65 tests distintos.

La primera ejecución Maven dentro del sandbox falló por la imposibilidad de adjuntar
el agente Mockito. Se repitió con permiso para los procesos/red requeridos; se distingue
este fallo de entorno del resultado de los tests del producto.

Para reproducir la API temporal sin migrar ni escribir en el corpus (requiere las
variables PostgreSQL existentes; no imprimir ni sobrescribir `.env`):

```bash
SPRING_DATASOURCE_HIKARI_CONNECTION_INIT_SQL='SET default_transaction_read_only=on' \
RAG_EMBEDDING_BASE_URL=http://192.168.1.203:11434 \
RAG_PC_OLLAMA_BASE_URL=http://192.168.1.207:11434 \
RAG_PC_GENERATION_MODEL=qwen3:8b \
RAG_FALLBACK_OLLAMA_BASE_URL=http://192.168.1.209:11434 \
RAG_FALLBACK_GENERATION_MODEL=qwen2.5:7b \
mvn spring-boot:run -Dspring-boot.run.arguments='--server.address=127.0.0.1 --server.port=18081 --spring.flyway.enabled=false --spring.ai.vectorstore.pgvector.initialize-schema=false --r4r.catalog-on-startup=false'
```

Seleccionar `http://127.0.0.1:18081` como endpoint personalizado en la UI local
para repetir la prueba de navegador. Para el fallo de MyMemory, bloquear únicamente
`api.mymemory.translated.net` con las herramientas del navegador; comprobar en el POST
que **no** hay `retrievalQuestion`, que `question` sigue siendo la original y
`responseLanguage` sigue siendo `en`. Esperar 21 s entre consultas por el rate limit.

Los tests live existentes son opt-in y se ejecutan contra la API temporal:

```bash
RAG_LIVE_TEST=true RAG_TEST_BASE_URL=http://127.0.0.1:18081 \
  mvn -Dtest=NoUnitTests -Dsurefire.failIfNoSpecifiedTests=false \
  -Dit.test=RagLiveQuestionsIT verify
# Esperar al menos 21 segundos entre suites por el rate limit.
RAG_LIVE_EN_TEST=true RAG_TEST_BASE_URL=http://127.0.0.1:18081 \
  mvn -Dtest=NoUnitTests -Dsurefire.failIfNoSpecifiedTests=false \
  -Dit.test=RagLiveEnglishQuestionsIT verify
```

La suite inglesa Java envía `retrievalQuestion=null`: verifica el fallback local del
backend para sus 15 casos. **No verifica MyMemory**; esa comprobación se realizó en
el navegador real y en la medición instrumentada anteriores.

### Detalle de suites sin LLM real

| Suite | Pruebas | Resultado |
|---|---:|---|
| `HeadingMarkdownChunkerTest` | 2 | PASSED |
| `DatabaseEnvironmentTest` | 5 | PASSED |
| `OllamaServerSeparationTest` | 2 | PASSED |
| `RagApiCorsTest` | 12 | PASSED |
| `RagApiSecurityFilterTest` | 6 | PASSED |
| `MarkdownDocumentLoaderTest` | 1 | PASSED |
| `KnowledgeIngestionCliContractTest` | 7 | PASSED |
| `KnowledgeIngestionCliTest` | 16 | PASSED |
| `KnowledgeIngestionFailureClassificationTest` | 13 | PASSED |
| `KnowledgeIngestionServiceTest` | 1 | PASSED |
| `KnowledgeIngestionSpringLifecycleTest` | 4 | PASSED |
| `CitedRagMultilingualRetrievalTest` | 5 | PASSED |
| `CitedRagServiceCitationsTest` | 6 | PASSED |
| `CitedRagServiceInvalidAnswersTest` | 7 | PASSED |
| `CitedRagServiceTest` | 11 | PASSED |
| `GenerationRouterTest` | 5 | PASSED |
| `LocalRetrievalQueryTranslatorTest` | 5 | PASSED |
| `InferenceHostTest` | 2 | PASSED |
| `RagQueryControllerTest` | 7 | PASSED |
| `RagRuntimeControllerTest` | 7 | PASSED |
| `KnowledgeCatalogServiceTest` | 1 | PASSED |
| `PostgresBaselineIT` | 1 | PASSED |
| `KnowledgeIngestionCliProcessIT` | 3 | PASSED |
| `KnowledgeIngestionServiceIT` | 4 | PASSED |
| `PgVectorKnowledgeStoreIT` | 14 | PASSED |
| `PgVectorKnowledgeStoreTransactionalRollbackIT` | 1 | PASSED |

Las suites live verifican contrato HTTP, abstención esperada, respuesta no vacía,
metadatos y referencias a citas; la inglesa añade una comprobación básica del idioma.
Por sí solas no prueban que cada afirmación sea semánticamente correcta. La revisión
manual de equivalencia con MyMemory detectó el fallo que sus checks de HTTP/citas
no detectan. No se presenta ese caso como una respuesta completamente correcta.

### Resultado final

| Comprobación | PASSED | FAILED | SKIPPED / NOT RUN |
|---|---:|---:|---|
| Java unitario, `mvn test` final | 125 | 0 | 0 |
| Integración Maven sin LLM (PostgreSQL, pgvector, ingesta, CLI) | 23 | 0 | 0 |
| Suite RAG real ES, Maven opt-in | 13 | **2** | 0 |
| Suite RAG real EN con fallback local, Maven opt-in | 10 | **5** | 0 |
| JavaScript canónico (ambos comandos ejecutados, mismos casos) | 65 | 0 | 0 |
| GET/OPTIONS CORS reales, API temporal + Synology | 24 | 0 | 0 |
| Cuatro flujos RAG de navegador real | 4 (transporte y representación) | **1 evaluación de equivalencia EN/MyMemory** | 0 |
| Error de conexión en navegador ES/EN | 2 | 0 | 0 |
| Fallback real PC inaccesible → portátil | 1 | 0 | 0 |
| RAG contra dominio público | — | — | **NOT RUN: DNS no resuelve en este entorno** |
| Firefox / dispositivos físicos | — | — | **NOT RUN: navegador/dispositivos no utilizados** |
| Ingesta nueva/reindexado sobre producción | — | — | **NOT RUN: excluidos para preservar datos** |
| Timeout de lectura real de inferencia bloqueada | — | — | **NOT RUN: no se bloquean servidores reales; casos de excepción simulados en unitarios** |

Los **178 casos Java distintos** finalmente ejecutados incluyen 171 PASSED y
**7 FAILED**. La suite general `mvn verify` inicial, con los opt-in desactivados,
ejecutó 124 unitarios y 23 integraciones con éxito y dejó **2 SKIPPED** (los dos
contenedores live). Después se añadió un test unitario de fallback, se ejecutaron
los 125 unitarios finales y se activaron ambas suites live: esos 30 casos ya **no**
quedaron omitidos. Los dos comandos Maven live terminaron con **exit code 1** por
las 2 y 5 expectativas fallidas; no es correcto afirmar que todos los tests Maven pasan.
La ejecución inicial restringida del sandbox también terminó en FAILED por attach de
Mockito; al repetirla con los permisos necesarios, los unitarios e integraciones
sin LLM sí pasaron. Las ejecuciones repetidas no se suman como tests distintos.

### Casos live ES

| # | Caso | Resultado | HTTP | Citas | Petición (s) | Etapa final |
|---|---|---|---:|---:|---:|---|
| 01 | Primera orientación | PASSED | 200 | 1 | 11.07 | `answered`, 3 fragmentos |
| 02 | Solera frente a solado | PASSED | 200 | 2 | 11.98 | `answered`, 3 fragmentos |
| 03 | Precio por acceso | PASSED | 200 | 1 | 9.04 | `answered`, 3 fragmentos |
| 04 | Casa antigua | PASSED | 200 | 2 | 10.61 | `answered`, 3 fragmentos |
| 05 | Presupuesto | **FAILED: abstención** | 200 | 0 | 12.07 | `model_abstention`, 3 fragmentos |
| 06 | Visita previa | PASSED | 200 | 1 | 8.99 | `answered`, 3 fragmentos |
| 07 | Patio | PASSED | 200 | 3 | 13.18 | `answered`, 3 fragmentos |
| 08 | Hormigón de camión | PASSED | 200 | 2 | 29.71 | `answered`, 3 fragmentos |
| 09 | Pasillo estrecho | PASSED | 200 | 1 | 15.05 | `answered`, 3 fragmentos |
| 10 | Humedades | PASSED | 200 | 1 | 11.37 | `answered`, 3 fragmentos |
| 11 | Baño | PASSED | 200 | 2 | 11.75 | `answered`, 3 fragmentos |
| 12 | Plazos | **FAILED: abstención** | 200 | 0 | 11.34 | `model_abstention`, 3 fragmentos |
| 13 | Fuera de dominio: fotosíntesis | PASSED | 200 | 0 | 9.00 | `model_abstention`, 1 fragmentos |
| 14 | Fuera de dominio: astronomía | PASSED | 200 | 0 | 3.26 | `no_evidence`, 0 fragmentos |
| 15 | Fuera de dominio: literatura | PASSED | 200 | 0 | 8.23 | `model_abstention`, 3 fragmentos |

### Casos live EN

| # | Caso | Resultado | HTTP | Citas | Petición (s) | Etapa final |
|---|---|---|---:|---:|---:|---|
| 01 | Company services ES/EN regression | PASSED | 200 | 1 | 23.68 | `answered`, 3 fragmentos |
| 02 | Concrete slab versus flooring | PASSED | 200 | 1 | 25.47 | `answered`, 3 fragmentos |
| 03 | Access and cost | **FAILED: abstención** | 200 | 0 | 19.41 | `model_abstention`, 3 fragmentos |
| 04 | Old house | PASSED | 200 | 2 | 32.73 | `answered`, 3 fragmentos |
| 05 | Estimate | **FAILED: abstención** | 200 | 0 | 20.33 | `model_abstention`, 3 fragmentos |
| 06 | Site visit | PASSED | 200 | 2 | 24.18 | `answered`, 3 fragmentos |
| 07 | Patio | **FAILED: abstención** | 200 | 0 | 15.66 | `model_abstention`, 3 fragmentos |
| 08 | Ready-mix concrete | **FAILED: abstención** | 200 | 0 | 14.97 | `model_abstention`, 3 fragmentos |
| 09 | Narrow corridor | PASSED | 200 | 1 | 20.32 | `answered`, 2 fragmentos |
| 10 | Damp | PASSED | 200 | 1 | 22.22 | `answered`, 3 fragmentos |
| 11 | Bathroom | PASSED | 200 | 2 | 31.98 | `answered`, 3 fragmentos |
| 12 | Timelines | **FAILED: abstención** | 200 | 0 | 21.32 | `model_abstention`, 3 fragmentos |
| 13 | Out of domain: photosynthesis | PASSED | 200 | 0 | 19.25 | `model_abstention`, 3 fragmentos |
| 14 | Out of domain: astronomy | PASSED | 200 | 0 | 9.19 | `no_evidence`, 0 fragmentos |
| 15 | Out of domain: literature | PASSED | 200 | 0 | 15.19 | `model_abstention`, 3 fragmentos |

El caso 01 de ES es primera orientación y el de EN es la regresión de servicios;
los otros 14 pares son semánticamente equivalentes. La comparación exacta de la
pregunta de servicios ES/EN está en las pruebas de navegador anteriores. Los siete
fallos live son abstenciones posteriores a generación con tres fragmentos recuperados,
no errores HTTP ni falta total de candidatos. Las seis preguntas fuera de dominio
(tres por idioma) se abstienen sin fuentes inventadas.



## Pendientes y recomendaciones (sin cambios adicionales)

1. **FAILED funcional:** equivalencia sobre oficinas usando MyMemory. Proponer una
   evaluación de recuperación por subpreguntas o reranking, con un conjunto de
   regresión bilingüe; requiere aprobación antes de implementar cambios de búsqueda.
   No se recomienda bajar umbrales ni cambiar de proveedor a ciegas.
2. **FAILED en suites live:** presupuesto y plazos en ES; acceso/coste,
   presupuesto, patio, hormigón de camión y plazos en EN. Evaluar evidencia
   seleccionada y expectativas antes de plantear una corrección autorizada.
   No se han tocado umbrales, tests esperados ni generación para hacerlos pasar.
3. El traductor local conserva el timeout de conexión/probe existente, pero la
   inferencia no tiene timeout de lectura configurado, como ya señalaba la auditoría.
   La UI cancela a 120 s; ello no garantiza cancelar la inferencia del servidor.
   Cambiar ese presupuesto operativo requiere decisión explícita; no se modifica aquí.
4. El runtime desplegado en `192.168.1.203:18080` sigue informando `qwen2.5:7b`
   y `PC-ASUS-ZORIN` como preferente. La API temporal sí utiliza PC + `qwen3:8b`.
   No se certifica que el despliegue actual coincida con este checkout.
5. El endpoint público no se pudo verificar aquí: resolución DNS de
   `r4r-api.athagon.tech` devuelve `Name or service not known`. Es **NOT RUN** para
   la validación RAG pública, no una afirmación de caída desde todas las redes.
6. Firefox, dispositivos físicos e ingesta nueva con Ollama sobre producción:
   **NOT RUN**. No se necesita escribir/reindexar producción para esta corrección.
7. No se incorporó un segundo traductor externo. Cualquier propuesta futura debe
   exponer cuota, calidad, CORS, privacidad y credenciales y recibir aprobación.
8. La auditoría también señala respuestas positivas sin cita y runtime global
   compartido entre peticiones. Se conservan; no se amplía el alcance para cambiarlos.

`/api/tags` del PC confirma que siguen instalados `gpt-oss:120b` y
`gpt-oss-120b-16k:latest`. No se borraron, modificaron ni invocaron, y no entran en el RAG.

## Evidencias de esta ejecución y cierre

El informe conserva los resultados de forma permanente. Los diagnósticos completos
quedan además en archivos temporales locales (pueden desaparecer al limpiar `/tmp`):

- `/tmp/r4r-translation-unit-final.log` y `/tmp/r4r-translation-verify.log`.
- `/tmp/r4r-translation-live-es-results.json` y `/tmp/r4r-translation-live-en-results.json`
  (pregunta, respuesta, citas, tiempo y traza de etapa de cada caso).
- `/tmp/r4r-translation-live-es.log` y `/tmp/r4r-translation-live-en.log`.
- `/tmp/r4r-translation-browser-results.json`.
- `/tmp/r4r-translation-timing.log`.
- `/tmp/r4r-translation-real-laptop-fallback.log`.
- `/tmp/r4r-translation-cors.json`.
- `/tmp/r4r-translation-es-abstentions.log`.
- `/tmp/r4r-translation-node.log` y `/tmp/r4r-translation-node-legacy.log`.
- `/tmp/r4r-translation-final-corpus-check.log`.
- Informes JUnit XML en `target/surefire-reports` y `target/failsafe-reports`.

Comprobación final tras todas las consultas: `readonly=on`, **282 filas** en el corpus.
Se detuvo exclusivamente la API temporal creada para validar, con cierre ordenado.
El servidor Python del usuario y los servicios Synology/PC/portátil siguen intactos.

## Archivos modificados

- `front-athagon-r4r-static/js/app.js`.
- `front-athagon-r4r-static/js/app.test.cjs`.
- `front-athagon-r4r-static/js/i18n.js`.
- `front-athagon-r4r-static/index.html`.
- `src/test/java/com/riansares/r4r/rag/LocalRetrievalQueryTranslatorTest.java`.
- `src/test/java/com/riansares/r4r/rag/CitedRagMultilingualRetrievalTest.java`.
- `src/test/js/rag-static.test.cjs`.
- `docs/validation/r4r-mymemory-restoration.md` (este informe).

No se modifican `LocalRetrievalQueryTranslator`, `CitedRagService`,
`RagQueryController`, `RagQueryRequest` ni configuración de backend.
No se modifican CSS, modelos, Docker, `.env`, esquema o datos del corpus real.
No se realiza commit, push ni despliegue. No se declara el MVP listo para publicar
mientras la regresión de equivalencia y las verificaciones operativas sigan pendientes.
