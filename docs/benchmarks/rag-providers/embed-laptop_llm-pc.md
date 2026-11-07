# F — Embeddings portátil ASUS → generación PC

## Modelos y hardware

- Embeddings: portátil ASUS, nomic-embed-text:latest, 768 dimensiones, digest `0a109f422b47e3a30ba2b10eca18548e944e8a23073ee3f3e947efcf3c45e59f`.
- Generación: PC, qwen3:8b. Hardware configurado: Intel i9-14900KF, 64 GB DDR5, NVIDIA RTX 4060 8 GB.
- Portátil: Intel i7-7700HQ, 32 GB RAM, NVIDIA GTX 1050 4 GB según configuración del repositorio. Son características configuradas, no telemetría nueva. La asignación real de memoria procede de /api/ps.
- Esta variante no tiene muestras históricas de la comparación de seis preguntas.

## Nueva comparación A–F

**8/8 PASSED; 0/8 FAILED.** Casos nuevos 01, 02, 03 y 10, primero ES y después EN. Textos y asserts de los IT originales, solo reordenados. Los resultados previos de este archivo son históricos y no equivalentes.

Top-K 5, umbral 0,63, expansión acotada de encabezados sin cuerpo, traducción local con límite configurable de 30 s; corpus inalterado. Una única petición SSE por caso; sin fallback silencioso ni OpenAI.

| Caso | Resultado | Embedding ms | Recuperación incl. embedding ms | Vector ms | Generación ms | Total backend ms | E2E SSE ms | Documentos | Modelo real |
| --- | --- | ---: | ---: | --- | ---: | ---: | ---: | ---: | --- |
| ES 01 | PASSED | 102.14 | 391.94 | null | 1512.15 | 1906.20 | 1916.61 | 5 | qwen3:8b · desktop |
| ES 02 | PASSED | 92.13 | 122.91 | null | 2756.58 | 2880.93 | 2883.40 | 5 | qwen3:8b · desktop |
| ES 03 | PASSED | 217.04 | 248.71 | null | 1546.36 | 1796.17 | 1798.57 | 3 | qwen3:8b · desktop |
| ES 10 | PASSED | 162.66 | 196.17 | null | 2610.78 | 2808.60 | 2810.36 | 5 | qwen3:8b · desktop |
| EN 01 | PASSED | 239.91 | 278.50 | null | 2480.74 | 3714.16 | 3716.56 | 5 | qwen3:8b · desktop |
| EN 02 | PASSED | 214.77 | 253.30 | null | 2146.61 | 3356.60 | 3358.18 | 5 | qwen3:8b · desktop |
| EN 03 | PASSED | 211.46 | 272.07 | null | 1180.46 | 2174.45 | 2176.37 | 4 | qwen3:8b · desktop |
| EN 10 | PASSED | 190.46 | 241.77 | null | 1928.86 | 3145.22 | 3147.10 | 5 | qwen3:8b · desktop |

### Agregados separados

| Muestras | n | Media embedding ms (n) | Media generación ms (n) | Media total backend ms (n) | Mediana backend ms | Máximo backend ms | Media E2E SSE ms (n) | Mediana E2E ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Exitosas | 8 | 178.82 (8) | 2020.32 (8) | 2722.79 (8) | 2844.77 | 3714.16 | 2725.89 (8) | 2846.88 |
| Fallos/timeouts | 0 | null (0) | null (0) | null (0) | null | null | null (0) | null |

- ES: 4/4 correctas; media total de éxitos 2347.98 ms.

- EN: 4/4 correctas; media total de éxitos 3097.61 ms.

### Resultados y memoria por consulta

#### ES 01 — Primera orientación

- Pregunta: Si quiero una orientación inicial sobre mi reforma, ¿qué detalles debería facilitar?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: Para una orientación inicial sobre tu reforma, deberías facilitar ubicación, fotografías, medidas aproximadas, uso del espacio, problema, resultado esperado y plazo. [S1]
- Traducción local ms: no ejecutada
- Cita [S1]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-001 — ¿Qué información hace falta para una primera orientación?
- Eventos: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB

#### ES 02 — Solera frente a solado

- Pregunta: En una obra, ¿en qué se distinguen la base de solera y el acabado de solado?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: La base de solera se refiere a la estructura de hormigón o mortero preparada para recibir el pavimento, mientras que el solado es la colocación del pavimento final sobre una base apta. Esta distinción está clara en [S2], donde se menciona que la solera es la base estructural y el solado es el pavimento final.
- Traducción local ms: no ejecutada
- Cita [S2]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Suelos / FAQ-041 — ¿Qué diferencia hay entre solera y solado?
- Eventos: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB

#### ES 03 — Precio por acceso

- Pregunta: ¿Por qué el acceso cambia el precio de una solera?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: El acceso cambia el precio de una solera porque la mezcla y transporte manual aumentan jornadas, coordinación y riesgo frente a descarga directa o bombeo. [S1]
- Traducción local ms: no ejecutada
- Cita [S1]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Patios / FAQ-045 — ¿Por qué el acceso cambia el precio de una solera?
- Eventos: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB

#### ES 10 — Plazos

- Pregunta: ¿Por qué es importante conocer el plazo previsto antes de presupuestar una reforma?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: Conocer el plazo previsto es importante porque influye en la planificación del presupuesto, incluyendo costos de mano de obra y posibles ajustes por plazos [S2]. Además, el plazo afecta la viabilidad de la reforma y la posibilidad de ajustar el alcance si los costos son elevados [S3].
- Traducción local ms: no ejecutada
- Cita [S2]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-003 — ¿Qué debe contener un presupuesto?
- Cita [S3]: `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` · Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 18. Comunicación con clientes / 18.3. Cliente que aprieta precio
- Eventos: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB

#### EN 01 — Company services ES/EN regression

- Pregunta: What kinds of renovation work does this company do? Does it work on houses and offices?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: The company performs renovations on houses, offices, and other structures. This is supported by [S2], which lists renovations for "viviendas," "pequeñas oficinas y locales," and "redistribuciones prudentes." Additionally, [S1] and [S4] mention corporate office renovations, confirming the company works on offices.
- Traducción local ms: 954.42
- Cita [S1]: `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` · Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 11. Caso F — Reforma de oficinas y aseos corporativos
- Cita [S2]: `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md` · Riansares 4R — identidad, criterios y voz editorial / 2. Identidad de Riansares 4R / 2.3. Servicios nucleares / Reformas
- Cita [S4]: `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` · Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 11. Caso F — Reforma de oficinas y aseos corporativos / 11.1. Contexto
- Eventos: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB

#### EN 02 — Concrete slab versus flooring

- Pregunta: In construction, how does the concrete base differ from the finished flooring?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: In construction, the concrete base, or solera, is a structured foundation made of concrete or mortar, prepared with specific thickness, reinforcement, slopes, and joints. The finished flooring, or solado, refers to the final placement of the pavement over a suitable base. This distinction is outlined in [S1].
- Traducción local ms: 956.11
- Cita [S1]: `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` · Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.3. Distinción entre solera y solado
- Eventos: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB

#### EN 03 — Access and cost

- Pregunta: Why does access affect the price of a concrete slab?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: Access affects the price of a concrete slab because manual mixing and transportation increase workdays, coordination, and risk compared to direct discharge or pumping. [S1]
- Traducción local ms: 721.25
- Cita [S1]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Patios / FAQ-045 — ¿Por qué el acceso cambia el precio de una solera?
- Eventos: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB

#### EN 10 — Timelines

- Pregunta: Why is it important to know the expected timeline before preparing a renovation estimate?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: Knowing the expected timeline is important before preparing a renovation estimate because it helps define the scope and ensures that all necessary work, including measurements, materials, and labor, can be properly accounted for within the given timeframe. This is essential for accurate budgeting and planning. [S3]
- Traducción local ms: 974.02
- Cita [S3]: `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` · Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 15. Presupuestación con criterio
- Eventos: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB

### Protección del corpus

```text
BENCHMARK_SETTINGS topK=5 minScore=0.63 translationTimeout=30s think=false
BENCHMARK_SAFETY readonly=true flyway=false sqlInit=never schemaInit=false catalog=false openai=false startupWarmup=false
BENCHMARK_CORPUS {"readonly":"on","snapshot":{"rows":282,"fingerprint":"58059dbac293f4ce2db72d942552610e"},"vectorType":"vector(768)"}
BENCHMARK_CORPUS_END {"rows":282,"fingerprint":"58059dbac293f4ce2db72d942552610e"}
```

El total backend procede de las métricas SSE; E2E SSE es el cronómetro real del cliente desde POST hasta recibir ANSWER_READY/ERROR. Ambos excluyen las lecturas /api/ps del adaptador. No incluyen MyMemory del frontend porque se ejecutan los IT del backend. La recuperación incluye embeddings y expansión de secciones. `vectorSearchMs=null`: no se estima por resta. No se ha repetido REST para medir la misma pregunta. Los modelos conservan residencia natural; no se descargan ni desalojan. Las medias de fallos están separadas de las exitosas. No se midió consumo eléctrico ni utilización de GPU, por lo que no se atribuye causalidad energética.
