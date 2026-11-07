# E — Embeddings pc + generación laptop

> Histórico: los casos antiguos 02/05/11 corresponden a los actuales 02/11/12. Las métricas de esa pasada se conservan; la nueva comparación usa 01/02/03/10 ES/EN y se documenta por separado.

**Estado: COMPLETED; 4/6 PASSED, 2/6 FAILED.** Una única ejecución por pregunta mediante SSE.

## Modelos y hardware

- Embeddings: nomic-embed-text:latest, F16, 768 dimensiones.
- Generación prevista: qwen2.5:7b.
- Equipo de embeddings: PC de sobremesa; Intel Core i9-14900KF y MemTotal 65.653.192 KiB observados localmente. NVIDIA GeForce RTX 4060 / 8 GB según InferenceHost.java (configuración); nvidia-smi no pudo comunicarse con el driver en esta sesión.
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
| ES 02 | PASSED | 822.61 | 876.52 | null | 6510.82 | 7389.66 | 3 | portátil · qwen2.5:7b |
| ES 05 | FAILED | 36.94 | 62.67 | null | 1336.82 | 1400.16 | 3 | portátil · qwen2.5:7b |
| ES 11 | PASSED | 36.41 | 133.19 | null | 7520.19 | 7654.47 | 3 | portátil · qwen2.5:7b |
| EN 02 | PASSED | 120.34 | 202.69 | null | 10160.05 | 15534.33 | 3 | portátil · qwen2.5:7b |
| EN 05 | FAILED | 36.35 | 66.17 | null | 1291.81 | 4143.07 | 3 | portátil · qwen2.5:7b |
| EN 11 | PASSED | 32.96 | 58.45 | null | 9566.34 | 12139.51 | 3 | portátil · qwen2.5:7b |

## Agregados

- Media embeddingMs: **180.93 ms**, 6/6 mediciones disponibles.
- Media generationMs: **6064.34 ms**, 6/6 mediciones disponibles.
- Media totalMs: **8043.53 ms**, 6/6 mediciones disponibles.
- Mediana total: **7522.07 ms**. Máximo: **15534.33 ms**.
- Correctas/fallidas: **4/2**. Las medias incluyen fallos con métricas reales, con denominadores explícitos; no mezclan muestras omitidas.

## Evidencias individuales, citas y memoria

### ES 02 — Solera frente a solado

- HTTP SSE backend: 200; validación: PASSED.
- Respuesta: La solera es la base estructural o de regularización que recibe el pavimento, mientras que el solado se refiere al propio pavimento final. [S2]
- Abstención: False. Coincidencia con proveedor seleccionado: True.
- Cita [S2]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Suelos / FAQ-041 — ¿Qué diferencia hay entre solera y solado?
- Fases observadas: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY

Memoria antes:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB
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
- Respuesta: Para orientar una reforma de baño, es necesario conocer el tamaño del espacio a reformar, ya que los costos pueden variar desde 1.560 € para 2 m² hasta 4.860 € para 6 m² de alta calidad [S1].
- Abstención: False. Coincidencia con proveedor seleccionado: True.
- Cita [S1]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Baños / FAQ-031 — ¿Cuánto cuesta reformar un baño?
- Fases observadas: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY

Memoria antes:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB

Memoria después:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB

### EN 02 — Concrete slab versus flooring

- HTTP SSE backend: 200; validación: PASSED.
- Respuesta: The concrete base in construction serves as a foundational layer, providing support and structure. In contrast, finished flooring includes materials like tiles or vinyl that are placed on top of the prepared surface. [S3]
- Abstención: False. Coincidencia con proveedor seleccionado: True.
- Cita [S3]: `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` · Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 7. Caso B — Vivienda antigua: rehabilitar sin demoler de más / 7.8. Suelos
- Fases observadas: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY

Memoria antes:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB

Memoria después:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB

### EN 05 — Estimate

- HTTP SSE backend: 200; validación: FAILED.
- Respuesta: (vacía/ninguna)
- Abstención: True. Coincidencia con proveedor seleccionado: True.
- Fallo del assert original: `AssertionFailedError: [La pregunta es del dominio R4R]  Expecting value to be false but was true`.
- Fases observadas: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY

Memoria antes:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB

Memoria después:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB

### EN 11 — Bathroom

- HTTP SSE backend: 200; validación: PASSED.
- Respuesta: To estimate a bathroom renovation, you need to consider the size of the bathroom (between 2 and 6 m²), as well as the quality level desired. [S2] cites examples ranging from €1,560 for 2 m² to €4,860 for 6 m² of high-quality work, though actual costs may vary.
- Abstención: False. Coincidencia con proveedor seleccionado: True.
- Cita [S2]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Baños / FAQ-031 — ¿Cuánto cuesta reformar un baño?
- Fases observadas: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY

Memoria antes:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB

Memoria después:

