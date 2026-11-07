# D — Embeddings laptop + generación laptop

> Histórico: los casos antiguos 02/05/11 corresponden a los actuales 02/11/12. Las métricas de esa pasada se conservan; la nueva comparación usa 01/02/03/10 ES/EN y se documenta por separado.

**Estado: COMPLETED; 4/6 PASSED, 2/6 FAILED.** Una única ejecución por pregunta mediante SSE.

## Modelos y hardware

- Embeddings: nomic-embed-text:latest, F16, 768 dimensiones.
- Generación prevista: qwen2.5:7b.
- Equipo de embeddings: Portátil ASUS; Intel i7-7700HQ, 32 GB RAM y NVIDIA GeForce GTX 1050 / 4 GB según InferenceHost.java (configuración, no medición nueva del hardware).
- Equipo de generación: Portátil ASUS; Intel i7-7700HQ, 32 GB RAM y NVIDIA GeForce GTX 1050 / 4 GB según InferenceHost.java (configuración, no medición nueva del hardware).
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
| ES 02 | PASSED | 2196.48 | 2246.16 | null | 9094.34 | 11342.86 | 3 | portátil · qwen2.5:7b |
| ES 05 | FAILED | 95.42 | 124.07 | null | 1349.63 | 1474.33 | 3 | portátil · qwen2.5:7b |
| ES 11 | PASSED | 89.90 | 122.11 | null | 6013.95 | 6137.02 | 3 | portátil · qwen2.5:7b |
| EN 02 | PASSED | 94.28 | 127.98 | null | 10432.69 | 16179.04 | 3 | portátil · qwen2.5:7b |
| EN 05 | FAILED | 116.40 | 148.97 | null | 1294.22 | 4252.69 | 3 | portátil · qwen2.5:7b |
| EN 11 | PASSED | 77.97 | 109.03 | null | 9345.43 | 11975.54 | 3 | portátil · qwen2.5:7b |

## Agregados

- Media embeddingMs: **445.07 ms**, 6/6 mediciones disponibles.
- Media generationMs: **6255.04 ms**, 6/6 mediciones disponibles.
- Media totalMs: **8560.25 ms**, 6/6 mediciones disponibles.
- Mediana total: **8739.94 ms**. Máximo: **16179.04 ms**.
- Correctas/fallidas: **4/2**. Las medias incluyen fallos con métricas reales, con denominadores explícitos; no mezclan muestras omitidas.

## Evidencias individuales, citas y memoria

### ES 02 — Solera frente a solado

- HTTP SSE backend: 200; validación: PASSED.
- Respuesta: La base de solera es la estructura que recibe el pavimento final, incluyendo preparación, espesores, armadura y juntas, mientras que el solado se refiere al pavimento final colocado sobre dicha base. [S1]
- Abstención: False. Coincidencia con proveedor seleccionado: True.
- Cita [S1]: `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` · Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.3. Distinción entre solera y solado
- Fases observadas: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY

Memoria antes:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB

Memoria después:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB

### ES 05 — Presupuesto

- HTTP SSE backend: 200; validación: FAILED.
- Respuesta: (vacía/ninguna)
- Abstención: True. Coincidencia con proveedor seleccionado: True.
- Fallo del assert original: `AssertionFailedError: [La pregunta es del dominio R4R]  Expecting value to be false but was true`.
- Fases observadas: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY

Memoria antes:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB

Memoria después:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB

### ES 11 — Baño

- HTTP SSE backend: 200; validación: PASSED.
- Respuesta: Para orientar una reforma de baño se necesitan conocer el área del baño (que varía entre 2 y 6 m² en los ejemplos consultados), así como el nivel de calidad deseado. [S1]
- Abstención: False. Coincidencia con proveedor seleccionado: True.
- Cita [S1]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Baños / FAQ-031 — ¿Cuánto cuesta reformar un baño?
- Fases observadas: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY

Memoria antes:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB

Memoria después:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB

### EN 02 — Concrete slab versus flooring

- HTTP SSE backend: 200; validación: PASSED.
- Respuesta: The concrete base, or solera, is a structured layer prepared for receiving the final pavement, including elements such as preparation, thickness, reinforcement, slopes, and joints. In contrast, the finished flooring, or solado, refers to placing the final pavement on a suitable base. [S1]
- Abstención: False. Coincidencia con proveedor seleccionado: True.
- Cita [S1]: `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` · Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.3. Distinción entre solera y solado
- Fases observadas: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY

Memoria antes:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB

Memoria después:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB

### EN 05 — Estimate

