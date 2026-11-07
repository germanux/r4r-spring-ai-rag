# Benchmark RAG y proveedores

**F fue la más rápida medida: 8/8 PASS y 2,73 s de media E2E SSE. Validación final con preguntas corregidas: 30/30 PASS.**

## Comparativa A–F conservada

Embeddings → generación. Milisegundos; medias calculadas solo con muestras PASSED. Los fallos quedan aparte. No se repitieron estos benchmarks al corregir los tests. La comparación utilizó la formulación anterior del caso 10 y el prompt V1 conservado en el historial; no se mezclan esas métricas con las de la validación final.

| Configuración | IT PASS/FAIL | Media E2E | Mediana E2E | Media embedding | Media generación |
| --- | ---: | ---: | ---: | ---: | ---: |
| [A: Synology → PC](embed-synology_llm-pc.md) | 8/0 | 8672.71 | 9027.81 | 5787.68 | 2375.25 |
| [B: PC → PC](embed-pc_llm-pc.md) | 8/0 | 3045.70 | 2279.15 | 563.00 | 1976.90 |
| [C: Synology → portátil](embed-synology_llm-laptop.md) | 6/2 | 19369.55 | 16215.58 | 6243.95 | 11378.11 |
| [D: portátil → portátil](embed-laptop_llm-laptop.md) | 8/0 | 10131.84 | 9275.15 | 268.78 | 8075.85 |
| [E: PC → portátil](embed-pc_llm-laptop.md) | 8/0 | 8734.26 | 7361.68 | 120.53 | 6830.71 |
| [F: portátil → PC](embed-laptop_llm-pc.md) | 8/0 | 2725.89 | 2846.88 | 178.82 | 2020.32 |

C registró una abstención en ES10 y una traducción rechazada en EN02; no fue un timeout de esa traducción. Los informes individuales conservan métricas, errores, citas, memoria y resultados históricos. Los asserts de la primera comparación no eran una evaluación semántica exhaustiva: la revisión del caso causal 10 motivó su reformulación autorizada y reglas de fundamentación más estrictas.

## Correcciones y validación

- Preguntas reordenadas; 10 ahora pregunta por el requisito de incluir plazos, y 6 concreta pavimentación. Las versiones anteriores y la regresión causal quedan identificadas en los tests y el informe final. 11/12 y 13–15 se mantienen.
- Recuperación: Top-K 5, mismo umbral 0,63; encabezados vacíos completados con hasta tres fragmentos hijos de la misma fuente/sección, sin reindexar. La FAQ de presupuestos estaba fuera del Top-3; el encabezado pertinente aparecía quinto.
- Spring AI 1.0 no enviaba think=false; se respeta esa configuración para Qwen3 mediante el cliente existente. La captura confirmó cero razonamiento adicional. Se completó el glosario local y se acotó su timeout a 30 s. MyMemory y los proveedores no se sustituyen.
- Citas FAQ normalizadas solo cuando su identificador existe en la evidencia; siguen rechazándose referencias inventadas. El [historial de prompts](prompt-before-citation-fix.md) conserva las versiones anteriores.
- Suite unitaria: **184 PASSED, 0 FAILED, 0 SKIPPED**, `mvn -q test`. Los [30 resultados finales](final-validation.md) reutilizan 26 consultas sin cambios y cuatro nuevas, sin alterar retrospectivamente sus resultados.
- Failover existente: pruebas unitarias con mocks PASSED; PC→PC y PC con endpoint falso→ASUS se comprobaron realmente durante la tarea. Selección de Luna cubierta con mocks, sin llamadas de pago. No se detuvieron servicios existentes.

## Recomendación y límites

F es la candidata recomendada por la latencia observada y la validación final indicada arriba. Requiere mantener el portátil disponible para embeddings. No se cambió la configuración operativa ni se afirma que .env haya adoptado esa opción. Para activarla, la variable existente es `RAG_EMBEDDING_BASE_URL=http://192.168.1.209:11434`; se mantienen generación PC/qwen3:8b y fallback ASUS/qwen2.5:7b. Una variable operativa puede prevalecer sobre defaults versionados.

