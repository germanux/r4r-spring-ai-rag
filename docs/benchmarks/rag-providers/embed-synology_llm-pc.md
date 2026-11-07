# A — Embeddings synology + generación pc

> Histórico: los casos antiguos 02/05/11 corresponden a los actuales 02/11/12. Las métricas de esa pasada se conservan; la nueva comparación usa 01/02/03/10 ES/EN y se documenta por separado.

**Estado: COMPLETED; 3/6 PASSED, 3/6 FAILED.** Una única ejecución por pregunta mediante SSE.

## Modelos y hardware

- Embeddings: nomic-embed-text:latest, F16, 768 dimensiones.
- Generación prevista: qwen3:8b.
- Equipo de embeddings: NAS Synology; CPU/modelo de hardware y RAM no expuestos por Ollama. Nomic observado con size_vram=0 (CPU).
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
| ES 02 | PASSED | 5932.96 | 6029.40 | null | 9285.65 | 15317.88 | 3 | PC · qwen3:8b |
| ES 05 | FAILED | 5155.71 | 5183.23 | null | 10073.28 | 15257.17 | 3 | PC · qwen3:8b |
| ES 11 | PASSED | 3837.51 | 3882.35 | null | 10382.46 | 14265.82 | 3 | PC · qwen3:8b |
| EN 02 | FAILED | null | null | null | null | 12024.27 | null | sin generación RAG final · null |
| EN 05 | FAILED | null | null | null | null | 12018.34 | null | sin generación RAG final · null |
| EN 11 | PASSED | 5938.78 | 5972.07 | null | 8740.34 | 25269.70 | 3 | PC · qwen3:8b |

## Agregados

- Media embeddingMs: **5216.24 ms**, 4/6 mediciones disponibles.
- Media generationMs: **9620.44 ms**, 4/6 mediciones disponibles.
- Media totalMs: **15692.20 ms**, 6/6 mediciones disponibles.
- Mediana total: **14761.50 ms**. Máximo: **25269.70 ms**.
- Correctas/fallidas: **3/3**. Las medias incluyen fallos con métricas reales, con denominadores explícitos; no mezclan muestras omitidas.

## Evidencias individuales, citas y memoria

### ES 02 — Solera frente a solado

- HTTP SSE backend: 200; validación: PASSED.
- Respuesta: La **solera** se distingue como la base estructural de hormigón o mortero con preparación, espesores, armadura y juntas, mientras que el **solado** es la colocación del pavimento final sobre una base apta. Esta diferenciación se respalda en [S1] y [S2], donde se explicita que la solera es la base estructural y el solado es el acabado final.
- Abstención: False. Coincidencia con proveedor seleccionado: True.
- Cita [S1]: `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` · Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.3. Distinción entre solera y solado
- Cita [S2]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Suelos / FAQ-041 — ¿Qué diferencia hay entre solera y solado?
- Fases observadas: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY

Memoria antes:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB

Memoria después:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB

### ES 05 — Presupuesto

- HTTP SSE backend: 200; validación: FAILED.
- Respuesta: (vacía/ninguna)
- Abstención: True. Coincidencia con proveedor seleccionado: True.
- Fallo del assert original: `AssertionFailedError: [La pregunta es del dominio R4R]  Expecting value to be false but was true`.
- Fases observadas: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY

Memoria antes:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB

Memoria después:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB

### ES 11 — Baño

- HTTP SSE backend: 200; validación: PASSED.
- Respuesta: Para orientar una reforma de baño, se necesitan datos como el área (ej.: 2–6 m²) y el nivel de calidad deseado, ya que los precios varían significativamente según estos factores, como se muestra en los ejemplos de 1.560 € a 4.860 € [S1]. Además, es relevante considerar si se incluyen acabados o instalaciones de alta calidad [S2].
- Abstención: False. Coincidencia con proveedor seleccionado: True.
- Cita [S1]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Baños / FAQ-031 — ¿Cuánto cuesta reformar un baño?
- Cita [S2]: `sources/R4R_70_WEB_SOURCE_MANIFEST.md` · Fuentes web consultadas / SRC-HAB-BANO — ¿Cuánto cuesta reformar un baño? Precios 2026
- Fases observadas: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY

Memoria antes:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB

Memoria después:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB

### EN 02 — Concrete slab versus flooring

- HTTP SSE backend: 200; validación: FAILED.
- Respuesta: (vacía/ninguna)
- Abstención: no disponible. Coincidencia con proveedor seleccionado: False.
- Fallo del assert original: `AssertionFailedError: [HTTP de Concrete slab versus flooring: {"error": "processing_failed"}]  expected: 200  but was: 503`.
- Fases observadas: REQUEST_RECEIVED → TRANSLATION_STARTED → ERROR
- Error SSE: `{"stage": "ERROR", "elapsedMs": 12024, "message": "processing_failed", "metrics": {"embeddingMs": null, "vectorSearchMs": null, "retrievalIncludingEmbeddingMs": null, "generationMs": null, "totalMs": 12024.270535}}`.

