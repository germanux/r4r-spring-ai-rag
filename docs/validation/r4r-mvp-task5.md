# Tarea 5 — Validación MVP y recuperación ES/EN

Validación: 2026-11-06. No se desplegó ni se reindexó producción.

## Diagnóstico

Corpus real: 282 filas, `vector(768)`, búsqueda coseno, Top-K 3 y score mínimo
0,63. Las comprobaciones locales conectaron a PostgreSQL con
`default_transaction_read_only=on`, Flyway desactivado y sin inicializar tablas.
Se conservaron las 282 filas en todos los probes.

Preguntas equivalentes:

- ES: «¿Qué tipos de reformas realiza esta empresa? ¿Trabaja en viviendas y oficinas?»
- EN: «What kinds of renovation work does this company do? Does it work on houses and offices?»

Se inspeccionaron diez vecinos antes de filtrar, los tres candidatos después del
filtro, el contexto enviado, el texto del modelo y las citas finales.

| Recuperación | Tres mejores puntuaciones antes de filtrar | Fragmentos después del filtro |
|---|---|---:|
| ES original | 0,769277 / 0,755139 / 0,740604 | 3 |
| EN original, sin reformular | 0,572774 / 0,563181 / 0,557129 | 0 |
| Traducción externa anterior de la UI | 0,696506 / 0,696215 / 0,694234 | 3, distintos |
| EN reformulada localmente, después de corregir | 0,769277 / 0,755139 / 0,740604 | 3, iguales a ES |

La abstención inglesa se reproduce por **filtrado de relevancia**: los vecinos
existen, pero ninguno supera 0,63 y no se llama al generador. No es una ausencia de
corpus sobre la empresa. Los documentos sí contienen viviendas y pequeñas oficinas.
`nomic-embed-text:latest` produce vectores de 768 dimensiones, pero en esta prueba
el texto inglés sin reformular no recupera el corpus español con suficiente score.
El digest del modelo coincide entre Synology, PC y portátil:
`0a109f422b47e3a30ba2b10eca18548e944e8a23073ee3f3e947efcf3c45e59f`.

La traducción externa observada fue:
«¿Qué tipo de trabajos de renovación realiza esta empresa? ¿Funciona en casas y
oficinas?». Tardó 2,61 s en esta ejecución y priorizó frases comerciales y
rehabilitación en lugar de la sección de servicios. La UI, además, ignoraba sus
errores y enviaba silenciosamente el inglés original. No se puede demostrar qué
ocurrió exactamente en la ejecución histórica de 22.845 ms sin sus logs.

En el backend desplegado, sin modificarlo, se observaron:

| Petición | Resultado | Tiempo HTTP |
|---|---|---:|
| ES | Respuesta española, 2 citas | 14,708 s |
| EN sin `retrievalQuestion` | Abstención, 0 citas | 0,195 s |
| EN con la recuperación española equivalente | Respuesta inglesa, 1 cita | 4,697 s |

Son mediciones puntuales sobre la configuración antigua, no un benchmark contra
el PC actualizado. El tiempo histórico de 22,8 s no se atribuye al generador.

## Corrección

- `LocalRetrievalQueryTranslator` reformula localmente al español cuando la petición
  indica `responseLanguage=en` y no aporta `retrievalQuestion`.
- Utiliza los mismos clientes/configuración y política de respaldo que la generación,
  con un router auxiliar que no sobrescribe el proveedor de la última respuesta
  mostrado por runtime. No cambia `GenerationRouter` ni añade modelos o servicios.
- El texto original y el idioma solicitado siguen en el prompt de respuesta. Un
  `retrievalQuestion` explícito conserva su comportamiento anterior.
- La UI envía la pregunta original y `responseLanguage`; se retira la petición a
  MyMemory y su fallback silencioso. Una traducción fallida devuelve un error, no
  una falsa abstención por falta de documentos.
- Se conservan Top-K 3, score 0,63, el modelo de embeddings, sus dimensiones y las
  protecciones de abstención/citas.
- Logs `RAG result=...` distinguen `no_evidence`, `model_abstention`,
  `invalid_answer` y `answered`, con tiempos por etapa y sin registrar preguntas
  o documentos completos.
- Los tests live opt-in ingleses dejan de inyectar traducciones preparadas. La
  espera entre peticiones pasa de 15 a 21 segundos para respetar el rate limit
  de 20 segundos. Estos tests no se activaron masivamente durante esta tarea.

## Comparativa sobre el mismo corpus, código actualizado

| Caso | Recuperados | Citas finales | Resultado | Idioma |
|---|---:|---:|---|---|
| ES | 3 | 2 | Respuesta válida sobre viviendas y oficinas | ES |
| EN con reformulación local | 3, idénticos a ES | 1 | Respuesta válida sobre viviendas y oficinas | EN |
| Fotosíntesis, ES | 3 | 0 | Abstención legítima del modelo | — |
| Código de sonda XQZ-991, EN | 0 | 0 | Abstención antes del generador | — |
| EN con principal inaccesible solo en el proceso de prueba | 3 | 1 | Respuesta válida del portátil | EN |