No se midieron vatios ni disponibilidad a largo plazo. Las observaciones de VRAM acreditan residencia, no utilización o competencia simultánea: las consultas fueron secuenciales. No se fuerza descarga/carga de modelos. La validación ES/EN de backend usa traducción local; no mide MyMemory del navegador. C conserva fallos históricos, por lo que no se afirma estabilidad universal de los seis proveedores/configuraciones.

PostgreSQL se utilizó en solo lectura: Flyway=false, SQL init=never, initialize-schema=false, catalog=false, warmup de arranque=false y OpenAI=false. Se verificaron transacción de solo lectura, vector(768), 282 filas y fingerprint `58059dbac293f4ce2db72d942552610e` antes/después. No se leyó ni modificó .env o DEVELOPMENT.md. Los IT de ingesta/TRUNCATE/migración no se ejecutan contra el corpus existente.


## Archivos de la tarea

Modificados:
- Backend: `CitedRagService.java`, `LocalRetrievalQueryTranslator.java`, `RagConfiguration.java`, `PgVectorKnowledgeStore.java` y `src/main/resources/application.yml`.
- Tests existentes: `RagLiveQuestionsIT.java`, `RagLiveEnglishQuestionsIT.java`, `CitedRagServiceInvalidAnswersTest.java` y `LocalRetrievalQueryTranslatorTest.java`.
- Documentación: README raíz, README comparativo, los cinco informes A–E y `final-validation.md`.

Nuevos:
- `src/main/java/com/riansares/r4r/rag/OllamaThinkingInterceptor.java`.
- `src/test/java/com/riansares/r4r/rag/OllamaThinkingInterceptorTest.java`.
- `src/test/java/com/riansares/r4r/vector/PgVectorSectionContextTest.java`.
- `docs/benchmarks/rag-providers/embed-laptop_llm-pc.md`.
- `docs/benchmarks/rag-providers/prompt-before-citation-fix.md`.

Durante el cierre solicitado solo se ajustaron tests y documentación; no se añadieron más cambios de producción ni se repitió la comparativa.

<details><summary>Inventario, numeración y resultados históricos conservados</summary>

# Benchmark RAG y proveedores — comparación A–F tras la reparación

Los resultados nuevos emplean ocho consultas por configuración, casos **01, 02, 03 y 10 ES/EN de la nueva numeración**. Se conservan más abajo el inventario y las métricas históricas de seis preguntas, con otros parámetros; no son muestras intercambiables.

## Diagnóstico y correcciones