- synology: nomic-embed-text:latest, RAM/total informado 0.36 GiB, VRAM 0.00 GiB
- pc: qwen3:8b, RAM/total informado 5.28 GiB, VRAM 4.31 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB
- laptop: qwen2.5:7b, RAM/total informado 4.67 GiB, VRAM 2.89 GiB; nomic-embed-text:latest, RAM/total informado 0.37 GiB, VRAM 0.10 GiB

## Solo lectura y estabilidad del corpus

```text
BENCHMARK_SAFETY readonly=true flyway=false sqlInit=never schemaInit=false catalog=false openai=false startupWarmup=false
BENCHMARK_CORPUS {"snapshot":{"rows":282,"fingerprint":"58059dbac293f4ce2db72d942552610e"},"vectorType":"vector(768)","readonly":"on"}
BENCHMARK_CORPUS_END {"rows":282,"fingerprint":"58059dbac293f4ce2db72d942552610e"}
```

## Observación inicial conservada


Esta es una observación inicial real de /api/ps, no el estado durante seis consultas
que no han podido ejecutarse. No hubo precargas, descargas ni desalojos forzados.

```json
{
  "pc": {
    "models": []
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

**8/8 PASSED; 0/8 FAILED.** Casos nuevos 01, 02, 03 y 10, primero ES y después EN. Textos y asserts de los IT originales, solo reordenados. Los resultados previos de este archivo son históricos y no equivalentes.

Top-K 5, umbral 0,63, expansión acotada de encabezados sin cuerpo, traducción local con límite configurable de 30 s; corpus inalterado. Una única petición SSE por caso; sin fallback silencioso ni OpenAI.

| Caso | Resultado | Embedding ms | Recuperación incl. embedding ms | Vector ms | Generación ms | Total backend ms | E2E SSE ms | Documentos | Modelo real |
| --- | --- | ---: | ---: | --- | ---: | ---: | ---: | ---: | --- |
| ES 01 | PASSED | 576.70 | 626.87 | null | 6998.88 | 7628.30 | 7641.18 | 5 | qwen2.5:7b · laptop |
| ES 02 | PASSED | 48.06 | 80.77 | null | 3709.31 | 3790.83 | 3793.69 | 5 | qwen2.5:7b · laptop |
| ES 03 | PASSED | 37.84 | 71.32 | null | 5621.54 | 5693.77 | 5696.32 | 3 | qwen2.5:7b · laptop |
| ES 10 | PASSED | 39.31 | 70.21 | null | 5189.64 | 5260.69 | 5262.52 | 5 | qwen2.5:7b · laptop |
| EN 01 | PASSED | 50.29 | 92.21 | null | 6906.14 | 10979.03 | 10981.09 | 5 | qwen2.5:7b · laptop |
| EN 02 | PASSED | 43.16 | 73.29 | null | 8849.78 | 12231.51 | 12233.47 | 5 | qwen2.5:7b · laptop |
| EN 03 | PASSED | 33.80 | 61.67 | null | 4180.05 | 7080.35 | 7082.19 | 2 | qwen2.5:7b · laptop |
| EN 10 | PASSED | 135.07 | 179.77 | null | 13190.30 | 17181.12 | 17183.64 | 5 | qwen2.5:7b · laptop |

### Agregados separados

| Muestras | n | Media embedding ms (n) | Media generación ms (n) | Media total backend ms (n) | Mediana backend ms | Máximo backend ms | Media E2E SSE ms (n) | Mediana E2E ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Exitosas | 8 | 120.53 (8) | 6830.71 (8) | 8730.70 (8) | 7354.33 | 17181.12 | 8734.26 (8) | 7361.68 |
| Fallos/timeouts | 0 | null (0) | null (0) | null (0) | null | null | null (0) | null |

- ES: 4/4 correctas; media total de éxitos 5593.40 ms.

- EN: 4/4 correctas; media total de éxitos 11868.01 ms.

### Resultados y memoria por consulta

#### ES 01 — Primera orientación

- Pregunta: Si quiero una orientación inicial sobre mi reforma, ¿qué detalles debería facilitar?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: Para obtener una orientación inicial sobre tu reforma, debes facilitar la ubicación, fotografías, medidas aproximadas, uso del espacio, el problema que quieres resolver, y el plazo para finalizar la reforma. [S1]
- Traducción local ms: no ejecutada
- Cita [S1]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-001 — ¿Qué información hace falta para una primera orientación?
- Eventos: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB

#### ES 02 — Solera frente a solado

- Pregunta: En una obra, ¿en qué se distinguen la base de solera y el acabado de solado?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: La solera es la base estructural o de regularización, mientras que el solado es el pavimento final. [S2]
- Traducción local ms: no ejecutada
- Cita [S2]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Suelos / FAQ-041 — ¿Qué diferencia hay entre solera y solado?
- Eventos: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB

#### ES 03 — Precio por acceso

- Pregunta: ¿Por qué el acceso cambia el precio de una solera?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: El acceso cambia el precio de una solera porque el mezcla y transporte manual aumentan jornadas, coordinación y riesgo frente a descarga directa o bombeo. [S1]
- Traducción local ms: no ejecutada
- Cita [S1]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Patios / FAQ-045 — ¿Por qué el acceso cambia el precio de una solera?
- Eventos: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB

#### ES 10 — Plazos

- Pregunta: ¿Por qué es importante conocer el plazo previsto antes de presupuestar una reforma?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: El conocer el plazo previsto antes de presupuestar una reforma es importante porque afecta al alcance del trabajo a realizar y a los plazos de pago. [S2]
- Traducción local ms: no ejecutada
- Cita [S2]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Presupuesto / FAQ-003 — ¿Qué debe contener un presupuesto?
- Eventos: REQUEST_RECEIVED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB

#### EN 01 — Company services ES/EN regression

- Pregunta: What kinds of renovation work does this company do? Does it work on houses and offices?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: The company reforms houses and offices. They handle renovations including bathrooms, kitchens, small offices, and living spaces, as well as interior distribution and painting works. [S2]
- Traducción local ms: 3980.23
- Cita [S2]: `R4R_00_IDENTIDAD_CRITERIOS_Y_VOZ_EDITORIAL.md` · Riansares 4R — identidad, criterios y voz editorial / 2. Identidad de Riansares 4R / 2.3. Servicios nucleares / Reformas
- Eventos: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB

#### EN 02 — Concrete slab versus flooring

- Pregunta: In construction, how does the concrete base differ from the finished flooring?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: The concrete base (solera) is a structural foundation that includes preparations like thickness, reinforcement, and joints, whereas the finished flooring (solado) refers to the final pavement placed on this prepared base. [S1]
- Traducción local ms: 3308.11
- Cita [S1]: `R4R_10_CASOS_PATIOS_VIVIENDAS_HUMEDADES_Y_REPARACIONES.md` · Riansares 4R — casos de patios, viviendas, humedades y reparaciones / 6. Caso A — Patio, solera, pavimento y drenaje / 6.3. Distinción entre solera y solado
- Eventos: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB

#### EN 03 — Access and cost

- Pregunta: Why does access affect the price of a concrete slab?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: Access affects the price of a concrete slab because it increases mixing and manual transportation hours, coordination, and risk compared to direct unloading or pumping. [S1]
- Traducción local ms: 2838.28
- Cita [S1]: `faqs/R4R_40_ALL_FAQ.md` · Riansares 4R — todas las preguntas frecuentes / Patios / FAQ-045 — ¿Por qué el acceso cambia el precio de una solera?
- Eventos: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB

#### EN 10 — Timelines

- Pregunta: Why is it important to know the expected timeline before preparing a renovation estimate?
- Resultado original: PASSED; abstención: False; proveedor real verificado: True.
- Respuesta: Knowing the expected timeline is crucial for accurately estimating materials, labor, and other resources required for a renovation project. This ensures that all aspects, including contingencies and deadlines, are properly accounted for. [S3]
- Traducción local ms: 3810.68
- Cita [S3]: `R4R_30_METODO_PRESUPUESTOS_SEO_CATALOGO_Y_PLANTILLAS.md` · Riansares 4R — método, presupuestos, SEO, catálogo y plantillas / 15. Presupuestación con criterio
- Eventos: REQUEST_RECEIVED → TRANSLATION_STARTED → TRANSLATION_COMPLETED → RETRIEVAL_STARTED → EMBEDDING_STARTED → EMBEDDING_COMPLETED → RETRIEVAL_COMPLETED → GENERATION_STARTED → GENERATION_COMPLETED → VALIDATION_COMPLETED → ANSWER_READY
- Memoria antes: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB
- Memoria después: synology: nomic-embed-text:latest total=0.36 GiB / VRAM=0.00 GiB; pc: qwen3:8b total=5.28 GiB / VRAM=4.31 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB; laptop: qwen2.5:7b total=4.67 GiB / VRAM=2.89 GiB, nomic-embed-text:latest total=0.37 GiB / VRAM=0.10 GiB

### Protección del corpus

```text
BENCHMARK_SETTINGS topK=5 minScore=0.63 translationTimeout=30s think=false
BENCHMARK_SAFETY readonly=true flyway=false sqlInit=never schemaInit=false catalog=false openai=false startupWarmup=false
BENCHMARK_CORPUS {"readonly":"on","vectorType":"vector(768)","snapshot":{"rows":282,"fingerprint":"58059dbac293f4ce2db72d942552610e"}}
BENCHMARK_CORPUS_END {"rows":282,"fingerprint":"58059dbac293f4ce2db72d942552610e"}
```

El total backend procede de las métricas SSE; E2E SSE es el cronómetro real del cliente desde POST hasta recibir ANSWER_READY/ERROR. Ambos excluyen las lecturas /api/ps del adaptador. No incluyen MyMemory del frontend porque se ejecutan los IT del backend. La recuperación incluye embeddings y expansión de secciones. `vectorSearchMs=null`: no se estima por resta. No se ha repetido REST para medir la misma pregunta. Los modelos conservan residencia natural; no se descargan ni desalojan. Las medias de fallos están separadas de las exitosas. No se midió consumo eléctrico ni utilización de GPU, por lo que no se atribuye causalidad energética.
