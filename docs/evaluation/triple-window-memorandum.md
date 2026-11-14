# Triple Window — cierre de integración, 2026-11-14

Decisión cerrada del experimento: **topK=5, minScore=0.49**. Resultado ya obtenido:
66/70, cobertura 55/60 y diez abstenciones de control correctas. Este cierre no
repitió ninguna tanda ni reanalizó las respuestas del benchmark.

Estado comprobado: 1.375 originales + 695 ventanas = 2.070 vectores PostgreSQL.
Ingesta incremental conectada después del commit de originales; IDs y algoritmo
previos conservados. Dos sincronizaciones reales mantuvieron íntegro el corpus sin
solicitar embeddings. Pruebas aisladas cubren repetición, modificación/eliminación,
integridad, exportación conjunta y orden de transacciones/check-only/fallo original.

S3 sincronizado con los mismos 2.070 IDs y embeddings de 1024 dimensiones, con
verificación de todos los datos/metadatos y recuperación de ventanas ES/EN mediante
Lambda real. Sin recreación de recursos ni cambios IAM.

Frontend PARTIAL: aviso breve ES/EN y estilo informativo; contenido y fuentes
conservados. Chromium comprueba SSE y degradación posterior por auditoría.
Auditor: prompt intacto; cinco escenarios preparados verifican PASS, FAIL por dato
inventado/conclusión categórica, evaluación inconclusa e inferencia razonable. Son
pruebas del contrato con modelo simulado, no una nueva evaluación del LLM.

[Flujo y límites operativos](../operations/triple-window-ingestion.md).
[Evidencia compacta](../validation/mvp-closure-2026-11-14/verification.json).