Fuentes recuperadas por las dos preguntas principales:

| Score ES/EN | Documento y fragmento | Identificador |
|---:|---|---|
| 0,769277 | `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md`, ordinal 2, oficinas y aseos | `6fe55b40-b57c-57e7-8ed3-3eb62b9abfb6` |
| 0,755139 | `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md`, ordinal 6, Servicios nucleares / Reformas | `7c87e86b-8b05-584f-9496-4c7bbc8ce8ae` |
| 0,740604 | `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md`, ordinal 35, Mensaje comprobado en portada | `b60f70b8-7d59-550d-9e03-040cdc5dd69c` |

ES citó los fragmentos 2 y 3; EN citó el 2. No tienen por qué citar todos los
recuperados. Una abstención limpia las citas de la respuesta: **cero fuentes en
la UI no demuestra por sí solo cero vecinos recuperados**, como muestra el caso
de fotosíntesis. Los logs permiten distinguir ambas situaciones.

## Rendimiento medido

Milisegundos, ejecución local instrumentada con clientes reales. Embeddings en
Synology, SQL en PostgreSQL real, generación en el PC salvo el caso de respaldo.
Los tiempos no son estimaciones ni prometen una mejora de velocidad.

| Etapa | ES | EN local | EN con fallback |
|---|---:|---:|---:|
| Reformulación local | 0 | 7.179,09 | 4.159,73 |
| Embedding | 6.309,80 | 6.297,83 | 6.412,30 |
| Búsqueda vectorial y mapeo | 52,04 | 29,43 | 50,98 |
| Contexto y validación posterior | 3,36 | 0,86 | 3,29 |
| Generación | 6.349,44 | 7.459,60 | 5.627,45 |
| **Total** | **12.714,65** | **20.966,82** | **16.253,74** |

En el servicio, los logs separan contexto y validación; el embedding y SQL se
separaron mediante un decorador temporal de `EmbeddingModel` en el probe. En el
log de aplicación `retrievalIncludingEmbeddingMs` incluye ambos.

Prueba HTTP completa posterior con Chromium headless y servidor Python existente
`http://127.0.0.1:8000`, contra la API actualizada temporal en localhost:18081:
ES 13,773 s y EN 24,719 s, ambas HTTP 200, no abstención y 2 citas visibles. El
runtime mostró `qwen3:8b · PC-GIGA-ZORUX`. No hubo errores inesperados de consola o
red, ni interceptación de la API o respuestas simuladas en esta prueba.

## Matriz de validación

| Comprobación | Resultado | Alcance/evidencia |
|---|---|---|
| CORS desarrollo: OPTIONS, GET runtime y POST | PASS | Synology y circuito Python → API local actualizado |
| CORS origen `https://athagon.tech` | PASS | OPTIONS 200 con origen exacto, sin comodín |
| Origen no autorizado | PASS | OPTIONS 403 sin `Access-Control-Allow-Origin` |
| Endpoint público `https://r4r-api.athagon.tech` | FAIL de verificación | DNS no resuelve desde este entorno |
| Embeddings exclusivamente Synology en código actualizado | PASS | Cliente explícito, 768 dimensiones; tests de aislamiento y probes reales |
| PC principal / `qwen3:8b` | PASS local | Logs de generación y runtime |
| Fallback portátil / `qwen2.5:7b` | PASS local | URL principal inaccesible solo en el proceso de prueba; no se detuvo el PC |
| Ingesta | PASS automatizado | Tests de ingesta con datos controlados; no se ingirió ni reindexó producción |
| PostgreSQL/pgvector, recuperación y scores | PASS real | `vector(768)`, consultas de solo lectura y comparación pre/post-filtro |
| Respuestas ES/EN y citas | PASS | Prueba real de servicio y HTTP/navegador, más corpus controlado de regresión |
| Fuera de dominio y sin resultados | PASS | Abstenciones reales en etapas diferentes; generación no llamada si no hay evidencia |
| Conexión rechazada | PASS | UI en ES/EN ante puerto local cerrado: mensaje controlado, sin falso 403 y controles habilitados |
| Arquitectura y runtime plegados al cargar | PASS | Navegador y tests DOM |
| Runtime diferencia configuración y último proveedor real | PASS | Router auxiliar no altera estado de última respuesta |
| Responsive / teclado / ratón / toque | PASS | 8 combinaciones ES/EN, 320/390/768/1440 px, Chromium headless |
| Consola | PASS | Sin excepciones ni fallos de red inesperados; el caso de conexión rechazada produce fallos esperados |
| Backend | PASS | `mvn -q test`: 119 tests, 0 fallos/errores |
| Frontend estático | PASS | 50 tests y `node --check`; no requiere transpilación ni bundler |
| Compose final | PASS estático | `docker compose ... config --quiet`; no se recrearon contenedores |
| Despliegue actual coincide con configuración final | FAIL | Synology aún declara `qwen2.5:7b`/portátil como preferente y no incluye esta corrección |

