# Transporte Lambda para S3 Nomic

## Estado real, 10-11-2026

Implementación local. Despliegue bloqueado, **no existe evidencia de una consulta real vía esta nueva Lambda**. La identidad comprobada es `arn:aws:iam::124021120482:user/r4r-codex-deployer`; GetIndex confirma el índice Nomic de 768 dimensiones y coseno. La comprobación previa devuelve AccessDenied para `lambda:GetFunctionConfiguration` sobre `arn:aws:lambda:eu-north-1:124021120482:function:r4r-s3-vectors-retrieval`. No se intentó crear sin comprobar el estado ni se modificó IAM. No se ha comprobado CreateFunction/PassRole ni la autorización Nomic del rol mediante ejecución real. No se declara la tarea cerrada bajo el criterio de Lambda desplegada + consulta real + tests verdes.

## Diseño

Nomic local → S3VectorsKnowledgeStore → transporte direct o Lambda → índice Nomic existente. La pequeña abstracción Function<QueryVectorsRequest,QueryVectorsResponse> cambia exclusivamente el acceso remoto; la validación del adaptador, umbral, reranking Nomic BM25 y reconstrucción MarkdownChunk siguen ejecutándose en el mismo código. No se duplican reglas de relevancia. El router PostgreSQL → S3 → Titan opcional no cambia. `lambda-titan/`, sus funciones, modelos e índices permanecen intactos. No hay independencia de embeddings locales ni del backend NAS.

Módulo independiente `lambda-s3-vectors/`: Java21, SDK2, S3VectorsClient reutilizado, sin Spring/Bedrock. Reutiliza por compilación las fuentes existentes de MarkdownChunk y mappers, como el módulo Titan, sin copiarlas. QueryVectors devuelve distancias originales y metadatos completos. El contrato v1 fija bucket e índice, 768 componentes finitos no cero y topK 1–10000. Se validan métrica, paginación, duplicados, presupuesto de respuesta y procedencia. La Lambda no aplica umbral ni BM25. Input máximo 64 KiB, output máximo 5 MiB. Las excepciones públicas/logs se sanean, sin preguntas, embeddings o documentos.

## Activación y reversión

```dotenv
RAG_S3_VECTORS_TRANSPORT=lambda
RAG_S3_LAMBDA_FUNCTION=r4r-s3-vectors-retrieval
RAG_S3_LAMBDA_REGION=eu-north-1
RAG_S3_LAMBDA_TIMEOUT=5s
```

Mantener S3 habilitado y su bucket/índice Nomic actuales. Predeterminado `direct`; reversión cambiando exclusivamente `RAG_S3_VECTORS_TRANSPORT=direct`. Las variables se propagan en Compose. No se activó ni desplegó en Synology. Un resultado vacío no activa otro fallback. Nomic continúa generándose localmente; su error conserva el tratamiento vigente. FunctionError de ejecución representa indisponibilidad técnica; errores del contrato no se confunden con ausencia legítima de resultados. Las reglas de Titan no se modifican.

## IAM y despliegue

El administrador debe permitir al deployer GetFunctionConfiguration sobre la nueva función para desbloquear la comprobación inicial. Para el flujo completo se requieren CreateFunction o UpdateFunctionCode, GetFunctionConfiguration/GetFunction, y PassRole limitado a Lambda y `r4r-lambda-execution`. No se solicita inspección administrativa IAM.

El rol de ejecución debe tener `s3vectors:QueryVectors` y `s3vectors:GetVectors` sobre `arn:aws:s3vectors:eu-north-1:124021120482:bucket/amzn-r4r-vectors/index/amzn-r4r-vector-index`, además de logging. Reutilizar el rol existente solo si ya cumple estos permisos; no se amplió. La identidad del backend necesita `lambda:InvokeFunction` exclusivamente sobre la nueva función. Su identidad desplegada y permisos siguen sin comprobar; no se modificaron secretos/políticas de producción.

```bash
mvn clean verify
mvn -f lambda-s3-vectors/pom.xml clean verify
bash lambda-s3-vectors/deploy.sh
```

El script verifica identidad, índice, existencia de función (AccessDenied detiene el flujo), compila y muestra configuración antes de publicar. Crea únicamente la nueva función privada Java21/512MB/15s sin VPC; si existe, actualiza solo código con RevisionId. Espera Active/Successful. No crea URLs, API Gateway, índices, provisioned concurrency o cambios Titan. El rol básico escribe logs; configurar retención según política operativa antes del uso continuado.

Tras autorizar el despliegue, generar localmente un embedding Nomic para cada pregunta: «¿Qué información hace falta para una reforma?» y «What information is needed for a renovation?». Guardar sendos JSON v1 con exactamente 768 floats y topK 40, sin enviar preguntas a Lambda. Para cada vector, consultar S3 directamente y Lambda usando el mismo vector, ordenar por clave y comparar distancias/metadatos, luego verificar los documentos finales del adaptador con ambos transportes. Solo dos preguntas, sin benchmarks.

```bash
aws lambda invoke --function-name r4r-s3-vectors-retrieval \
  --payload fileb:///tmp/r4r-s3-query-es.json \
  --profile r4r-codex --region eu-north-1 /tmp/r4r-s3-result-es.json
aws logs filter-log-events --log-group-name /aws/lambda/r4r-s3-vectors-retrieval \
  --profile r4r-codex --region eu-north-1
```

Comprobar FunctionError además de HTTP 200; registrar vectorSearchMs y comparar evidencias/citas sin publicar textos privados. Pendientes de IAM: despliegue, acceso efectivo del rol, comparación real ES/EN y logs reales. No se ejecutó ninguna llamada Bedrock.

## Pruebas y CV

Suite backend completa y módulo independiente se ejecutan offline; nuevos tests cubren contrato, vector válido/inválido, duplicados, paginación, metadatos, exceso, cancelación, errores, transporte, FunctionError/timeouts y equivalencia del filtrado/citas con respuestas simuladas. Los tests anteriores se conservan, incluido Titan desactivado por defecto. La comparación offline usa la misma lista de candidatos para comprobar que ambos transportes recorren la misma lógica; no sustituye una prueba real AWS.

Redacción de CV válida actualmente: «Implementación de un transporte Lambda Java 21 para consultas privadas de S3 Vectors, manteniendo embeddings Nomic y ranking BM25 en Spring Boot, con contrato versionado y pruebas offline». Añadir «desplegado y validado ES/EN en AWS» únicamente tras resolver IAM y registrar esas pruebas. Costes pendientes de medición; solo cómputo bajo demanda, consultas al índice existente y logs, sin capacidad provisionada.

Resultado final de esta ejecución: `mvn clean verify` BUILD SUCCESS; Surefire 424 tests, 0 fallos/errores; Failsafe 102 entradas, 26 omitidas por perfiles, 0 fallos/errores. Módulo Lambda: 8 tests, 0 fallos/errores y JAR Shade generado. `git diff --check` y sintaxis shell correctos. `git diff --name-only -- lambda-titan` vacío. No commits, merge o push; la historia queda en manos del controlador conforme a AGENTS.md.
