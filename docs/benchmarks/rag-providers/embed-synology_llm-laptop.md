# C — Embeddings synology + generación laptop

> Histórico: los casos antiguos 02/05/11 corresponden a los actuales 02/11/12. Las métricas de esa pasada se conservan; la nueva comparación usa 01/02/03/10 ES/EN y se documenta por separado.

**Estado: COMPLETED; 4/6 PASSED, 2/6 FAILED.** Una única ejecución por pregunta mediante SSE.

## Modelos y hardware

- Embeddings: nomic-embed-text:latest, F16, 768 dimensiones.
- Generación prevista: qwen2.5:7b.
- Equipo de embeddings: NAS Synology; CPU/modelo de hardware y RAM no expuestos por Ollama. Nomic observado con size_vram=0 (CPU).
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
| ES 02 | PASSED | 5899.07 | 5962.04 | null | 11808.80 | 17773.11 | 3 | portátil · qwen2.5:7b |
| ES 05 | FAILED | 4876.29 | 4955.46 | null | 3487.22 | 8443.26 | 3 | portátil · qwen2.5:7b |
| ES 11 | PASSED | 3918.74 | 3947.07 | null | 7473.50 | 11421.43 | 3 | portátil · qwen2.5:7b |
| EN 02 | PASSED | 5510.43 | 5656.56 | null | 9908.20 | 19329.87 | 3 | portátil · qwen2.5:7b |
| EN 05 | FAILED | 5109.31 | 5142.28 | null | 3560.69 | 11819.25 | 3 | portátil · qwen2.5:7b |
| EN 11 | PASSED | 4762.30 | 4793.90 | null | 10283.46 | 17916.43 | 3 | portátil · qwen2.5:7b |

## Agregados

- Media embeddingMs: **5012.69 ms**, 6/6 mediciones disponibles.
- Media generationMs: **7753.65 ms**, 6/6 mediciones disponibles.
- Media totalMs: **14450.56 ms**, 6/6 mediciones disponibles.
- Mediana total: **14796.18 ms**. Máximo: **19329.87 ms**.
- Correctas/fallidas: **4/2**. Las medias incluyen fallos con métricas reales, con denominadores explícitos; no mezclan muestras omitidas.

## Evidencias individuales, citas y memoria

### ES 02 — Solera frente a solado

- HTTP SSE backend: 200; validación: PASSED.
- Respuesta: La solera es la base estructural o de regularización hecha con hormigón o mortero, mientras que el solado se refiere al pavimento final colocado sobre esa base. [S2]
- Abstención: False. Coincidencia con proveedor seleccionado: True.
- Cita [S2]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Suelos / FAQ-041 — ¿Qué diferencia hay entre solera y solado?
- Fases observadas: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY

Memoria antes:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.29 GiB
- laptop: sin modelos residentes

Memoria después:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.29 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB

### ES 05 — Presupuesto

- HTTP SSE backend: 200; validación: FAILED.
- Respuesta: (vacía/ninguna)
- Abstención: True. Coincidencia con proveedor seleccionado: True.
- Fallo del assert original: `AssertionFailedError: [La pregunta es del dominio R4R]  Expecting value to be false but was true`.
- Fases observadas: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY

Memoria antes:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.29 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB

Memoria después:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.29 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB

### ES 11 — Baño

- HTTP SSE backend: 200; validación: PASSED.
- Respuesta: Para orientar una reforma de baño se necesitan conocer el tamaño del baño (entre 2 y 6 m² según la evidencia), así como el nivel de calidad deseado. [S1]
- Abstención: False. Coincidencia con proveedor seleccionado: True.
- Cita [S1]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Baños / FAQ-031 — ¿Cuánto cuesta reformar un baño?
- Fases observadas: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY

Memoria antes:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.29 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB

Memoria después:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.29 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB

### EN 02 — Concrete slab versus flooring

- HTTP SSE backend: 200; validación: PASSED.
- Respuesta: The concrete base (solera) is a structured foundation made of cement or mortar intended to support the flooring. It includes preparation, thickness, reinforcement, slopes, and joints, whereas the finished flooring (solado) refers to the placement of the final pavement on an appropriate base. [S1]
- Abstención: False. Coincidencia con proveedor seleccionado: True.
- Cita [S1]: `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` · Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.3. Distinción entre solera y solado
- Fases observadas: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY

Memoria antes:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.29 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB

Memoria después:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.29 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB

### EN 05 — Estimate

- HTTP SSE backend: 200; validación: FAILED.
- Respuesta: (vacía/ninguna)
- Abstención: True. Coincidencia con proveedor seleccionado: True.
- Fallo del assert original: `AssertionFailedError: [La pregunta es del dominio R4R]  Expecting value to be false but was true`.
- Fases observadas: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY

