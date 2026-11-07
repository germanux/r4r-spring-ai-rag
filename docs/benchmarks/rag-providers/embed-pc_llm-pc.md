# B — Embeddings pc + generación pc

> Histórico: los casos antiguos 02/05/11 corresponden a los actuales 02/11/12. Las métricas de esa pasada se conservan; la nueva comparación usa 01/02/03/10 ES/EN y se documenta por separado.

**Estado: COMPLETED; 3/6 PASSED, 3/6 FAILED.** Una única ejecución por pregunta mediante SSE.

## Modelos y hardware

- Embeddings: nomic-embed-text:latest, F16, 768 dimensiones.
- Generación prevista: qwen3:8b.
- Equipo de embeddings: PC de sobremesa; Intel Core i9-14900KF y MemTotal 65.653.192 KiB observados localmente. NVIDIA GeForce RTX 4060 / 8 GB según InferenceHost.java (configuración); nvidia-smi no pudo comunicarse con el driver en esta sesión.
- Equipo de generación: PC de sobremesa; Intel Core i9-14900KF y MemTotal 65.653.192 KiB observados localmente. NVIDIA GeForce RTX 4060 / 8 GB según InferenceHost.java (configuración); nvidia-smi no pudo comunicarse con el driver en esta sesión.
- Digest nomic instalado: `0a109f422b47e3a30ba2b10eca18548e944e8a23073ee3f3e947efcf3c45e59f`.

## Casos seleccionados y orden fijo

[RagLiveQuestionsIT.java](../../../src/test/java/com/riansares/r4r/llm_tests/RagLiveQuestionsIT.java) y [RagLiveEnglishQuestionsIT.java](../../../src/test/java/com/riansares/r4r/llm_tests/RagLiveEnglishQuestionsIT.java), casos 02, 05 y 11. Son tres pares de casos existentes en dos clases IT; no se han creado preguntas ni cambiado expectativas.

1. **ES · RagLiveQuestionsIT · caso 2:** En una obra, ¿en qué se distinguen la base de solera y el acabado de solado?
2. **ES · RagLiveQuestionsIT · caso 5:** ¿Qué datos debe aportar el cliente para solicitar un presupuesto?
3. **ES · RagLiveQuestionsIT · caso 11:** ¿Qué datos hacen falta para orientar una reforma de baño?
4. **EN · RagLiveEnglishQuestionsIT · caso 2:** In construction, how does the concrete base differ from the finished flooring?
5. **EN · RagLiveEnglishQuestionsIT · caso 5:** What information should a customer provide to request an estimate?
6. **EN · RagLiveEnglishQuestionsIT · caso 11:** What information is needed to estimate a bathroom renovation?

## Resultados individuales

Tiempos en ms obtenidos de la misma consulta SSE. `null` significa no disponible/no ejecutado, nunca cero estimado.

| Caso | Validación IT | embeddingMs | retrievalIncludingEmbeddingMs | vectorSearchMs | generationMs | totalMs | Recuperados | Proveedor/modelo real |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ES 02 | PASSED | 6422.79 | 6473.67 | null | 13290.99 | 19767.61 | 3 | PC · qwen3:8b |
| ES 05 | FAILED | 589.37 | 613.85 | null | 10000.25 | 10614.95 | 3 | PC · qwen3:8b |
| ES 11 | PASSED | 37.77 | 63.76 | null | 12702.01 | 12766.90 | 3 | PC · qwen3:8b |
| EN 02 | PASSED | 28.90 | 61.63 | null | 8511.52 | 19583.89 | 3 | PC · qwen3:8b |
| EN 05 | FAILED | null | null | null | null | 12008.31 | null | sin generación RAG final · null |
| EN 11 | FAILED | null | null | null | null | 12016.16 | null | sin generación RAG final · null |

## Agregados