1. **Recuperación:** en el caso de presupuestos, el Top-3 original devolvió negociación de precios (0,7532), contenido sobre humedades (0,7359) y un encabezado vacío de presupuestación (0,7195). El LLM devolvió literalmente `__R4R_ABSTAIN__`: no era un falso positivo del detector. La FAQ-001 estaba almacenada, pero aparecía en el puesto 149 con 0,5800; no se bajó el umbral 0,63 ni se cambiaron vectores.
2. El encabezado de la sección FAQ «Presupuesto» sí estaba quinto (0,6980). Se usan **cinco candidatos** y los encabezados sin cuerpo se completan mediante SELECT de hasta tres descendientes de la **misma fuente y sección**, con límite de 6.000 caracteres. Se conserva la atribución a esa fuente/sección. Los chunks completos no ejecutan consultas adicionales; no se recalculan embeddings. Las FAQ aportadas son contexto real, no datos fabricados.
3. **Formato:** después de recuperar la FAQ, el modelo dio la respuesta correcta con `[S5: FAQ-001]`; el rechazo era `missing_citations`. Se precisa el formato en el prompt y se normaliza esa variante únicamente si el número de fuente es válido y su contenido incluye literalmente ese identificador FAQ. Se siguen rechazando fuentes inexistentes, FAQ inventadas, índices no canónicos, respuestas vacías y citas sin respuesta. El [prompt anterior](prompt-before-citation-fix.md) queda conservado para reproducir la regresión.
4. **Traducción:** una muestra válida necesitó 15,64 s frente al límite anterior de 12 s. El límite configurable pasa a 30 s (`RAG_TRANSLATION_READ_TIMEOUT`). El diagnóstico posterior demostró que aumentar el límite no bastaba: Spring AI 1.0.0 no serializaba `think`, aunque el YAML declaraba false. Una captura real mostró 1.266 caracteres de razonamiento/298 tokens y 9,34 s para traducir una frase. Se adapta exclusivamente la petición Qwen3 del cliente Spring AI para enviar el valor configurado de `think`; no se crea otro cliente HTTP ni se cambia de proveedor. Con false real, otra muestra informó cero caracteres de razonamiento, 16 tokens y 1,81 s. Son muestras diagnósticas, no un ensayo controlado de rendimiento. [Ollama documenta el control nativo](https://ollama.com/blog/thinking).
5. **Terminología:** «estimate» se tradujo como «estimación» y desvió el contexto. El glosario de traducción se completa con estimate/presupuesto, concrete slab/solera, flooring/solado e initial advice/primera orientación. La pregunta original y el idioma de respuesta se conservan. MyMemory del frontend, el orden de failover y sus proveedores no se sustituyen.

Se conserva una diferencia heredada de las suites: ES01 pregunta por primera orientación y EN01 por servicios de la empresa. No se cambian sus textos para fabricar un par equivalente; todas las configuraciones reciben exactamente los mismos ocho casos.

No se añadieron preguntas 16/17: los casos 11/12 y las regresiones unitarias cubren las variantes observadas. No se cambiaron expectativas ni se presenta una abstención inesperada como PASS.

## Prevalidación y tests unitarios

### prevalidation-core: 2/2 PASSED

| Caso | Resultado | Total backend ms | Respuesta/abstención |
| --- | --- | ---: | --- |
| ES 11 | PASSED | 1729.21 | Respuesta con citas |
| EN 11 | PASSED | 2295.99 | Respuesta con citas |
### prevalidation-rest: 8/8 PASSED

| Caso | Resultado | Total backend ms | Respuesta/abstención |
| --- | --- | ---: | --- |
| ES 12 | PASSED | 1653.65 | Respuesta con citas |
| ES 13 | PASSED | 476.13 | Abstención |
| ES 14 | PASSED | 55.92 | Abstención |
| ES 15 | PASSED | 551.05 | Abstención |
| EN 12 | PASSED | 3982.95 | Respuesta con citas |
| EN 13 | PASSED | 1703.26 | Abstención |
| EN 14 | PASSED | 827.58 | Abstención |
| EN 15 | PASSED | 1354.05 | Abstención |
### prevalidation-laptop: 4/4 PASSED

| Caso | Resultado | Total backend ms | Respuesta/abstención |
| --- | --- | ---: | --- |
| ES 11 | PASSED | 9871.83 | Respuesta con citas |
| ES 12 | PASSED | 6998.69 | Respuesta con citas |
| EN 11 | PASSED | 14296.76 | Respuesta con citas |
| EN 12 | PASSED | 12097.43 | Respuesta con citas |

Suite unitaria Java: **184 tests, 0 fallos, 0 errores, 0 omitidos**, `mvn -q test`. Incluye las regresiones RAG, traducción, clasificación, citas, SSE, precarga y failover existentes; se añaden pruebas aisladas de expansión acotada de sección y de serialización de think, y se amplían los casos de citas. El primer intento restringido de Mockito falló por instrumentación del entorno; la ejecución completa posterior con permisos adecuados pasó. No se presenta aquel intento como fallo funcional corregido por cambios de producto.

## Comparativa nueva

Milisegundos. Las medias de éxito **excluyen fallos y timeouts**, que se contabilizan aparte. E2E SSE es POST→ANSWER_READY/ERROR medido por el cliente; total backend procede del evento final. No incluye las consultas /api/ps del adaptador ni MyMemory del frontend.

| Configuración | PASS/FAIL | Media E2E éxito | Mediana E2E éxito | Media total backend éxito | Media embedding éxito (n) | Media generación éxito (n) | Media total backend fallos (n) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| [A: Synology → PC](embed-synology_llm-pc.md) | 8/0 | 8672.71 | 9027.81 | 8669.84 | 5787.68 (8) | 2375.25 (8) | null (0) |
| [B: PC → PC](embed-pc_llm-pc.md) | 8/0 | 3045.70 | 2279.15 | 3042.34 | 563.00 (8) | 1976.90 (8) | null (0) |
| [C: Synology → portátil](embed-synology_llm-laptop.md) | 6/2 | 19369.55 | 16215.58 | 19365.68 | 6243.95 (6) | 11378.11 (6) | 7035.96 (2) |
| [D: portátil → portátil](embed-laptop_llm-laptop.md) | 8/0 | 10131.84 | 9275.15 | 10128.69 | 268.78 (8) | 8075.85 (8) | null (0) |
| [E: PC → portátil](embed-pc_llm-laptop.md) | 8/0 | 8734.26 | 7361.68 | 8730.70 | 120.53 (8) | 6830.71 (8) | null (0) |
| [F: portátil → PC](embed-laptop_llm-pc.md) | 8/0 | 2725.89 | 2846.88 | 2722.79 | 178.82 (8) | 2020.32 (8) | null (0) |

Cada informe incluye las ocho muestras, respuestas, citas, estado de memoria y errores. `retrievalIncludingEmbeddingMs` incluye el embedding, búsqueda y lectura de contexto de sección; `vectorSearchMs=null` porque no existe cronómetro aislado. No se calcula por resta ni se duplica ninguna operación para medirla.

El inventario se conserva: los tres nomic tienen digest `0a109f422b47e3a30ba2b10eca18548e944e8a23073ee3f3e947efcf3c45e59f`. Cada arranque exige `vector(768)`, corpus de 282 filas y fingerprint `58059dbac293f4ce2db72d942552610e`, verificado antes/después. No se leyó ni modificó .env o DEVELOPMENT.md. Spring Boot importó normalmente su configuración local desde la raíz.

## Método, seguridad y límites

- Se ejecutan los DynamicTest y los asserts originales mediante un lanzador temporal de selección, sin modificar su comportamiento. Un adaptador local convierte el único SSE en el JSON final esperado por el IT; no se repite REST para obtener métricas. Se conservan las pausas originales de 21 s y se añade una pausa de 21 s entre suites cuando empiezan por 01 para respetar el límite existente por IP.
- Antes de crear beans se verifican Flyway=false, SQL init=never, initialize-schema=false, catalog-on-startup=false, startupWarmup=false, OpenAI=false y conexiones de solo lectura. También se comprueban `default_transaction_read_only=on` y `transaction_read_only=on`. No se crearon usuarios ni se cambiaron permisos; no se afirma que el usuario existente tenga exclusivamente SELECT.
- Se fuerza Top-K 5, score 0,63, traducción 30 s y think=false en todos los arranques comparados. El target medido se configura temporalmente como primario, con su modelo real. El endpoint de fallback es deliberadamente inaccesible y OpenAI está deshabilitado. Si falla el target, se registra fallo; nunca se atribuye generación de otro equipo al seleccionado.
- PC: qwen3:8b; portátil: qwen2.5:7b. El nombre del rol principal del router puede seguir siendo «PC principal» en un ensayo del portátil; se verifica identidad real por `inferenceHost.type` y modelo. El timeout del target primario es 35 s en todos estos ensayos; el timeout operativo de 60 s del portátil como fallback se conserva y no se utiliza cuando se mide como primario.
- No se fuerzan descargas ni precargas. /api/ps se registra antes de cada configuración y consulta y después de cada consulta; las medias incluyen las cargas naturales. Los eventos confirman que embedding y generación se ejecutan secuencialmente: este ensayo observa residencia y transición entre modelos, no competencia de cómputo simultánea inducida por carga. Una sola pasada y cachés no permiten aislar causalmente el efecto de compartir GPU. size_vram indica asignación de memoria, no utilización de GPU. No se midieron vatios ni disponibilidad a largo plazo.
- No se ejecutan IT de ingesta/TRUNCATE/migración contra el corpus existente. Las regresiones de escritura de esos IT quedan fuera de esta validación de solo lectura. No se descarga ningún modelo ni se hacen llamadas de pago.

## Selección y validación final

Candidata de menor media E2E entre las que superan 8/8: **F — portátil → PC**, 2725.89 ms. La declaración definitiva depende de los 30 casos completos y de la disponibilidad operativa.

La disponibilidad importa: si los embeddings dependen del PC o del portátil y ese equipo se apaga, la consulta puede fallar antes de llegar al failover de generación. El NAS mantiene separada esa dependencia. Mover embeddings a un equipo de generación exige aceptar explícitamente ese compromiso; esta tarea no incorpora un failover de embeddings ni modifica silenciosamente la configuración operativa. La comparación de latencias no mide por sí sola ese riesgo ni el coste energético.

Véase [final-validation.md](final-validation.md). No se hace commit, push ni despliegue. Se detienen únicamente los procesos iniciados por esta tarea.

## Resultados históricos conservados

### Comparación histórica anterior: seis consultas por configuración

**Comparación completada: 30 consultas únicas, 18 PASSED y 12 FAILED. Ninguna configuración cumple el requisito de seis consultas correctas; no hay ganadora elegible.**

El bloqueo anterior se resolvió arrancando desde la raíz con la configuración habitual. Spring Boot importó automáticamente su archivo local; el asistente no leyó ni modificó `.env` ni `DEVELOPMENT.md`. No se solicitaron credenciales nuevas.

## Tabla comparativa

Tiempos en milisegundos. Entre paréntesis, número de mediciones disponibles para cada media parcial. Las medias totales incluyen fallos funcionales y errores con tiempo observado; no son latencias de seis respuestas correctas.

| Configuración | Media total | Mediana total | Máximo | Media embedding (n) | Media generación (n) | PASSED / FAILED |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| [A: synology → pc](embed-synology_llm-pc.md) | 15692.20 | 14761.50 | 25269.70 | 5216.24 (4) | 9620.44 (4) | 3 / 3 |
| [B: pc → pc](embed-pc_llm-pc.md) | 14459.64 | 12391.53 | 19767.61 | 1769.71 (4) | 11126.19 (4) | 3 / 3 |
| [C: synology → laptop](embed-synology_llm-laptop.md) | 14450.56 | 14796.18 | 19329.87 | 5012.69 (6) | 7753.65 (6) | 4 / 2 |
| [D: laptop → laptop](embed-laptop_llm-laptop.md) | 8560.25 | 8739.94 | 16179.04 | 445.07 (6) | 6255.04 (6) | 4 / 2 |
| [E: pc → laptop](embed-pc_llm-laptop.md) | 8043.53 | 7522.07 | 15534.33 | 180.93 (6) | 6064.34 (6) | 4 / 2 |

Las tablas de cada configuración contienen las seis latencias individuales, métricas parciales, respuesta, citas, eventos, errores y estados reales `/api/ps` antes/después.

## Diagnóstico funcional

- Ocho consultas terminan en abstención inesperada sobre presupuestos. Se recuperan **tres fragmentos**, se ejecuta generación y el log registra `RAG result=model_abstention`. El fallo no es una búsqueda sin documentos ni un error de conectividad. El servicio agrupa respuestas vacías, token de abstención y patrones de abstención natural en esa rama; sin la salida cruda no se distingue cuál de ellos ocurrió.
- Cuatro consultas inglesas fallan después de `TRANSLATION_STARTED`, sin iniciar embeddings: A/EN02, A/EN05, B/EN05 y B/EN11. Se registra `RestClientException` alrededor de 12 s, compatible con el límite configurado de traducción local. No se atribuye ese tiempo a embeddings ni a generación RAG.
- El corpus del repositorio sí contiene información sobre presupuestos: `knowledge/faqs/R4R_40_ALL_FAQ.md`, FAQ-001, enumera ubicación, fotografías, medidas, uso, problema, resultado esperado y plazo. Eso no demuestra que los tres fragmentos concretamente recuperados contengan esa evidencia: el stream no devuelve fragmentos/puntuaciones de una respuesta abstendida. Queda por discriminar selección de contexto frente a respuesta/validación del modelo, sin repetir consultas ni rebajar protecciones.
- Los casos correctos conservan respuesta y citas verificadas por los asserts originales. No se han cambiado preguntas, expectativas, prompts, umbrales ni configuración de traducción.

## Selección exacta

[RagLiveQuestionsIT.java](../../../src/test/java/com/riansares/r4r/llm_tests/RagLiveQuestionsIT.java) y [RagLiveEnglishQuestionsIT.java](../../../src/test/java/com/riansares/r4r/llm_tests/RagLiveEnglishQuestionsIT.java), casos 02, 05 y 11. Son tres pares de casos existentes en dos clases IT; no se han creado preguntas ni cambiado expectativas.

1. **ES · RagLiveQuestionsIT · caso 2:** En una obra, ¿en qué se distinguen la base de solera y el acabado de solado?
2. **ES · RagLiveQuestionsIT · caso 5:** ¿Qué datos debe aportar el cliente para solicitar un presupuesto?
3. **ES · RagLiveQuestionsIT · caso 11:** ¿Qué datos hacen falta para orientar una reforma de baño?
4. **EN · RagLiveEnglishQuestionsIT · caso 2:** In construction, how does the concrete base differ from the finished flooring?
5. **EN · RagLiveEnglishQuestionsIT · caso 5:** What information should a customer provide to request an estimate?
6. **EN · RagLiveEnglishQuestionsIT · caso 11:** What information is needed to estimate a bathroom renovation?

Se conserva este orden para las cinco configuraciones. Top-K 3, score mínimo 0,63,
modelo nomic de 768 dimensiones y corpus constantes. La petición inglesa reproduce
el IT existente: question original, responseLanguage=en y retrievalQuestion ausente;
se incluye por tanto la normalización local del backend. No mide la traducción
MyMemory del frontend. Las expectativas siguen siendo las de los IT, incluido el
chequeo inglés y la correspondencia de referencias/citas; esto no constituye una
validación semántica exhaustiva del contenido.

## Inventario real y compatibilidad

Se consultaron /api/tags y /api/ps sin modificar modelos. Los tres servidores tienen
nomic-embed-text:latest, F16, digest
`0a109f422b47e3a30ba2b10eca18548e944e8a23073ee3f3e947efcf3c45e59f`
y 768 dimensiones. Qwen3 8B está instalado en el PC y Qwen2.5 7B en el portátil.
Una consulta inicial al portátil devolvió «No route to host»; consultas posteriores
a tags/ps sí funcionaron. No se ocultó esa disponibilidad transitoria.

Comprobación actual en las cinco configuraciones: 282 filas, `vector(768)`, conexiones en solo lectura y el mismo fingerprint antes/después: `58059dbac293f4ce2db72d942552610e`. No se ha reindexado ni modificado el corpus.

## CPU, GPU y co-residencia

- Synology: nomic observado con `size_vram=0`, aproximadamente 0,36 GiB de memoria informada. Sus medias de embedding fueron 5,22 s (A, n=4) y 5,01 s (C, n=6).
- PC: inicialmente sin modelos residentes. Durante B/E se observó nomic y Qwen3 residentes con asignaciones de VRAM. El fallo inicial de `nvidia-smi` al contactar con el driver **no demuestra ausencia de uso de GPU**. No se midió utilización GPU.
- Portátil: Qwen2.5 informó aproximadamente 4,67 GiB totales y 2,89 GiB VRAM. En D, nomic coexistió con Qwen y mostró unos 0,096 GiB de VRAM. Hay residencia parcial en GPU, sin medida de ocupación o competencia de cómputo.
- E obtuvo la menor media observada (8,04 s); D quedó en 8,56 s, aproximadamente 6,4 % más. Esta pasada no permite aislar causalmente el efecto de compartir GPU: cambian host de embeddings, cargas iniciales y cachés. E mantiene Qwen3 residente en el PC por las configuraciones anteriores, aunque allí no se solicita generación RAG.
- Orden efectivo **B → C → D → E → A**; las seis preguntas mantienen el mismo orden en todas las variantes. A se inició inicialmente sin llegar a recibir consultas; se completó al final. No se repitieron muestras, precargaron modelos ni forzaron desalojos. Una pasada no mide disponibilidad a largo plazo ni constituye un ensayo de carga.

## Método y protección de PostgreSQL

Se reutilizaron los ejecutables DynamicTest originales de ambos IT, seleccionando 02/05/11 con un lanzador Java temporal fuera del repositorio. Un adaptador local entrega el JSON final al assert original tras **una sola** petición SSE; no se ejecuta REST y SSE para la misma pregunta. Se conservan las pausas originales de 21 s. El HTTP 503 del adaptador en los errores representa un evento ERROR sobre HTTP SSE 200; no se presenta como HTTP 503 del backend original.

Los tiempos son los medidos por el backend, excluyen las observaciones `/api/ps` del adaptador. `embeddingMs` se observa sin duplicar embeddings; está incluido en `retrievalIncludingEmbeddingMs`. `vectorSearchMs=null`: no hay cronómetro aislado de búsqueda vectorial; no se fabrica por resta. El total inglés incluye normalización local conforme al IT, no MyMemory del frontend. Los errores previos a embeddings tienen las métricas no ejecutadas en null.

Overrides temporales, conservando la importación normal de configuración:

```text
--spring.flyway.enabled=false
--spring.sql.init.mode=never
--spring.ai.vectorstore.pgvector.initialize-schema=false
--spring.datasource.hikari.read-only=true
--spring.datasource.hikari.connection-init-sql=SET default_transaction_read_only=on
--r4r.catalog-on-startup=false
--r4r.rag.warmup.on-startup=false
--r4r.rag.openai.enabled=false
```

El lanzador verifica esos valores antes de crear beans. Se verificó `default_transaction_read_only=on`, `vector(768)`, 282 filas y fingerprint idéntico antes/después en las cinco configuraciones. No hubo Flyway, SQL de inicialización, catálogo, ingesta o reindexado. Los mensajes genéricos de inicialización/validación de PgVectorStore no implican creación de esquema: `initialize-schema=false` y PostgreSQL en solo lectura impiden las escrituras.

Se cambiaron únicamente `r4r.rag.embedding.base-url`, `r4r.rag.generation.pc-base-url` y `pc-model` temporalmente. Durante la comparación el fallback apuntó a un endpoint local imposible y OpenAI estuvo deshabilitado: no se atribuyen tiempos de otro proveedor al seleccionado. El host real se comprobó mediante modelo e `inferenceHost.type`; el nombre del rol principal sigue siendo PC aunque temporalmente apunte al portátil. Presupuesto constante de lectura: 35 s para el target principal medido, 12 s para traducción y 180 s para SSE. Los 60 s del portátil como fallback no se aplican cuando se mide como target principal.

## Failover

| Comprobación | Resultado | Modelo real | Tiempo total |
| --- | --- | --- | ---: |
| pc-available | PASSED | qwen3:8b · desktop | 16378.6596 ms |
| pc-false-laptop-available | PASSED | qwen2.5:7b · laptop | 10194.800507 ms |
| Ambos locales fallan → OpenAI Luna | PASSED con mocks; sin llamada de pago | gpt-6-luna (simulado) | No comparable |

- pc-available: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY.
- pc-false-laptop-available: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → PROVIDER_FAILED → FAILOVER_STARTED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY.

Regresión existente ejecutada: `mvn -Dtest=OpenAiFallbackTest#orderFailureDisableAndAbstentionNeverSpendUnnecessarily test`, PASSED (un método, escenarios de orden, deshabilitación, fallo y abstención). No se apagaron servicios; solo se sustituyó temporalmente el endpoint del PC por un puerto local sin servicio para comprobar el salto al portátil.

## Recomendación y validación final

**No activar ninguna variante como ganadora de producción con estos resultados.** E es la candidata de rendimiento; tanto E como D fallan las dos preguntas sobre presupuesto. E sería la primera candidata a reevaluar después de diagnosticar esa abstención, pero no cumple la condición de seis respuestas correctas. Se conserva la configuración habitual, sin proclamarla validada por este benchmark.

Los **30 IT finales están NOT RUN** porque no existe ganadora elegible. Se solicitó aclaración para ejecutarlos sobre E solo como candidata diagnóstica; no se interpreta la ausencia de respuesta como aprobación. Véase [final-validation.md](final-validation.md). Esta evidencia no permite afirmar que el MVP esté listo para publicar.

No se cambió configuración versionada ni `.env`. Para un eventual ensayo de E, los overrides son `RAG_EMBEDDING_BASE_URL` apuntando al Ollama del PC, `RAG_PC_OLLAMA_BASE_URL` al portátil y `RAG_PC_GENERATION_MODEL=qwen2.5:7b`; esto reutiliza el rol principal del benchmark, no define un cambio operativo definitivo de roles. Las variables de entorno/importadas pueden prevalecer sobre defaults versionados; no se afirma que la candidata esté aplicada. El orden operativo y sus etiquetas requieren revisión explícita antes de seleccionar al portátil como principal.

Sin commit, push ni despliegue. Se detuvieron únicamente los procesos Spring Boot iniciados por la tarea. No se leyó ni modificó `.env` o `DEVELOPMENT.md`.

## Reanudación: nueva numeración y reparación

Los resultados anteriores son históricos: emplearon casos antiguos 02/05/11, Top-K 3 y traducción local con límite de 12 s. No son equivalentes al nuevo benchmark A–F (01/02/03/10 ES/EN, Top-K 5 y límite configurable de traducción 30 s). Se conservan sin sobrescribir sus métricas.

| Número nuevo | Número antiguo |
| --- | --- |
| 01 | 01 |
| 02 | 02 |
| 03 | 03 |
| 04 | 04 |
| 05 | 06 |
| 06 | 07 |
| 07 | 08 |
| 08 | 09 |
| 09 | 10 |
| 10 | 12 |
| 11 | 05 |
| 12 | 11 |
| 13 | 13 |
| 14 | 14 |
| 15 | 15 |

Los casos históricos 02/05/11 corresponden ahora a 02/11/12. Las preguntas 11 y 12 exigen respuesta fundamentada; 13–15 exigen abstención. Los textos, expectativas, pausas y tratamiento de idioma de los IT permanecen intactos. Los selectores de ejecución nuevos usan la nueva numeración; los informes anteriores retienen explícitamente la antigua.

</details>
