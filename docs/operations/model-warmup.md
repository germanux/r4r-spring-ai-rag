# Precarga local y estado de modelos

La API sigue usando Spring MVC. Tras `ApplicationReadyEvent`, prepara los destinos
configurados con exclusión mutua por origen Ollama (esquema, host y puerto).
Instancias distintas siguen trabajando en paralelo; generación y embeddings de
una misma instancia ya no hacen precargas simultáneas.

Para una instancia vacía se elige un único papel: generación local principal tiene
prioridad sobre embeddings, y embeddings sobre generación de respaldo. Con los
defaults actuales, el PC prepara su LLM y el portátil prepara Qwen3 embeddings.
El NAS prepara únicamente su respaldo de embeddings. Esto no cambia el orden de
inferencia: embeddings portátil → PC → NAS; generación PC → portátil → OpenAI.
El PC sigue siendo candidato de embeddings bajo demanda, aunque su precarga no
expulse el LLM para anticipar una consulta que quizá no llegue.

- Generación: cliente Spring AI `OllamaApi`, `/api/chat`, mensajes vacíos y
  `keep_alive=8h`. Conserva las opciones de generación configuradas.
- Embeddings: `/api/embed`, entrada vacía y las opciones del modelo de embeddings,
  incluido `keep_alive=8h`. No genera vectores de documentos. Las consultas normales
  también envían 8 h para ambos tipos de modelo, evitando acortar su residencia.
- No descarga modelos, modifica pgvector ni invoca OpenAI. La lista de proveedores
  locales se toma del router, sin pasar por su mecanismo de generación/failover.