- Media embeddingMs: **1769.71 ms**, 4/6 mediciones disponibles.
- Media generationMs: **11126.19 ms**, 4/6 mediciones disponibles.
- Media totalMs: **14459.64 ms**, 6/6 mediciones disponibles.
- Mediana total: **12391.53 ms**. Máximo: **19767.61 ms**.
- Correctas/fallidas: **3/3**. Las medias incluyen fallos con métricas reales, con denominadores explícitos; no mezclan muestras omitidas.

## Evidencias individuales, citas y memoria

### ES 02 — Solera frente a solado

- HTTP SSE backend: 200; validación: PASSED.
- Respuesta: La **solera** es la base estructural de hormigón o mortero con preparación, espesores y armadura, mientras que el **solado** es la colocación del pavimento final sobre una base apta. Esta distinción se fundamenta en que la solera es la estructura subyacente y el solado corresponde al acabado superficial. [S1], [S2]
- Abstención: False. Coincidencia con proveedor seleccionado: True.
- Cita [S1]: `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` · Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.3. Distinción entre solera y solado
- Cita [S2]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Suelos / FAQ-041 — ¿Qué diferencia hay entre solera y solado?
- Fases observadas: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY

Memoria antes:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: sin modelos residentes
- laptop: sin modelos residentes

Memoria después:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB
- laptop: sin modelos residentes

### ES 05 — Presupuesto

- HTTP SSE backend: 200; validación: FAILED.
- Respuesta: (vacía/ninguna)
- Abstención: True. Coincidencia con proveedor seleccionado: True.
- Fallo del assert original: `AssertionFailedError: [La pregunta es del dominio R4R]  Expecting value to be false but was true`.
- Fases observadas: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY

Memoria antes:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB
- laptop: sin modelos residentes

Memoria después:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.29 GiB
- laptop: sin modelos residentes

### ES 11 — Baño

- HTTP SSE backend: 200; validación: PASSED.
- Respuesta: Para orientar una reforma de baño, se necesitan datos como el **área en metros cuadrados** (ejemplo: 2–6 m², según [S2]) y el **nivel de calidad deseado** (que influye en el costo, como muestra [S1]). Además, es relevante considerar el tipo de reforma (parcial o integral) y materiales seleccionados, según [S3].
- Abstención: False. Coincidencia con proveedor seleccionado: True.
- Cita [S1]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Baños / FAQ-031 — ¿Cuánto cuesta reformar un baño?
- Cita [S2]: `sources/R4R_70_WEB_SOURCE_MANIFEST.md` · Fuentes web consultadas / SRC-HAB-BANO — ¿Cuánto cuesta reformar un baño? Precios 2026
- Cita [S3]: `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md` · Riansares 4R — identidad, criterios y voz editorial / 2. Identidad de Riansares 4R / 2.3. Servicios nucleares / Reformas
- Fases observadas: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY

Memoria antes:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.29 GiB
- laptop: sin modelos residentes

Memoria después:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.29 GiB
- laptop: sin modelos residentes

### EN 02 — Concrete slab versus flooring

- HTTP SSE backend: 200; validación: PASSED.
- Respuesta: In construction, the **concrete base** (solera) is a structural foundation made of concrete or mortar with reinforcement, slopes, and joints, while the **finished flooring** (solado) refers to the final layer of paving installed over an appropriate base. This distinction is explicitly defined in [S1].
- Abstención: False. Coincidencia con proveedor seleccionado: True.
- Cita [S1]: `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` · Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.3. Distinción entre solera y solado
- Fases observadas: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY

Memoria antes:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.29 GiB
- laptop: sin modelos residentes

Memoria después:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.29 GiB
- laptop: sin modelos residentes

### EN 05 — Estimate

- HTTP SSE backend: 200; validación: FAILED.
- Respuesta: (vacía/ninguna)
- Abstención: no disponible. Coincidencia con proveedor seleccionado: False.
- Fallo del assert original: `AssertionFailedError: [HTTP de Estimate: {"error": "processing_failed"}]  expected: 200  but was: 503`.
- Fases observadas: REQUEST_RECEIVED → TRANSLATION_STARTED → ERROR
- Error SSE: `{"stage": "ERROR", "elapsedMs": 12008, "message": "processing_failed", "metrics": {"embeddingMs": null, "vectorSearchMs": null, "retrievalIncludingEmbeddingMs": null, "generationMs": null, "totalMs": 12008.312267}}`.