Memoria antes:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB

Memoria después:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB

### EN 05 — Estimate

- HTTP SSE backend: 200; validación: FAILED.
- Respuesta: (vacía/ninguna)
- Abstención: no disponible. Coincidencia con proveedor seleccionado: False.
- Fallo del assert original: `AssertionFailedError: [HTTP de Estimate: {"error": "processing_failed"}]  expected: 200  but was: 503`.
- Fases observadas: REQUEST_RECEIVED → TRANSLATION_STARTED → ERROR
- Error SSE: `{"stage": "ERROR", "elapsedMs": 12018, "message": "processing_failed", "metrics": {"embeddingMs": null, "vectorSearchMs": null, "retrievalIncludingEmbeddingMs": null, "generationMs": null, "totalMs": 12018.338837}}`.

Memoria antes:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB

Memoria después:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB

### EN 11 — Bathroom

- HTTP SSE backend: 200; validación: PASSED.
- Respuesta: To estimate a bathroom renovation, the size (in m²) and desired quality level (standard vs. high-quality) are critical factors, as prices range from 1,560 € for 2 m² to 4,860 € for 6 m² [S2]. These examples are based on projects ranging from 2–6 m² [S1].
- Abstención: False. Coincidencia con proveedor seleccionado: True.
- Cita [S1]: `sources/R4R_70_WEB_SOURCE_MANIFEST.md` · Fuentes web consultadas / SRC-HAB-BANO — ¿Cuánto cuesta reformar un baño? Precios 2026
- Cita [S2]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Baños / FAQ-031 — ¿Cuánto cuesta reformar un baño?
- Fases observadas: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY

Memoria antes:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB

Memoria después:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB

## Solo lectura y estabilidad del corpus

```text
BENCHMARK_SAFETY readonly=true flyway=false sqlInit=never schemaInit=false catalog=false openai=false startupWarmup=false
BENCHMARK_CORPUS {"readonly":"on","vectorType":"vector(768)","snapshot":{"rows":282,"fingerprint":"58059dbac293f4ce2db72d942552610e"}}
BENCHMARK_CORPUS_END {"rows":282,"fingerprint":"58059dbac293f4ce2db72d942552610e"}
```

## Observación inicial conservada


Esta es una observación inicial real de /api/ps, no el estado durante seis consultas
que no han podido ejecutarse. No hubo precargas, descargas ni desalojos forzados.

