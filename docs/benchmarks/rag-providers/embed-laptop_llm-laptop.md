# D — Embeddings laptop + generación laptop

**Estado: NOT RUN.** NOT RUN: faltan credenciales PostgreSQL disponibles fuera de .env para conectar Spring Boot local al corpus real en solo lectura.

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

No se han enviado consultas de benchmark. «—» significa no medido; no equivale a cero.

| Consulta | Estado | embeddingMs | retrievalIncludingEmbeddingMs | vectorSearchMs | generationMs | totalMs | Proveedor/modelo utilizado | Documentos recuperados | Error/timeout |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 · ES · caso 02 | NOT RUN | — | — | — | — | — | No ejecutado | — | Credenciales de lectura no disponibles; no es timeout RAG. |
| 2 · ES · caso 05 | NOT RUN | — | — | — | — | — | No ejecutado | — | Credenciales de lectura no disponibles; no es timeout RAG. |
| 3 · ES · caso 11 | NOT RUN | — | — | — | — | — | No ejecutado | — | Credenciales de lectura no disponibles; no es timeout RAG. |
| 4 · EN · caso 02 | NOT RUN | — | — | — | — | — | No ejecutado | — | Credenciales de lectura no disponibles; no es timeout RAG. |
| 5 · EN · caso 05 | NOT RUN | — | — | — | — | — | No ejecutado | — | Credenciales de lectura no disponibles; no es timeout RAG. |
| 6 · EN · caso 11 | NOT RUN | — | — | — | — | — | No ejecutado | — | Credenciales de lectura no disponibles; no es timeout RAG. |

## Agregados

Media de embeddings, media de generación, media/mediana/máximo total y consultas
correctas/fallidas: **no calculables**. No existen muestras ni se convierten NOT RUN
en fallos funcionales. vectorSearchMs permanecerá null en una ejecución real porque
Spring AI no ofrece la medida aislada; aquí ni siquiera se ha ejecutado la búsqueda.

## Memoria observada antes de iniciar la comparación

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

No es posible evaluar latencia total, corrección, citas ni competencia por GPU sin
conexión al corpus. Los modelos requeridos están instalados, por lo que el bloqueo
no se etiqueta como SKIPPED por modelo ausente. La compatibilidad de artefactos nomic
entre los tres servidores se confirma por digest y dimensiones; la configuración y
proveniencia actuales de los vectores almacenados requieren la comprobación SQL de
solo lectura pendiente. No se recomienda esta variante sobre las demás sin muestras.