Memoria antes:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.29 GiB
- laptop: sin modelos residentes

Memoria después:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.29 GiB
- laptop: sin modelos residentes

### EN 11 — Bathroom

- HTTP SSE backend: 200; validación: FAILED.
- Respuesta: (vacía/ninguna)
- Abstención: no disponible. Coincidencia con proveedor seleccionado: False.
- Fallo del assert original: `AssertionFailedError: [HTTP de Bathroom: {"error": "processing_failed"}]  expected: 200  but was: 503`.
- Fases observadas: REQUEST_RECEIVED → TRANSLATION_STARTED → ERROR
- Error SSE: `{"stage": "ERROR", "elapsedMs": 12016, "message": "processing_failed", "metrics": {"embeddingMs": null, "vectorSearchMs": null, "retrievalIncludingEmbeddingMs": null, "generationMs": null, "totalMs": 12016.164297}}`.

Memoria antes:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.29 GiB
- laptop: sin modelos residentes

Memoria después:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.29 GiB
- laptop: sin modelos residentes

## Solo lectura y estabilidad del corpus

```text
BENCHMARK_SAFETY readonly=true flyway=false sqlInit=never schemaInit=false catalog=false openai=false startupWarmup=false
BENCHMARK_CORPUS {"vectorType":"vector(768)","readonly":"on","snapshot":{"rows":282,"fingerprint":"58059dbac293f4ce2db72d942552610e"}}
BENCHMARK_CORPUS_END {"rows":282,"fingerprint":"58059dbac293f4ce2db72d942552610e"}
```

## Observación inicial conservada


Esta es una observación inicial real de /api/ps, no el estado durante seis consultas
que no han podido ejecutarse. No hubo precargas, descargas ni desalojos forzados.

```json
{
  "pc": {
    "models": []
  }
}
```


## Limitaciones y conclusión

Validaciones originales ejecutadas directamente, sin modificar los IT. Un adaptador local convierte un único SSE en el JSON final esperado por REST; los tiempos de la tabla son los del backend, no el tiempo del adaptador con sus lecturas /api/ps. El campo `provider.name` mantiene el nombre del rol principal del router incluso cuando se apunta temporalmente al portátil; la atribución real se comprueba mediante `inferenceHost.type` y modelo.

Se mantuvieron los límites de la configuración cargada: 35 s en el target principal de este benchmark, 12 s para traducción auxiliar y 180 s de consulta SSE. El portátil se midió como target principal; el límite de 60 s de su rol de fallback no se aplica a estas muestras. No hubo cambios de parámetros entre las variantes medidas.

Orden efectivo de configuraciones: B, C, D, E, A. El arranque inicial de A se verificó pero su proceso terminó antes de enviar consultas; A se midió al final sin repetir ninguna pregunta. No hubo precargas ni desalojos forzados. Las diferencias de caché/carga inicial constan en /api/ps y limitan la generalización de una única pasada.

No es elegible como ganadora según el requisito de seis consultas correctas. La abstención tras recuperar tres fragmentos ocurre en generación/validación, no por ausencia de resultados. Los errores tras TRANSLATION_STARTED, alrededor de 12 s y sin EMBEDDING_STARTED, afectan a normalización previa; son compatibles con el límite configurado de traducción. No se modifican prompts, umbrales o expectativas para ocultarlos.

## Nueva comparación A–F

**8/8 PASSED; 0/8 FAILED.** Casos nuevos 01, 02, 03 y 10, primero ES y después EN. Textos y asserts de los IT originales, solo reordenados. Los resultados previos de este archivo son históricos y no equivalentes.

