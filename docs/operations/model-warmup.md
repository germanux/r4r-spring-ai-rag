# Precarga local y estado de modelos

La API sigue usando Spring MVC. Tras `ApplicationReadyEvent`, un executor de tres
hilos prepara independientemente el PC, el portátil ASUS y los embeddings del
Synology. Un equipo desconectado no bloquea los otros trabajos ni la API.

- Generación: cliente Spring AI `OllamaApi`, `/api/chat`, mensajes vacíos y
  `keep_alive=3h`. Conserva las opciones de generación configuradas.
- Embeddings: `/api/embed`, entrada vacía y las opciones del modelo de embeddings,
  incluido su `keep_alive` cuando esté configurado. No genera vectores de documentos.
- No descarga modelos, modifica pgvector ni invoca OpenAI. La lista de proveedores
  locales se toma del router, sin pasar por su mecanismo de generación/failover.
- [Ollama documenta la precarga mediante solicitudes vacías](https://github.com/ollama/ollama/blob/main/docs/api.md).

## Endpoints

`POST /api/rag/warmup` no espera a Ollama: devuelve `202` si acepta algún trabajo,
`200` si todos estaban ejecutándose o dentro de su intervalo mínimo. Responde con
`accepted`, `skipped`, `retryAfterSeconds` y `Retry-After`. Ambos mecanismos de
precarga (arranque y HTTP) comparten las mismas exclusiones por modelo y proceso.
No hay colas de trabajos duplicados ni reintentos automáticos.

El POST respeta los orígenes permitidos, pero no consume la cuota de consultas RAG.
El control de concurrencia y frecuencia lo aplica el servicio de precarga.

`GET /api/rag/runtime` conserva sus campos y añade `models`, con identificadores
públicos `desktop`, `laptop`, `embeddings`, modelo, estado, `checkedAt`, código de
error saneado y `refreshing`. Nunca añade URLs privadas ni mensajes de excepciones.

| Estado | Significado |
| --- | --- |
| `unknown` | Todavía no comprobado; no implica disponibilidad. |
| `loading` | Precarga en curso. |
| `available` | Modelo instalado confirmado; memoria no comprobable. |
| `loaded` | Modelo cargado observado en `/api/ps` o precarga completada sin telemetría disponible. |
| `unloaded` | Ollama responde, pero el modelo no figura en memoria. |
| `unreachable` | No se pudo utilizar el modelo (incluye modelo no instalado); `error` distingue `timeout`, `model_missing` y `provider_unavailable`. |

El runtime usa caché de diez segundos y refrescos asíncronos de solo lectura.
No realiza inferencias ni mantiene modelos cargados mediante sondeos. El proveedor
de la última respuesta permanece separado del estado actual de cada equipo.
Los datos de memoria se conservan en bytes; valores ausentes/invalidos siguen siendo
`null`, diferentes de cero. El frontend convierte los bytes a GiB.

## Configuración (sin cambios en Compose ni en el .env activo)

| Variable | Valor predeterminado |
| --- | --- |
| `RAG_WARMUP_MIN_INTERVAL` | `1m` entre inicios por modelo |
| `RAG_WARMUP_PC_TIMEOUT` | `60s` de lectura para precarga |
| `RAG_WARMUP_LAPTOP_TIMEOUT` | `60s` de lectura para precarga |
| `RAG_WARMUP_EMBEDDING_TIMEOUT` | `30s` de lectura para precarga |
| `RAG_FALLBACK_READ_TIMEOUT` | `60s` de lectura para generación en el portátil |

Conexión de precarga: 2 s. Sondeo de modelos: 2 s de conexión/lectura; telemetría:
1 s de conexión y 2 s de petición. Los límites de sondeo y telemetría se suman al
límite de lectura de la operación de precarga. La propiedad Spring
`r4r.rag.warmup.on-startup=false` se utiliza en el perfil de test para evitar
contactos involuntarios con modelos reales.

El orden de generación no cambia: PC → portátil → OpenAI, solo si está habilitado.
Las consultas y el contrato de `POST /api/rag/answers` se conservan.

## Frontend

Al abrir la página y al cambiar explícitamente de endpoint, solicita una precarga
opcional con timeout de 5 s y un intervalo mínimo de 60 s por endpoint en esa página.
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

## Resultado de esta implementación

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