Memoria antes:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.29 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB

Memoria después:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.29 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB

### EN 11 — Bathroom

- HTTP SSE backend: 200; validación: PASSED.
- Respuesta: To estimate a bathroom renovation, you need to consider the size of the bathroom and its quality level. [S2] cites that costs range from 1,560 € for 2 m² up to 4,860 € for 6 m² of high-quality work.
- Abstención: False. Coincidencia con proveedor seleccionado: True.
- Cita [S2]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Baños / FAQ-031 — ¿Cuánto cuesta reformar un baño?
- Fases observadas: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY

Memoria antes:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.29 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB

Memoria después:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.29 GiB
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

**6/8 PASSED; 2/8 FAILED.** Casos nuevos 01, 02, 03 y 10, primero ES y después EN. Textos y asserts de los IT originales, solo reordenados. Los resultados previos de este archivo son históricos y no equivalentes.

Top-K 5, umbral 0,63, expansión acotada de encabezados sin cuerpo, traducción local con límite configurable de 30 s; corpus inalterado. Una única petición SSE por caso; sin fallback silencioso ni OpenAI.

| Caso | Resultado | Embedding ms | Recuperación incl. embedding ms | Vector ms | Generación ms | Total backend ms | E2E SSE ms | Documentos | Modelo real |
| --- | --- | ---: | ---: | --- | ---: | ---: | ---: | ---: | --- |
| ES 01 | PASSED | 6285.17 | 6345.00 | null | 11352.64 | 17700.28 | 17713.35 | 5 | qwen2.5:7b · laptop |
| ES 02 | PASSED | 6000.78 | 6054.90 | null | 7766.24 | 13822.21 | 13824.41 | 5 | qwen2.5:7b · laptop |
| ES 03 | PASSED | 4125.73 | 4167.44 | null | 10547.02 | 14715.47 | 14717.80 | 3 | qwen2.5:7b · laptop |
| ES 10 | FAILED | 6288.62 | 6334.98 | null | 5733.25 | 12069.02 | 12071.72 | 5 | qwen2.5:7b · laptop |
| EN 01 | PASSED | 6756.48 | 6795.10 | null | 19549.68 | 30184.80 | 30186.96 | 5 | qwen2.5:7b · laptop |
| EN 02 | FAILED | null | null | null | null | 2002.89 | 2004.83 | None | null · sin generación |
| EN 03 | PASSED | 3995.82 | 4027.53 | null | 6316.44 | 12996.10 | 12997.70 | 2 | qwen2.5:7b · laptop |
| EN 10 | PASSED | 10299.71 | 10344.63 | null | 12736.63 | 26775.25 | 26777.08 | 5 | qwen2.5:7b · laptop |

### Agregados separados

| Muestras | n | Media embedding ms (n) | Media generación ms (n) | Media total backend ms (n) | Mediana backend ms | Máximo backend ms | Media E2E SSE ms (n) | Mediana E2E ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Exitosas | 6 | 6243.95 (6) | 11378.11 (6) | 19365.68 (6) | 16207.87 | 30184.80 | 19369.55 (6) | 16215.58 |
| Fallos/timeouts | 2 | 6288.62 (1) | 5733.25 (1) | 7035.96 (2) | 7035.96 | 12069.02 | 7038.28 (2) | 7038.28 |

- ES: 3/4 correctas; media total de éxitos 15412.65 ms.

- EN: 3/4 correctas; media total de éxitos 23318.72 ms.

### Resultados y memoria por consulta

#### ES 01 — Primera orientación

- Pregunta: Si quiero una orientación inicial sobre mi reforma, ¿qué detalles debería facilitar?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: Para obtener una orientación inicial sobre tu reforma, debes facilitar la ubicación, fotografías, medidas aproximadas, uso del espacio, el problema que deseas solucionar, y el resultado esperado junto con un plazo. [S1]
- Traducción local ms: no ejecutada
- Cita [S1]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-001 — ¿Qué información hace falta para una primera orientación?
- Eventos: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB

#### ES 02 — Solera frente a solado

- Pregunta: En una obra, ¿en qué se distinguen la base de solera y el acabado de solado?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: La solera es la base estructural o de regularización de hormigón o mortero, mientras que el solado se refiere a la colocación del pavimento final sobre dicha base. [S2]
- Traducción local ms: no ejecutada
- Cita [S2]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Suelos / FAQ-041 — ¿Qué diferencia hay entre solera y solado?
- Eventos: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB

#### ES 03 — Precio por acceso

- Pregunta: ¿Por qué el acceso cambia el precio de una solera?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: El acceso cambia el precio de una solera porque mezclar y transportar manualmente materiales aumenta las jornadas, la coordinación y el riesgo, distorsionando el costo real. [S1]
- Traducción local ms: no ejecutada
- Cita [S1]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Patios / FAQ-045 — ¿Por qué el acceso cambia el precio de una solera?
- Eventos: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB

#### ES 10 — Plazos

- Pregunta: ¿Por qué es importante conocer el plazo previsto antes de presupuestar una reforma?
- Resultado original: FAILED; abstención: True; proveedor real verificado: True.
- Respuesta: (vacía)
- Traducción local ms: no ejecutada
- Fallo: AssertionFailedError: [La pregunta es del dominio R4R]  Expecting value to be false but was true
- Eventos: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: <urlopen error timed out>
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB

#### EN 01 — Company services ES/EN regression

- Pregunta: What kinds of renovation work does this company do? Does it work on houses and offices?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: The company engages in various types of renovation work including house and office renovations. This is evidenced by [S1] which mentions "reforming a corporate project" and [S5] discussing strategies for renovating houses and offices.
- Traducción local ms: 3839.59
- Cita [S1]: `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md` · Riansares 4R — casos de oficinas, cuevas, baños y filtros comerciales / 11. Caso F — Reforma de oficinas y aseos corporativos
- Cita [S5]: `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` · Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.2. Principio: negociar con la estructura existente
- Eventos: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB

#### EN 02 — Concrete slab versus flooring

- Pregunta: In construction, how does the concrete base differ from the finished flooring?
- Resultado original: FAILED; abstención: null; proveedor real verificado: True.
- Respuesta: (vacía)
- Traducción local ms: null
- Fallo: AssertionFailedError: [HTTP de Concrete slab versus flooring: {"error": "processing_failed"}]  expected: 200  but was: 503
- Eventos: REQUEST_RECEIVED → TRANSLATION_STARTED → ERROR
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: <urlopen error timed out>
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: <urlopen error timed out>

#### EN 03 — Access and cost

- Pregunta: Why does access affect the price of a concrete slab?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: Access affects the price of a concrete slab because the mixing and manual transportation increase work hours, coordination, and risk compared to direct unloading or pumping. [S1]
- Traducción local ms: 2651.76
- Cita [S1]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Patios / FAQ-045 — ¿Por qué el acceso cambia el precio de una solera?
- Eventos: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB

#### EN 10 — Timelines

- Pregunta: Why is it important to know the expected timeline before preparing a renovation estimate?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: Knowing the expected timeline is important before preparing a renovation estimate as it affects various cost factors such as labor and materials [S2]. Additionally, the timeline influences when payments are made, impacting cash flow considerations [S5].
- Traducción local ms: 3693.62
- Cita [S2]: `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` · Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 15. Presupuestación con criterio
- Cita [S5]: `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` · Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 15. Presupuestación con criterio / 15.5. Presupuesto preliminar y definitivo
- Eventos: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB

### Diagnóstico de fallos observados

- ES 10: `RAG result=model_abstention retrieved=5 topK=5 minScore=0.63 normalizationMs=0.001648 retrievalIncludingEmbeddingMs=6334.983498 contextMs=0.048141 generationMs=5733.253402 validationMs=0.027324`.
- EN 02: `RAG result=error stage=normalization errorType=ResponseStatusException elapsedMs=2002.7534`.
Los marcadores model_abstention agrupan salida vacía, token explícito o abstención natural. Los errores de normalización ocurren antes de embeddings; ResponseStatusException indica rechazo de la salida de traducción, no se etiqueta como timeout sin evidencia. Esta variante se excluye de la selección si falla un caso legítimo.

### Protección del corpus

```text
BENCHMARK_SETTINGS topK=5 minScore=0.63 translationTimeout=30s think=false
BENCHMARK_SAFETY readonly=true flyway=false sqlInit=never schemaInit=false catalog=false openai=false startupWarmup=false
BENCHMARK_CORPUS {"vectorType":"vector(768)","snapshot":{"rows":282,"fingerprint":"58059dbac293f4ce2db72d942552610e"},"readonly":"on"}
BENCHMARK_CORPUS_END {"rows":282,"fingerprint":"58059dbac293f4ce2db72d942552610e"}
```

El total backend procede de las métricas SSE; E2E SSE es el cronómetro real del cliente desde POST hasta recibir ANSWER_READY/ERROR. Ambos excluyen las lecturas /api/ps del adaptador. No incluyen MyMemory del frontend porque se ejecutan los IT del backend. La recuperación incluye embeddings y expansión de secciones. `vectorSearchMs=null`: no se estima por resta. No se ha repetido REST para medir la misma pregunta. Los modelos conservan residencia natural; no se descargan ni desalojan. Las medias de fallos están separadas de las exitosas. No se midió consumo eléctrico ni utilización de GPU, por lo que no se atribuye causalidad energética.