Top-K 5, umbral 0,63, expansión acotada de encabezados sin cuerpo, traducción local con límite configurable de 30 s; corpus inalterado. Una única petición SSE por caso; sin fallback silencioso ni OpenAI.

| Caso | Resultado | Embedding ms | Recuperación incl. embedding ms | Vector ms | Generación ms | Total backend ms | E2E SSE ms | Documentos | Modelo real |
| --- | --- | ---: | ---: | --- | ---: | ---: | ---: | ---: | --- |
| ES 01 | PASSED | 57.39 | 111.93 | null | 1549.57 | 1663.91 | 1676.35 | 5 | qwen3:8b · desktop |
| ES 02 | PASSED | 38.38 | 71.36 | null | 2462.93 | 2535.28 | 2537.75 | 5 | qwen3:8b · desktop |
| ES 03 | PASSED | 31.75 | 70.95 | null | 1532.40 | 1604.32 | 1606.83 | 3 | qwen3:8b · desktop |
| ES 10 | PASSED | 46.24 | 79.21 | null | 1720.51 | 1800.52 | 1802.14 | 5 | qwen3:8b · desktop |
| EN 01 | PASSED | 40.14 | 78.15 | null | 2679.41 | 3733.80 | 3735.77 | 5 | qwen3:8b · desktop |
| EN 02 | PASSED | 39.29 | 73.78 | null | 2427.83 | 3500.11 | 3501.99 | 5 | qwen3:8b · desktop |
| EN 03 | PASSED | 37.76 | 78.87 | null | 1209.35 | 2018.40 | 2020.55 | 4 | qwen3:8b · desktop |
| EN 10 | PASSED | 4213.05 | 4260.69 | null | 2233.15 | 7482.37 | 7484.23 | 5 | qwen3:8b · desktop |

### Agregados separados

| Muestras | n | Media embedding ms (n) | Media generación ms (n) | Media total backend ms (n) | Mediana backend ms | Máximo backend ms | Media E2E SSE ms (n) | Mediana E2E ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Exitosas | 8 | 563.00 (8) | 1976.90 (8) | 3042.34 (8) | 2276.84 | 7482.37 | 3045.70 (8) | 2279.15 |
| Fallos/timeouts | 0 | null (0) | null (0) | null (0) | null | null | null (0) | null |

- ES: 4/4 correctas; media total de éxitos 1901.01 ms.

- EN: 4/4 correctas; media total de éxitos 4183.67 ms.

### Resultados y memoria por consulta

#### ES 01 — Primera orientación

- Pregunta: Si quiero una orientación inicial sobre mi reforma, ¿qué detalles debería facilitar?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: Para una orientación inicial sobre tu reforma, deberías facilitar la ubicación, fotografías, medidas aproximadas, uso del espacio, problema, resultado esperado y plazo. [S1]
- Traducción local ms: no ejecutada
- Cita [S1]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-001 — ¿Qué información hace falta para una primera orientación?
- Eventos: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB

#### ES 02 — Solera frente a solado

- Pregunta: En una obra, ¿en qué se distinguen la base de solera y el acabado de solado?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: La base de solera se distingue del acabado de solado por su función estructural y preparativa, mientras que el solado es la colocación del pavimento final. [S2] La solera es la base estructural o de regularización, mientras que el solado es el pavimento final. [S1]
- Traducción local ms: no ejecutada
- Cita [S1]: `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` · Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.3. Distinción entre solera y solado
- Cita [S2]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Suelos / FAQ-041 — ¿Qué diferencia hay entre solera y solado?
- Eventos: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB

#### ES 03 — Precio por acceso

- Pregunta: ¿Por qué el acceso cambia el precio de una solera?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: El acceso cambia el precio de una solera porque la mezcla y transporte manual aumentan jornadas, coordinación y riesgo frente a descarga directa o bombeo. [S1]
- Traducción local ms: no ejecutada
- Cita [S1]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Patios / FAQ-045 — ¿Por qué el acceso cambia el precio de una solera?
- Eventos: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB

#### ES 10 — Plazos