```json
{
  "synology": {
    "models": [
      {
        "name": "nomic-embed-text:latest",
        "model": "nomic-embed-text:latest",
        "size": 389053152,
        "digest": "0a109f422b47e3a30ba2b10eca18548e944e8a23073ee3f3e947efcf3c45e59f",
        "details": {
          "parent_model": "",
          "format": "gguf",
          "family": "nomic-bert",
          "families": [
            "nomic-bert"
          ],
          "parameter_size": "137M",
          "quantization_level": "F16"
        },
        "expires_at": "2319-02-17T11:20:52.283870958Z",
        "size_vram": 0,
        "context_length": 2048,
        "runner": "llamacpp"
      }
    ]
  },
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
| ES 01 | PASSED | 6518.39 | 6567.99 | null | 1951.55 | 8521.82 | 8530.73 | 5 | qwen3:8b · desktop |
| ES 02 | PASSED | 6518.39 | 6553.91 | null | 2205.26 | 8760.24 | 8762.88 | 5 | qwen3:8b · desktop |
| ES 03 | PASSED | 3877.07 | 3908.62 | null | 1899.12 | 5808.71 | 5810.92 | 3 | qwen3:8b · desktop |
| ES 10 | PASSED | 6292.08 | 6328.53 | null | 2961.36 | 9290.82 | 9292.74 | 5 | qwen3:8b · desktop |
| EN 01 | PASSED | 6039.25 | 6079.36 | null | 3034.57 | 10041.60 | 10044.01 | 5 | qwen3:8b · desktop |
| EN 02 | PASSED | 6059.85 | 6095.48 | null | 2776.98 | 9867.74 | 9869.33 | 5 | qwen3:8b · desktop |
| EN 03 | PASSED | 4199.67 | 4236.00 | null | 1666.80 | 6685.45 | 6687.14 | 4 | qwen3:8b · desktop |
| EN 10 | PASSED | 6796.78 | 6843.05 | null | 2506.34 | 10382.33 | 10383.95 | 5 | qwen3:8b · desktop |

### Agregados separados

| Muestras | n | Media embedding ms (n) | Media generación ms (n) | Media total backend ms (n) | Mediana backend ms | Máximo backend ms | Media E2E SSE ms (n) | Mediana E2E ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Exitosas | 8 | 5787.68 (8) | 2375.25 (8) | 8669.84 (8) | 9025.53 | 10382.33 | 8672.71 (8) | 9027.81 |
| Fallos/timeouts | 0 | null (0) | null (0) | null (0) | null | null | null (0) | null |

- ES: 4/4 correctas; media total de éxitos 8095.40 ms.

- EN: 4/4 correctas; media total de éxitos 9244.28 ms.

### Resultados y memoria por consulta

#### ES 01 — Primera orientación

- Pregunta: Si quiero una orientación inicial sobre mi reforma, ¿qué detalles debería facilitar?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: Para una orientación inicial sobre tu reforma, debes facilitar ubicación, fotografías, medidas aproximadas, uso del espacio, problema, resultado esperado y plazo. [S1]
- Traducción local ms: no ejecutada
- Cita [S1]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-001 — ¿Qué información hace falta para una primera orientación?
- Eventos: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB

#### ES 02 — Solera frente a solado

- Pregunta: En una obra, ¿en qué se distinguen la base de solera y el acabado de solado?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: La base de solera se distingue del acabado de solado por ser la estructura de hormigón o mortero preparada para recibir el pavimento, mientras que el solado es la colocación del pavimento final sobre una base apta. [S1]
- Traducción local ms: no ejecutada
- Cita [S1]: `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` · Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.3. Distinción entre solera y solado
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
- Respuesta: Conocer el plazo previsto es importante porque influye en la planificación del presupuesto, incluyendo los plazos mencionados en el presupuesto que deben contenerse según [S2]. Además, el plazo puede afectar la elección de materiales, mano de obra y otros factores que impactan en el costo total de la reforma.
- Traducción local ms: no ejecutada
- Cita [S2]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-003 — ¿Qué debe contener un presupuesto?
- Eventos: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB

#### EN 01 — Company services ES/EN regression

- Pregunta: What kinds of renovation work does this company do? Does it work on houses and offices?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: The company performs renovations on houses, offices, and other buildings. This is supported by [S2], which lists renovations for "viviendas," "pequeñas oficinas y locales," and "reformas." The company also works on corporate projects, including offices, as indicated in [S1] and [S4].
- Traducción local ms: 927.22
- Cita [S1]: `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` · Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 11. Caso F — Reforma de oficinas y aseos corporativos
- Cita [S2]: `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md` · Riansares 4R — identidad, criterios y voz editorial / 2. Identidad de Riansares 4R / 2.3. Servicios nucleares / Reformas
- Cita [S4]: `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` · Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 11. Caso F — Reforma de oficinas y aseos corporativos / 11.1. Contexto
- Eventos: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB

#### EN 02 — Concrete slab versus flooring

- Pregunta: In construction, how does the concrete base differ from the finished flooring?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: In construction, the concrete base, or solera, is a structured foundation made of concrete or mortar, prepared with specific thickness, reinforcement, slopes, and joints. The finished flooring, or solado, refers to the final placement of the floor covering over a suitable base. This distinction is supported by [S1].
- Traducción local ms: 994.53
- Cita [S1]: `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` · Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.3. Distinción entre solera y solado
- Eventos: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB

#### EN 03 — Access and cost

- Pregunta: Why does access affect the price of a concrete slab?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: Access affects the price of a concrete slab because manual mixing and transportation increase days, coordination, and risk compared to direct discharge or pumping. [S1]
- Traducción local ms: 782.07
- Cita [S1]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Patios / FAQ-045 — ¿Por qué el acceso cambia el precio de una solera?
- Eventos: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB

#### EN 10 — Timelines

- Pregunta: Why is it important to know the expected timeline before preparing a renovation estimate?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: Knowing the expected timeline is important before preparing a renovation estimate because it helps define the scope and ensures that all necessary work, including measurements, materials, and labor, can be properly accounted for within the specified timeframe. This is essential for accurate budgeting and planning. [S3]
- Traducción local ms: 1032.30
- Cita [S3]: `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` · Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 15. Presupuestación con criterio
- Eventos: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB

### Protección del corpus

```text
BENCHMARK_SETTINGS topK=5 minScore=0.63 translationTimeout=30s think=false
BENCHMARK_SAFETY readonly=true flyway=false sqlInit=never schemaInit=false catalog=false openai=false startupWarmup=false
BENCHMARK_CORPUS {"readonly":"on","vectorType":"vector(768)","snapshot":{"rows":282,"fingerprint":"58059dbac293f4ce2db72d942552610e"}}
BENCHMARK_CORPUS_END {"rows":282,"fingerprint":"58059dbac293f4ce2db72d942552610e"}
```

El total backend procede de las métricas SSE; E2E SSE es el cronómetro real del cliente desde POST hasta recibir ANSWER_READY/ERROR. Ambos excluyen las lecturas /api/ps del adaptador. No incluyen MyMemory del frontend porque se ejecutan los IT del backend. La recuperación incluye embeddings y expansión de secciones. `vectorSearchMs=null`: no se estima por resta. No se ha repetido REST para medir la misma pregunta. Los modelos conservan residencia natural; no se descargan ni desalojan. Las medias de fallos están separadas de las exitosas. No se midió consumo eléctrico ni utilización de GPU, por lo que no se atribuye causalidad energética.