- HTTP SSE backend: 200; validación: FAILED.
- Respuesta: (vacía/ninguna)
- Abstención: True. Coincidencia con proveedor seleccionado: True.
- Fallo del assert original: `AssertionFailedError: [La pregunta es del dominio R4R]  Expecting value to be false but was true`.
- Fases observadas: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY

Memoria antes:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB

Memoria después:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB

### EN 11 — Bathroom

- HTTP SSE backend: 200; validación: PASSED.
- Respuesta: To estimate a bathroom renovation, you need to consider the size of the bathroom (ranging from 2 to 6 m²), as well as the level of quality desired. [S2] cites that costs can range from 1,560 € for 2 m² to 4,860 € for 6 m² for high-quality renovations.
- Abstención: False. Coincidencia con proveedor seleccionado: True.
- Cita [S2]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Baños / FAQ-031 — ¿Cuánto cuesta reformar un baño?
- Fases observadas: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY

Memoria antes:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB

Memoria después:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB

## Solo lectura y estabilidad del corpus

```text
BENCHMARK_SAFETY readonly=true flyway=false sqlInit=never schemaInit=false catalog=false openai=false startupWarmup=false
BENCHMARK_CORPUS {"vectorType":"vector(768)","snapshot":{"rows":282,"fingerprint":"58059dbac293f4ce2db72d942552610e"},"readonly":"on"}
BENCHMARK_CORPUS_END {"rows":282,"fingerprint":"58059dbac293f4ce2db72d942552610e"}
```

## Observación inicial conservada


Esta es una observación inicial real de /api/ps, no el estado durante seis consultas
que no han podido ejecutarse. No hubo precargas, descargas ni desalojos forzados.