- Pregunta: ¿Por qué es importante conocer el plazo previsto antes de presupuestar una reforma?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: Conocer el plazo previsto es importante para incluirlo en el presupuesto, ya que los plazos afectan a los costes de mano de obra y a la planificación de la reforma. [S2]
- Traducción local ms: no ejecutada
- Cita [S2]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-003 — ¿Qué debe contener un presupuesto?
- Eventos: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB

#### EN 01 — Company services ES/EN regression

- Pregunta: What kinds of renovation work does this company do? Does it work on houses and offices?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: The company does renovation work including houses, offices, and other buildings. This is supported by [S2], which lists renovations for "viviendas," "pequeñas oficinas y locales," and "reformas." The company also works on corporate projects, as indicated in [S1] and [S4], which mention renovations for offices and corporate spaces.
- Traducción local ms: 975.48
- Cita [S1]: `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` · Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 11. Caso F — Reforma de oficinas y aseos corporativos
- Cita [S2]: `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md` · Riansares 4R — identidad, criterios y voz editorial / 2. Identidad de Riansares 4R / 2.3. Servicios nucleares / Reformas
- Cita [S4]: `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` · Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 11. Caso F — Reforma de oficinas y aseos corporativos / 11.1. Contexto
- Eventos: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB

#### EN 02 — Concrete slab versus flooring

- Pregunta: In construction, how does the concrete base differ from the finished flooring?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: In construction, the concrete base, or solera, is a structured foundation made of concrete or mortar, prepared with specific thicknesses, reinforcement, slopes, and joints. The finished flooring, or solado, refers to the final placement of the flooring material over a suitable base. This distinction is supported by [S1].
- Traducción local ms: 998.01
- Cita [S1]: `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` · Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.3. Distinción entre solera y solado
- Eventos: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB

#### EN 03 — Access and cost

- Pregunta: Why does access affect the price of a concrete slab?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: Access affects the price of a concrete slab because manual mixing and transportation increase working days, coordination, and risk compared to direct discharge or pumping. [S1]
- Traducción local ms: 729.55
- Cita [S1]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Patios / FAQ-045 — ¿Por qué el acceso cambia el precio de una solera?
- Eventos: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB

#### EN 10 — Timelines

- Pregunta: Why is it important to know the expected timeline before preparing a renovation estimate?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: Knowing the expected timeline is important before preparing a renovation estimate because it helps in defining the scope and planning the work steps, which are essential components of a comprehensive estimate [S3]. The timeline also influences the calculation of labor, logistics, and contingencies, ensuring all aspects are accounted for in the budget [S3].
- Traducción local ms: 987.87
- Cita [S3]: `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` · Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 15. Presupuestación con criterio
- Eventos: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB

### Protección del corpus

```text
BENCHMARK_SETTINGS topK=5 minScore=0.63 translationTimeout=30s think=false
BENCHMARK_SAFETY readonly=true flyway=false sqlInit=never schemaInit=false catalog=false openai=false startupWarmup=false
BENCHMARK_CORPUS {"readonly":"on","snapshot":{"rows":282,"fingerprint":"58059dbac293f4ce2db72d942552610e"},"vectorType":"vector(768)"}
BENCHMARK_CORPUS_END {"rows":282,"fingerprint":"58059dbac293f4ce2db72d942552610e"}
```

El total backend procede de las métricas SSE; E2E SSE es el cronómetro real del cliente desde POST hasta recibir ANSWER_READY/ERROR. Ambos excluyen las lecturas /api/ps del adaptador. No incluyen MyMemory del frontend porque se ejecutan los IT del backend. La recuperación incluye embeddings y expansión de secciones. `vectorSearchMs=null`: no se estima por resta. No se ha repetido REST para medir la misma pregunta. Los modelos conservan residencia natural; no se descargan ni desalojan. Las medias de fallos están separadas de las exitosas. No se midió consumo eléctrico ni utilización de GPU, por lo que no se atribuye causalidad energética.
