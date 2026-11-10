# Transporte Lambda para S3 Nomic

## Cierre verificado en AWS, 10-11-2026

La función `r4r-s3-vectors-retrieval` está desplegada y Active, Java21/x86_64, 512 MB, 15 s, rol `r4r-lambda-execution`. Se comprobó ResourceNotFound antes de crearla y se actualizó después únicamente su código para corregir la serialización de metadatos multilínea (Document.toString no es JSON fiable). SHA256 base64 final: `HM5CGHEgQBjGaughJ+r90IsMRQIU9crHLQQDN3ypAi8=`. Prueba de regresión añadida para comillas, barras y saltos de línea. Titan, Bedrock e índices/datos existentes no se modificaron.

Prueba real reproducible, opt-in y desactivada en verify habitual:

```bash
RAG_EMBEDDING_LIVE_URL=http://localhost:11434 mvn -Dr4r.s3.lambda.live=true \
  -Dit.test=S3LambdaLiveIT test-compile failsafe:integration-test failsafe:verify
```

Se usó Spring AI OllamaApi con `nomic-embed-text:latest`, digest observado `0a109f422b47e3a30ba2b10eca18548e944e8a23073ee3f3e947efcf3c45e59f`, dos embeddings reales de 768 dimensiones. El proveedor .209 no estaba accesible; localhost sí, sin cambiar configuración de producción. Cada vector se reutilizó para directo y Lambda. No se usaron embeddings inventados.

| Consulta | Candidatos direct/Lambda | Documentos finales (topK5, minScore .63, m8, w.10) | Directo ms | Invoke ms | Igualdad |
|---|---|---|---|---|---|
| ¿Qué información hace falta para una reforma? | 40/40 | 5/5 | 393.56 | 5724.25 (primera/cold) | claves, distancias, metadatos y MarkdownChunk iguales |
| What information is needed for a renovation? | 40/40 | 0/0 | 135.55 | 286.36 | mismos 40 candidatos; ambos abstienen tras el umbral |

La ausencia EN es legítima y equivalente a directo; no se bajó el umbral ni se ajustó ranking para forzar resultados. No equivale a demostrar relevancia general ni calidad de generación. Las fuentes ES finales fueron FAQ ordinales 4,66,37; método/presupuestos ordinal35; oficinas/cuevas/baños ordinal2, conservando su orden y headings. La comparación de MarkdownChunk incluye texto exacto y procedencia para citas.

Una comprobación adicional con un vector Nomic del export existente produjo vectorSearchMs=163.229383, duración Lambda=199.48 ms, facturada=200 ms, memoria máxima=167 MB. RequestId `b226a77a-c802-4452-a7c3-25c237add288`. Los logs saneados devueltos por Invoke/Tail contienen únicamente evento y métrica.

Limitación operativa: `logs:FilterLogEvents` sigue denegado para el deployer sobre `arn:aws:logs:eu-north-1:124021120482:log-group:/aws/lambda/r4r-s3-vectors-retrieval`. No se eludió ni modificó esa autorización; el resumen de ejecución se comprobó por el mecanismo de diagnóstico incluido en Invoke. El permiso del rol para leer S3 con metadatos quedó demostrado por las invocaciones correctas. La identidad del backend NAS aún debe tener InvokeFunction; no se cambió ni desplegó el NAS.

Para activar tras configurar esa identidad, usar las cuatro variables indicadas debajo, recomendando **RAG_S3_LAMBDA_TIMEOUT=10s** debido a los 5.72 s medidos en la primera invocación. El valor por defecto de código sigue siendo 5s; la prueba real usó un timeout diagnóstico de 30s. La reversión sigue siendo transport=direct. No hay Function URL, API Gateway ni otros recursos nuevos aparte de la Lambda y sus logs.

CV: «Implementé y desplegué una Lambda privada Java 21 para consultar Amazon S3 Vectors con embeddings Nomic de 768 dimensiones, integrada con Spring Boot mediante IAM; validé equivalencia real ES/EN frente al acceso directo conservando el filtrado, BM25 y citas existentes». No presentar esto como independencia del NAS o de los embeddings locales.