- [Ollama documenta la precarga mediante solicitudes vacías](https://github.com/ollama/ollama/blob/main/docs/api.md).

## Endpoints

`POST /api/rag/warmup` no espera a Ollama: devuelve `202` si acepta algún trabajo,
`200` si todos estaban ejecutándose o dentro de su intervalo mínimo. Responde con
`accepted`, `skipped`, `retryAfterSeconds` y `Retry-After`. Ambos mecanismos de
precarga (arranque y HTTP) comparten las mismas exclusiones por modelo y proceso.
No hay colas de trabajos duplicados ni reintentos automáticos. Si una observación
está en curso, se reserva como máximo una precarga pendiente por modelo y se ejecuta
al terminar, en el mismo worker. El cooldown se reserva al aceptar la petición.
Se consulta `/api/ps` antes y, si hubo carga, después. No se repite la petición
vacía si el modelo ya está residente: unas opciones distintas pueden recargarlo.
Tampoco se carga si hay otro modelo residente, si falta telemetría fiable o si el
modelo no es el elegido para el arranque en frío de esa instancia. Se conserva
su estado observado (`loaded`, `unloaded` o `available`), sin confundir instalación
con residencia ni marcar una precarga aplazada como caída del proveedor.
Solicitudes repetidas y observaciones no intercambian modelos; siguen aplicándose
la deduplicación y el intervalo mínimo de precarga.
La duración solicitada no garantiza residencia si Ollama necesita liberar memoria,
se reinicia o limita el número de modelos simultáneos. Para alojar LLM y embeddings
en PC/portátil, su servidor debe admitir ambos y disponer de memoria suficiente;
el Compose de este repositorio configura solo el Ollama del NAS.

El POST respeta los orígenes permitidos, pero no consume la cuota de consultas RAG.
El control de concurrencia y frecuencia lo aplica el servicio de precarga.

`GET /api/rag/runtime` conserva sus campos y añade `models`, con identificadores
públicos `desktop`, `laptop`, `embeddings` para el embedding preferido y
`embeddings-<id del proveedor>` para los respaldos, modelo, estado, `checkedAt`, código de
error saneado y `refreshing`. Añade `warmupCompletedAt` (última precarga completada)
y `memoryObserved` (memoria comprobada mediante /api/ps). Una precarga completada
no sustituye esa observación. El estado seleccionado se resuelve por identidad
del proveedor y modelo, aunque los dos equipos usen el mismo nombre de modelo. Nunca añade URLs privadas ni mensajes de excepciones.

| Estado | Significado |
| --- | --- |
| `unknown` | Todavía no comprobado; no implica disponibilidad. |
| `loading` | Precarga en curso. |
| `available` | Modelo instalado confirmado; memoria no comprobable. |
| `loaded` | Modelo cargado confirmado en `/api/ps`; una respuesta de warmup no basta. |
| `unloaded` | Ollama responde, pero el modelo no figura en memoria. |
| `unreachable` | No se pudo utilizar el modelo (incluye modelo no instalado); `error` distingue `timeout`, `model_missing` y `provider_unavailable`. |

El runtime usa caché de diez segundos y refrescos asíncronos de solo lectura.
No realiza inferencias ni mantiene modelos cargados mediante sondeos. El proveedor
de la última respuesta permanece separado del estado actual de cada equipo.
Los datos de memoria se conservan en bytes; valores ausentes/invalidos siguen siendo
`null`, diferentes de cero. El frontend convierte los bytes a GiB.

## Configuración vigente (2026-11-16)

| Variable | Valor predeterminado |
| --- | --- |
| `RAG_WARMUP_MIN_INTERVAL` | `1m` entre inicios por modelo |
| `RAG_WARMUP_EMBEDDING_TIMEOUT` | `30s` de lectura para precarga |
| `RAG_EMBEDDING_PROVIDERS` | Lectura de consultas: portátil/PC `1500ms`; NAS `30s` |
| `RAG_EMBEDDING_CONNECT_TIMEOUT` | `400ms` para conexión TCP de embeddings |
| `RAG_EMBEDDING_PROBE_TIMEOUT` | `600ms` de lectura de `/api/tags` de embeddings |
| `OLLAMA_KEEP_ALIVE` | `8h` por defecto en el contenedor Ollama del NAS |

La precarga LLM conserva los tiempos propios de cada proveedor de generación.
La precarga de embeddings usa los 30 s independientes: bajar las consultas a 1,5 s
no debe abortar una carga inicial. Conexión de precarga LLM: 2 s; sondeo de sus
modelos según la configuración de generación. Telemetría `/api/ps`:
1 s de conexión y 2 s de petición. Los límites de sondeo y telemetría se suman al
límite de lectura de la operación de precarga. La propiedad Spring
`r4r.rag.warmup.on-startup=false` se utiliza en el perfil de test para evitar
contactos involuntarios con modelos reales.

El orden de generación no cambia: PC → portátil → OpenAI, solo si está habilitado.
Las consultas y el contrato de `POST /api/rag/answers` se conservan.

## Frontend

Al abrir la página, también en modo `embed=1`, y al cambiar explícitamente de
endpoint, solicita una precarga opcional con timeout HTTP de 15 s y un intervalo
mínimo de 60 s por endpoint en esa página. El prerender no solicita precarga.
El backend aplica además su control global por proceso, también entre pestañas.
Una API antigua sin `/warmup`, un error o un timeout no impiden consultar el RAG.

Los estados ES/EN aparecen en detalles técnicos y en los equipos locales. Solo
`unreachable` se representa como no disponible cuando hay observaciones reales;
se conserva el comportamiento anterior para APIs que todavía no devuelvan `models`.
El navegador sondea cada 5 s mientras hay carga/observaciones pendientes, hasta
18 sondeos; nunca repite automáticamente la precarga. El límite total de una
consulta pasa a 180 s para dejar margen al portátil de 60 s y al respaldo remoto.

## Verificación manual tras aplicar los cambios por el procedimiento habitual

```bash
curl -i -X OPTIONS http://192.168.1.203:18080/api/rag/warmup \
  -H 'Origin: http://127.0.0.1:8000' \
  -H 'Access-Control-Request-Method: POST'
curl -i -X POST http://192.168.1.203:18080/api/rag/warmup \
  -H 'Origin: http://127.0.0.1:8000'
curl -i -X POST http://192.168.1.203:18080/api/rag/warmup \
  -H 'Origin: http://127.0.0.1:8000'
curl -s http://192.168.1.203:18080/api/rag/runtime \
  -H 'Origin: http://127.0.0.1:8000' | python3 -m json.tool
```

La segunda precarga debería omitir los trabajos en curso o en cooldown. Repetir
la lectura del runtime para observar su finalización. No apagar equipos para
probar fallos; la suite los simula con servidores HTTP locales.

```bash
mvn verify
node front-athagon-r4r-static/js/app.test.cjs
```

`OllamaModelPreloaderTest` usa clientes Spring AI reales contra servidores simulados:
paralelismo, ambos equipos disponibles, cualquiera desconectado, ambos desconectados,
concurrencia, repetición, cooldown, timeout, opciones y telemetría. Los tests MVC
verifican contrato, CORS y la independencia de la cuota de respuestas. No son pruebas
contra los LLM físicos ni una certificación del despliegue.

## Validación del ajuste de 8 horas y precarga completa (2026-11-16)

Este apartado registra el ajuste anterior. La corrección posterior de competencia
por instancia y su validación están en [disponibilidad de embeddings](embedding-availability.md#corrección-de-competencia-por-instancia-2026-11-16).

- 88 pruebas Java seleccionadas correctas: precarga 13, separación/configuración
  Ollama 11, configuración pública 5, runtime 14, REST 8, SSE 10 y failover de
  embeddings 27. La prueba de defaults se adaptó de 8 s a 1500 ms tras detectar
  esa expectativa antigua; las dos suites afectadas volvieron a pasar (24 pruebas),
  y la ejecución final conjunta confirmó las 88 correctas, sin fallos ni errores.
- Pruebas nuevas: cinco cargas independientes en paralelo sin duplicados,
  carga inicial con presupuesto separado de la consulta y renovación a 8 horas
  de un modelo residente. Las solicitudes usan clientes Spring AI contra dobles
  HTTP locales; no requieren LLM ni PostgreSQL real.
- Angular: 24 pruebas correctas, incluidas apertura normal, `embed=1` y ausencia
  de precarga en prerender. Build de producción y prerender HTML correctos.
  Exportación estática local actualizada, conservando documentos compartidos.
- Compose: 1 prueba correcta con valores sintéticos, nuevos timeouts y residencia.
- Los primeros intentos en sandbox fallaron por permisos de sockets/Mockito;
  las ejecuciones con los permisos necesarios pasaron. Sin imágenes ni capturas.
- Solo se midió `/api/tags` real: PC máximo 1,74 ms; portátil 264,85 ms en
  20 muestras por equipo. [Detalle y límites](embedding-availability.md#lectura-real-de-apitags).
- No se hizo push ni despliegue backend/frontend. Para activar estos cambios hay
  que desplegar la API/configuración y distribuir el frontend compilado. Un `.env`
  privado o variables externas pueden sobrescribir los nuevos defaults. No se
  editaron servicios Ollama del PC/portátil ni se verificó allí su configuración
  de capacidad de memoria; el ajuste de keep-alive se realiza mediante la API.

Evidencias locales: `/tmp/r4r-warmup-final-tests.log`, `/tmp/r4r-warmup-8h-tests.log`,
`/tmp/r4r-warmup-corrected-tests.log`, `/tmp/r4r-warmup-angular-tests.log`,
`/tmp/r4r-warmup-angular-build.log` y `/tmp/r4r-warmup-prerender.log`.

## Resultado de la implementación inicial (de8beb6)

Las cifras y la lista siguientes son históricas; no describen los nuevos defaults.

- `mvn verify`: **143 unitarios PASSED**, **23 integraciones PASSED** en la base
  dedicada `r4r_rag_test`; **3 suites live SKIPPED** por activación opt-in.
- Demo estática: **71/71 PASSED**, tanto la suite canónica como su alias legado
  (son las mismas pruebas, no 142 pruebas independientes).
- Angular existente: **19/19 PASSED** con Chromium headless.
- Chromium headless con runtime simulado: **8/8 PASSED**, ES/EN a 320, 390,
  768 y 1440 px; teclado, ratón/toque emulado, sin overflow ni excepciones JS.
- Equipos físicos/OpenAI: **NOT RUN** en esta tarea. Los fallos y timeouts se
  simularon; no se interrumpieron equipos ni se generaron llamadas de pago.
- No se cambiaron Docker, Compose, `.env` activo, recuperación ni contrato de
  respuestas. No se realizó commit, push ni despliegue.

Archivos modificados:

- `src/main/java/com/riansares/r4r/rag/OllamaModelPreloader.java`.
- `src/main/java/com/riansares/r4r/rag/GenerationRouter.java`.
- `src/main/java/com/riansares/r4r/rag/RagConfiguration.java`.
- `src/main/java/com/riansares/r4r/rag/api/RagRuntimeController.java`.
- `src/main/java/com/riansares/r4r/config/RagApiSecurityFilter.java`.
- `src/main/resources/application.yml`.
- `front-athagon-r4r-static/index.html`.
- `front-athagon-r4r-static/js/app.js`.
- `front-athagon-r4r-static/js/i18n.js`.
- `front-athagon-r4r-static/js/app.test.cjs`.
- `front-athagon-r4r-static/styles/styles.css`.
- `src/test/java/com/riansares/r4r/config/RagApiCorsTest.java`.
- `src/test/java/com/riansares/r4r/rag/api/RagRuntimeControllerTest.java`.
- `src/test/resources/application-test.yml`.

Nuevos:

- `src/test/java/com/riansares/r4r/rag/OllamaModelPreloaderTest.java`.
- `docs/operations/model-warmup.md` (este documento).
