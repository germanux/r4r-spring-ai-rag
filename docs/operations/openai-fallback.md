# Respaldo remoto RAG: GPT-6 Luna

Orden: PC/Ollama qwen3:8b → portátil/Ollama qwen2.5:7b → OpenAI Responses API,
modelo configurado con `RAG_OPENAI_MODEL` (por defecto `gpt-6-luna`). El tercero
solo existe en el router cuando `RAG_OPENAI_ENABLED=true`. Se mantiene **false**
en el archivo local al entregar; no se ha desplegado ni interrumpido ningún equipo.

## Configuración

Conservar `OPENAI_API_KEY` existente en `docker-spring/.env`, ignorado por Git.
Compose ya pasa ese archivo a la API; Spring también lo importa como propiedades.
No copiar la clave a JavaScript, argumentos de consola o documentación.

```dotenv
RAG_OPENAI_ENABLED=false
RAG_OPENAI_MODEL=gpt-6-luna
```

Para habilitar tras revisión: cambiar únicamente el flag a true y aplicar el
procedimiento habitual autorizado de construcción/recreación de la API. No se hizo aquí.
Si se activa sin clave, el arranque falla con un mensaje genérico.

## Comportamiento y límites

- Se reutiliza el mismo Prompt de Spring AI: pregunta original, fragmentos recuperados,
  idioma, instrucciones y restricciones de citas. Sin herramientas, historial ni corpus completo.
- Recuperación, embeddings, pgvector y validación de citas/abstenciones no cambian.
- La traducción auxiliar y la precarga siguen siendo exclusivamente locales.
- No se intenta OpenAI por lentitud, abstención o mala calidad de una respuesta local.
- Una petición HTTP remota, sin retry: conexión 3 s y lectura 45 s; máximo 32.000
  caracteres de entrada y 2.048 tokens de salida (incluido razonamiento). Un remoto
  simultáneo, sin cola. El rate limit HTTP existente se conserva.
- `store=false`. Se aceptan solo respuestas `completed` con texto de asistente.
  HTTP 429/5xx, timeouts, respuestas incompletas o vacías terminan en error controlado.
- Los logs de consumo contienen solo input/output tokens, no preguntas, fragmentos,
  respuestas de error del proveedor ni credenciales. Estos límites por petición no
  sustituyen un presupuesto de cuenta en OpenAI.

## Identificación

`/api/rag/answers` conserva answer/abstained/citations y añade `provider` opcional,
correlacionado con la ejecución de esa petición; no se usa el último proveedor global
para atribuirle la respuesta. Ejemplo remoto:

```json
{"name":"OpenAI","model":"gpt-6-luna","inferenceHost":{"type":"cloud","hostname":null,"cpu":null,"ram":null,"gpu":null}}
```

`/api/rag/runtime` añade `remoteFallback: {enabled, model}`. No consulta `/api/ps`
en OpenAI. La UI ES/EN identifica nube y oculta hardware/memoria local para su respuesta.
Los agentes de desarrollo GPT-5.6 Luna/Codex son distintos del respaldo RAG GPT-6 Luna.

## Pruebas ejecutadas

- Mocks: PC operativo, PC caído y portátil operativo, ambos locales caídos,
  fallo de los tres, flag deshabilitado y abstención local sin invocación remota.
- Adaptador: contrato Responses, HTTP 429, timeout, incomplete, failed y texto vacío;
  una sola petición y errores saneados.
- RAG remoto simulado: mismo contexto, idioma ES/EN y citas; metadatos del controlador.
- Runtime remoto: ningún sondeo Ollama ni atribución de hardware local.
- Configuración Spring: dos targets por defecto; tres solo al habilitar.
- Prueba real opt-in Luna: **PASS** con fragmento de prueba controlado (recuperación
  simulada), locales simulados como inaccesibles y generación OpenAI real. No se
  presenta como una nueva prueba de ingesta/pgvector de producción.
  Respuesta: `Yes, Riansares 4R renovates small offices. [S1]`.
  Proveedor/modelo: `OpenAI` / `gpt-6-luna`. Consumo: **153 input / 21 output tokens**.
  Una sola llamada real. Sigue deshabilitado en la configuración normal.

Comandos:

```bash
mvn verify
node front-athagon-r4r-static/js/app.test.cjs
RAG_LUNA_LIVE_TEST=true mvn -Dtest=NoUnitTests -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=LunaLiveIT verify
```

El último comando incurre en consumo real y solo se debe lanzar expresamente.
Las suites live antiguas ES/EN no se repitieron en este encargo; quedan opt-in.

## Coste orientativo

Tarifa Standard consultada: USD 0,10/M input y USD 0,50/M output; cached input
USD 0,01/M. [Precios oficiales](https://developers.openai.com/api/docs/pricing).
Sin descontar caché: `(input × 0,10 + output × 0,50) / 1.000.000`.
La consulta real: **USD 0,0000258**. Ejemplo de 3.000 input + 300 output:
**USD 0,00045**. Es una estimación de tarifa estándar, no un presupuesto garantizado;
puede variar con tarifas, nivel de servicio, región e impuestos.
[Responses API](https://developers.openai.com/api/docs/guides/text).

## Archivos de esta integración

- `DEVELOPMENT.md`.
- `docker-spring/.env.base`.
- `front-athagon-r4r-static/index.html`.
- `front-athagon-r4r-static/js/app.js`.
- `front-athagon-r4r-static/js/app.test.cjs`.
- `front-athagon-r4r-static/js/i18n.js`.
- `src/main/java/com/riansares/r4r/rag/GenerationRouter.java`.
- `src/main/java/com/riansares/r4r/rag/RagConfiguration.java`.
- `src/main/java/com/riansares/r4r/rag/api/RagQueryController.java`.
- `src/main/java/com/riansares/r4r/rag/api/RagQueryResponse.java`.
- `src/main/java/com/riansares/r4r/rag/api/RagRuntimeController.java`.
- `src/main/resources/application.yml`.
- `src/test/java/com/riansares/r4r/config/OllamaServerSeparationTest.java`.
- `src/test/java/com/riansares/r4r/rag/api/RagQueryControllerTest.java`.
- `src/test/java/com/riansares/r4r/rag/api/RagRuntimeControllerTest.java`.
- `docs/operations/openai-fallback.md` (nuevo).
- `src/main/java/com/riansares/r4r/rag/OpenAiResponsesModel.java` (nuevo).
- `src/test/java/com/riansares/r4r/llm_tests/LunaLiveIT.java` (nuevo).
- `src/test/java/com/riansares/r4r/rag/OpenAiFallbackTest.java` (nuevo).
- `docker-spring/.env`: archivo local ignorado; solo secretos y sobrescrituras; flags y modelo públicos en `.env.base`.

Resultado de `mvn verify`: 131 unitarios y 23 integraciones sin LLM pasan; tres contenedores live omitidos por defecto. LunaLiveIT se ejecutó expresamente y pasó (una llamada real). Frontend estático: 67/67. Chromium con runtime simulado: ocho combinaciones ES/EN y 320/390/768/1440 px, sin desbordamientos ni excepciones JavaScript; proveedor nube sin hardware local.

Suite Angular existente: 19/19 PASS, sin cambios en su código.