```json
{
  "laptop": {
    "models": [
      {
        "name": "qwen2.5:7b",
        "model": "qwen2.5:7b",
        "size": 5015671772,
        "digest": "845dbda0ea48ed749caafd9e6037047aa19acfcfd82e704d7ca97d631a0b697e",
        "details": {
          "parent_model": "",
          "format": "gguf",
          "family": "qwen2",
          "families": [
            "qwen2"
          ],
          "parameter_size": "7.6B",
          "quantization_level": "Q4_K_M"
        },
        "expires_at": "2026-11-07T14:13:37.570911217+01:00",
        "size_vram": 3102180638,
        "context_length": 4096
      }
    ]
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
| ES 01 | PASSED | 1158.85 | 1224.79 | null | 10611.09 | 11838.26 | 11850.45 | 5 | qwen2.5:7b · laptop |
| ES 02 | PASSED | 133.96 | 167.04 | null | 5505.98 | 5673.77 | 5675.72 | 5 | qwen2.5:7b · laptop |
| ES 03 | PASSED | 137.43 | 180.31 | null | 5524.19 | 5705.19 | 5707.38 | 3 | qwen2.5:7b · laptop |
| ES 10 | PASSED | 110.84 | 151.85 | null | 4347.44 | 4500.02 | 4502.07 | 5 | qwen2.5:7b · laptop |
| EN 01 | PASSED | 117.34 | 154.74 | null | 8446.31 | 12432.81 | 12434.66 | 5 | qwen2.5:7b · laptop |
| EN 02 | PASSED | 210.69 | 240.73 | null | 12968.99 | 16947.75 | 16949.30 | 5 | qwen2.5:7b · laptop |
| EN 03 | PASSED | 163.60 | 195.42 | null | 3801.24 | 6698.32 | 6699.86 | 2 | qwen2.5:7b · laptop |
| EN 10 | PASSED | 117.49 | 162.86 | null | 13401.52 | 17233.41 | 17235.27 | 5 | qwen2.5:7b · laptop |

### Agregados separados

| Muestras | n | Media embedding ms (n) | Media generación ms (n) | Media total backend ms (n) | Mediana backend ms | Máximo backend ms | Media E2E SSE ms (n) | Mediana E2E ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Exitosas | 8 | 268.78 (8) | 8075.85 (8) | 10128.69 (8) | 9268.29 | 17233.41 | 10131.84 (8) | 9275.15 |
| Fallos/timeouts | 0 | null (0) | null (0) | null (0) | null | null | null (0) | null |

- ES: 4/4 correctas; media total de éxitos 6929.31 ms.

- EN: 4/4 correctas; media total de éxitos 13328.07 ms.

### Resultados y memoria por consulta

#### ES 01 — Primera orientación

- Pregunta: Si quiero una orientación inicial sobre mi reforma, ¿qué detalles debería facilitar?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: Para recibir una orientación inicial sobre tu reforma, debes facilitar la ubicación, fotografías, medidas aproximadas, el uso del espacio, el problema que quieres solucionar, y cuál es el resultado esperado. Además, se recomienda tener un plazo aproximado en mente [S1].
- Traducción local ms: no ejecutada
- Cita [S1]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-001 — ¿Qué información hace falta para una primera orientación?
- Eventos: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB

#### ES 02 — Solera frente a solado

- Pregunta: En una obra, ¿en qué se distinguen la base de solera y el acabado de solado?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: La solera es la base estructural o de regularización construida con hormigón o mortero, mientras que el solado es el pavimento final colocado sobre dicha base. [S2]
- Traducción local ms: no ejecutada
- Cita [S2]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Suelos / FAQ-041 — ¿Qué diferencia hay entre solera y solado?
- Eventos: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB

#### ES 03 — Precio por acceso

- Pregunta: ¿Por qué el acceso cambia el precio de una solera?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: El acceso cambia el precio de una solera porque la mezcla y transporte manual aumentan jornadas, coordinación y riesgo frente a descarga directa o bombeo. [S1]
- Traducción local ms: no ejecutada
- Cita [S1]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Patios / FAQ-045 — ¿Por qué el acceso cambia el precio de una solera?
- Eventos: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB

#### ES 10 — Plazos

- Pregunta: ¿Por qué es importante conocer el plazo previsto antes de presupuestar una reforma?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: El conocer el plazo previsto antes de presupuestar una reforma es importante porque influye en la planificación y ejecución del proyecto. [S2]
- Traducción local ms: no ejecutada
- Cita [S2]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-003 — ¿Qué debe contener un presupuesto?
- Eventos: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB

#### EN 01 — Company services ES/EN regression

- Pregunta: What kinds of renovation work does this company do? Does it work on houses and offices?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: The company conducts renovations on houses and offices. This is supported by [S2], which lists reforms for homes, small offices, and corporate projects divided into multiple packages.
- Traducción local ms: 3831.25
- Cita [S2]: `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md` · Riansares 4R — identidad, criterios y voz editorial / 2. Identidad de Riansares 4R / 2.3. Servicios nucleares / Reformas
- Eventos: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB

#### EN 02 — Concrete slab versus flooring

- Pregunta: In construction, how does the concrete base differ from the finished flooring?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: The concrete base, or solera, is the structural foundation designed to receive the final pavement and may include preparations, thicknesses, reinforcement, slopes, and joints. In contrast, the finished flooring, or solado, is placed on a suitable base. [S1]
- Traducción local ms: 3737.59
- Cita [S1]: `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` · Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.3. Distinción entre solera y solado
- Eventos: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB

#### EN 03 — Access and cost

- Pregunta: Why does access affect the price of a concrete slab?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: Access affects the price of a concrete slab because it increases labor hours, coordination, and risk compared to direct pouring or pumping. [S1]
- Traducción local ms: 2701.25
- Cita [S1]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Patios / FAQ-045 — ¿Por qué el acceso cambia el precio de una solera?
- Eventos: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB

#### EN 10 — Timelines

- Pregunta: Why is it important to know the expected timeline before preparing a renovation estimate?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: Knowing the expected timeline is important because it helps in planning the logistics, manpower scheduling, and completion dates, which are crucial for both the client's expectations and the project management. [S5]
- Traducción local ms: 3668.67
- Cita [S5]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Presupuesto
- Eventos: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB

### Protección del corpus

```text
BENCHMARK_SETTINGS topK=5 minScore=0.63 translationTimeout=30s think=false
BENCHMARK_SAFETY readonly=true flyway=false sqlInit=never schemaInit=false catalog=false openai=false startupWarmup=false
BENCHMARK_CORPUS {"readonly":"on","snapshot":{"rows":282,"fingerprint":"58059dbac293f4ce2db72d942552610e"},"vectorType":"vector(768)"}
BENCHMARK_CORPUS_END {"rows":282,"fingerprint":"58059dbac293f4ce2db72d942552610e"}
```

El total backend procede de las métricas SSE; E2E SSE es el cronómetro real del cliente desde POST hasta recibir ANSWER_READY/ERROR. Ambos excluyen las lecturas /api/ps del adaptador. No incluyen MyMemory del frontend porque se ejecutan los IT del backend. La recuperación incluye embeddings y expansión de secciones. `vectorSearchMs=null`: no se estima por resta. No se ha repetido REST para medir la misma pregunta. Los modelos conservan residencia natural; no se descargan ni desalojan. Las medias de fallos están separadas de las exitosas. No se midió consumo eléctrico ni utilización de GPU, por lo que no se atribuye causalidad energética.