## Arquitectura, activación y reversión

Spring Boot genera Nomic local; S3VectorsKnowledgeStore delega solo QueryVectors a direct o Lambda. La misma lógica existente valida candidatos, aplica umbral y NomicCandidateReranker y reconstruye MarkdownChunk. PGVector → S3 (transporte seleccionable) → Titan opcional conserva sus reglas. La Lambda no genera embeddings ni aplica BM25. Contrato v1: embedding finito no cero de 768 floats, topK 1–10000, bucket/índice fijos, candidatos con distancia original y metadatos completos; respuesta e input acotados. Sin Spring ni Bedrock en el módulo independiente; SDK2 y clientes reutilizados.

Activación manual futura en Synology, sin despliegue realizado:

```dotenv
RAG_S3_VECTORS_TRANSPORT=lambda
RAG_S3_LAMBDA_FUNCTION=r4r-s3-vectors-retrieval
RAG_S3_LAMBDA_REGION=eu-north-1
RAG_S3_LAMBDA_TIMEOUT=10s
```

Conservar S3 habilitado, bucket/índice y generación actuales. El transporte predeterminado continúa siendo direct. Reversión: RAG_S3_VECTORS_TRANSPORT=direct. El principal del backend necesita lambda:InvokeFunction sobre arn:aws:lambda:eu-north-1:124021120482:function:r4r-s3-vectors-retrieval. La lectura autorizada del rol Lambda requiere s3vectors:QueryVectors y s3vectors:GetVectors sobre arn:aws:s3vectors:eu-north-1:124021120482:bucket/amzn-r4r-vectors/index/amzn-r4r-vector-index y logging básico. No se inspeccionaron ni modificaron políticas o credenciales privadas.

## Despliegue y pruebas

```bash
mvn clean verify
mvn -f lambda-s3-vectors/pom.xml clean verify
bash lambda-s3-vectors/deploy.sh
```

El script verifica identidad y existencia del índice, prueba el módulo y crea solamente la nueva función si no existe; si existe, actualiza solo código con RevisionId. Espera Active/Successful. Todas las llamadas CLI usan --profile r4r-codex --region eu-north-1. Los clientes del test real seleccionan explícitamente ese perfil y región; Lambda utiliza su rol. AccessDenied detiene la operación afectada, sin solicitar permisos administrativos. No se ejecutó ninguna llamada Bedrock, ingesta o escritura de vectores.

En el primer despliegue, la prueba real detectó JsonParseException por serializar texto documental mediante Document.toString. Se corrigió y añadió regresión; la actualización de código pasó las pruebas antes de publicarse. Primer intento de Nomic .209: timeout de conexión, sin llamadas AWS en ese intento. Después se usó el proveedor local disponible. Las invocaciones fallidas de diagnóstico no se presentan como éxitos.

Verificación final: módulo Lambda 9 tests aprobados; backend verify con integración real opt-in desactivada por defecto; test real S3LambdaLiveIT aprobado para las dos preguntas. Las pruebas previas de Titan y direct permanecen sin cambios. git diff --check limpio. No se modificó producción ni se amplió el alcance. El bloqueo inicial de GetFunctionConfiguration quedó resuelto por el propietario de IAM antes de esta sesión; el permiso FilterLogEvents indicado arriba permanece pendiente y no impide la consulta ni la comparación real ya demostrada.

## Costes y límites

Solo cómputo Lambda bajo demanda, consultas al índice existente y logs. Sin provisioned concurrency ni nueva base de datos. No se obtuvo una factura ni cotización regional: no confundir duración medida con precio facturado. Se hicieron únicamente consultas de diagnóstico y dos preguntas de comparación, sin benchmarks masivos. Sigue existiendo dependencia de Nomic local y del backend NAS; esta fase desacopla el transporte de búsqueda S3.
