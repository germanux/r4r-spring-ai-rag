# A — Embeddings synology + generación pc

**Estado: NOT RUN.** NOT RUN: faltan credenciales PostgreSQL disponibles fuera de .env para conectar Spring Boot local al corpus real en solo lectura.

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

No es posible evaluar latencia total, corrección, citas ni competencia por GPU sin
conexión al corpus. Los modelos requeridos están instalados, por lo que el bloqueo
no se etiqueta como SKIPPED por modelo ausente. La compatibilidad de artefactos nomic
entre los tres servidores se confirma por digest y dimensiones; la configuración y
proveniencia actuales de los vectores almacenados requieren la comprobación SQL de
solo lectura pendiente. No se recomienda esta variante sobre las demás sin muestras.