No se probaron dispositivos físicos ni Firefox mediante automatización. El
circuito real se comprobó en Chromium. Los casos de UI responsive adicionales
utilizaron API simulada, separados de la prueba HTTP real.

## Reproducción mínima

```bash
mvn -q test
node front-athagon-r4r-static/js/app.test.cjs
node --check front-athagon-r4r-static/js/app.js
node --check front-athagon-r4r-static/js/i18n.js
docker compose -f docker-spring/docker-compose.yml config --quiet
```

Para revisar el código actualizado sin desplegar, en un terminal local:

```bash
SPRING_DATASOURCE_HIKARI_CONNECTION_INIT_SQL='SET default_transaction_read_only=on' \
mvn spring-boot:run \
  -Dspring-boot.run.arguments='--server.address=127.0.0.1 --server.port=18081 --spring.flyway.enabled=false --spring.ai.vectorstore.pgvector.initialize-schema=false --r4r.catalog-on-startup=false'
```

En otro terminal, origen legítimo y respetando el límite de consultas:

```bash
curl -i --max-time 150 http://127.0.0.1:18081/api/rag/answers \
  -H 'Origin: http://127.0.0.1:8000' -H 'Content-Type: application/json' \
  --data '{"question":"¿Qué tipos de reformas realiza esta empresa? ¿Trabaja en viviendas y oficinas?","responseLanguage":"es"}'
sleep 21
curl -i --max-time 150 http://127.0.0.1:18081/api/rag/answers \
  -H 'Origin: http://127.0.0.1:8000' -H 'Content-Type: application/json' \
  --data '{"question":"What kinds of renovation work does this company do? Does it work on houses and offices?","responseLanguage":"en"}'
```

Confirmar las variables de la tarea 2 antes de arrancar, sin sobrescribir `.env`.
Los tests live opt-in existentes se deben ejecutar únicamente después de actualizar
el backend y con autorización para sus consultas; no son parte de `mvn test`.

## Pendientes y publicación

**El código queda validado localmente; el MVP desplegado no se declara listo para
publicación todavía.** Falta verificar DNS/ruta/túnel del dominio público desde el
PC y actualizar el backend desplegado tras autorización. No se ha demostrado que
la configuración desplegada utilice exclusivamente Synology para embeddings: su
runtime no expone la URL y sigue mostrando una generación preferente antigua.

Después de la validación manual y autorización:

1. Preservar el `.env` activo, contraseña y token. Revisar las variables separadas
   de embeddings/generación de la tarea 2; Compose fija el DNS interno de embeddings.
2. Construir y desplegar **primero el backend**, mediante el procedimiento existente
   (`scripts/deploy-synology.sh`), después de revisar sus efectos. No fue ejecutado.
3. Verificar OPTIONS, runtime y las dos consultas anteriores contra Synology y el
   dominio público. Revisar `RAG result=...` y los tiempos por etapa.
4. Actualizar el frontend después: la nueva UI ya depende de la reformulación
   local del backend. Se actualizaron las referencias versionadas de JavaScript.
5. No borrar volúmenes, reindexar, cambiar modelo/dimensiones ni bajar umbrales.

Esperar aprobación manual antes de cualquier despliegue de producción.

## Archivos modificados

Backend:
- `src/main/java/com/riansares/r4r/rag/LocalRetrievalQueryTranslator.java` (nuevo).
- `src/main/java/com/riansares/r4r/rag/CitedRagService.java`.
- `src/main/java/com/riansares/r4r/rag/RagConfiguration.java`.

Pruebas:
- `src/test/java/com/riansares/r4r/rag/CitedRagMultilingualRetrievalTest.java` (nuevo).
- `src/test/java/com/riansares/r4r/rag/LocalRetrievalQueryTranslatorTest.java` (nuevo).
- `src/test/java/com/riansares/r4r/config/OllamaServerSeparationTest.java`.
- `src/test/java/com/riansares/r4r/llm_tests/RagLiveEnglishQuestionsIT.java`.
- `src/test/java/com/riansares/r4r/llm_tests/RagLiveQuestionsIT.java`.

Frontend:
- `front-athagon-r4r-static/index.html` (texto de alcance y referencias versionadas).
- `front-athagon-r4r-static/js/app.js` (retirada de traducción externa).
- `front-athagon-r4r-static/js/app.test.cjs`.
- `front-athagon-r4r-static/js/i18n.js` (texto ES/EN actualizado).

Informe: `docs/validation/r4r-mvp-task5.md`.
La API temporal de validación fue detenida al terminar. No se modificaron CSS,
Docker Compose, `.env`, JAR desplegado, modelo de embeddings ni esquema/datos de producción.
