# Validación Titan — 10 de noviembre de 2026

## Resultado

Integración Spring implementada y validada offline. Lambda actualizada y activa.
Titan desactivado por defecto. **0 embeddings reales importados**, porque la única
comprobación nueva de Bedrock devolvió `ThrottlingException`. No se ha demostrado
recuperación semántica AWS, calibración ni generación completa real con Titan.

## Evidencia de ejecución

Java **21.0.12.1** (Ubuntu), Maven **3.8.7**, compilación release 21.
Se ejecutaron los comandos completos, sin `skipTests`, sin arrancar contenedores,
sin requerir PostgreSQL/AWS/Ollama y sin consultar de nuevo el corpus en PG.
Las dependencias Maven nuevas sí se descargaron del repositorio de artefactos.
El estado actual de los contenedores NAS no se inspeccionó: SSH no permitió entrar.
La independencia de servicios se verifica mediante DataSource/SDK/LLM simulados,
protección JDBC offline y endpoints locales en el perfil de test.

| Comando real | Resultado |
|---|---|
| `mvn clean package` | BUILD SUCCESS; 370 obligatorias, 0 errores/fallos; JAR generado. |
| `mvn clean verify` | BUILD SUCCESS; 370 obligatorias + 46 integraciones offline aprobadas; 25 entradas reales SKIPPED/NOT RUN. |
| `mvn -f lambda-titan/pom.xml clean verify` | BUILD SUCCESS; 21 tests (12 handler + 9 importador), 0 fallos/errores. |
| `python3 -m unittest discover -s lambda-titan -p 'test_*.py'` | 8 aprobados; herramientas/transporte con mocks. |
| `python3 py-scripts/test_secret_isolation.py` | 1 aprobado; Maven verify con secretos sintéticos y flags heredados, sin divulgación ni activación de pruebas reales. |
| `python3 py-scripts/test_compose_configuration.py` | 1 aprobado; Compose aislado/autocontenido, Titan false y umbral vacío. |
| `python3 lambda-titan/corpus.py` | Auditoría offline: 282 IDs únicos y manifiesto idéntico al original. |
| `python3 lambda-titan/evaluate.py` | Preview offline: 30 preguntas ES/EN canónicas; cero consultas AWS. |
| `python3 -m py_compile ...` | Sin errores en las herramientas Python modificadas. |
| `git diff --check` y `git diff --cached --check` | Sin errores. |

Las 30 preguntas bilingües del HTTP offline son casos de contrato con resultados
simulados: no se presentan como evaluación de relevancia real. Se conservaron los
asserts anteriores y se añadieron checks de orden, recuperación, errores, citas,
metadatos nulos, cancelación, separación de espacios y métricas.

## AWS real

Todas las operaciones locales utilizaron exclusivamente `r4r-codex`, región
`eu-north-1`, identidad `r4r-codex-deployer` de la cuenta `124021120482`.

- Prueba mínima Bedrock: `2026-11-10T04:42:34.931780+00:00`, un intento, salida CLI 254, `ThrottlingException`. No se volvió a invocar el modelo.
- Función: `r4r-titan-retrieval`, Java21, 1024 MB, timeout 30s; estado final `Active`, actualización `Successful`.
- Código final verificado contra AWS: `PiAYhXakrQtb1k3f9A6QPV6sy7MS62HgUGs+kXUHAVU=`.
- Comprobación posterior: `2026-11-10T05:06:46.064897+00:00`.
- JAR Lambda: 16 323 173 bytes; solo clases R4R ligeras, sin clases Spring.
- Invocación real con pregunta vacía: HTTP 200 + `FunctionError=Unhandled`, mensaje `Invalid retrieval request`, contrato esperado. **No invocó Bedrock y no mide recuperación semántica**.
- Índice Titan: `0` registros; 1024 dimensiones, float32, coseno; `source_text` no filtrable. `TITAN_MIN_SIMILARITY` de Lambda sin establecer.

Se actualizó únicamente el código de la función existente. Se verificó el hash
local/remoto con revision-id; no se cambió configuración, concurrencia compartida,
roles, índices, políticas, logs o backend Synology. El índice Nomic no se modificó.

## Pruebas reales no ejecutadas

| Grupo | Entradas JUnit omitidas en verify | Motivo |
|---|---:|---|
| PostgreSQL/pgvector | 17 | Servicios detenidos según el usuario; requieren `-Ppostgres-it`. |
| S3/Nomic | 1 | Requiere `-Paws-it` y configuración real explícita. |
| LLM/API E2E | 6 | Requiere `-Pllm-it`, endpoints dedicados y modelos reales. |
| Titan | 1 | Requiere `-Ptitan-it`, Bedrock operativo, índice poblado y umbral explícito. Al habilitarlo genera los 30 casos canónicos. |

La prueba Lambda de entrada inválida anterior es una comprobación real estructural,
no sustituye estos tests. No se ejecutaron ingesta, consultas ES/EN válidas a Lambda,
evaluación semántica, mediciones de embedding/búsqueda reales ni E2E Spring + AWS.

## IAM y siguientes pasos

La identidad efectiva del backend NAS sigue **sin verificar**: SSH BatchMode devolvió
`Permission denied (publickey,password)`. La configuración existente utiliza la
cadena SDK y variables AWS del contenedor; eso no demuestra qué ARN corresponde.
Se preparó `lambda-titan/iam/backend-invoke-titan.json`, con una única acción y ARN,
y el diagnóstico Java `RuntimeIdentity`. No se aplicó ninguna política.

Pendientes: confirmación AWS → ingesta de 282 → validación de vectores/metadatos →
evaluación/calibración ES/EN → permiso de la identidad real NAS → activación en un
entorno dedicado → pruebas completas de fallback y generación → preparación del
despliegue backend. Los comandos exactos están en
[la guía operativa](titan-lambda-retrieval.md#activación-y-validación-real-pendiente).

## Git y archivos

Rama `feature/aws-bedrock-embed-titan`, HEAD conservado `5cd8754`.
No se ejecutaron add, commit, merge, reset, checkout ni push. Los cambios quedan
sin registrar para revisión/controlador. El corpus exportado no se modificó.

Archivos modificados o añadidos en esta entrega:

- `docker-spring/.env.base`
- `docker-spring/docker-compose.yml`
- `docs/operations/titan-lambda-retrieval.md`
- `docs/testing.md`
- `lambda-titan/corpus.py`
- `lambda-titan/deploy.py`
- `lambda-titan/evaluate.py`
- `lambda-titan/pom.xml`
- `lambda-titan/src/main/java/com/riansares/r4r/titan/RetrievalHandler.java`
- `lambda-titan/test_corpus.py`
- `pom.xml`
- `py-scripts/r4r_live_failover_validation.py`
- `py-scripts/test_compose_configuration.py`
- `py-scripts/test_secret_isolation.py`
- `src/main/java/com/riansares/r4r/rag/RagProgress.java`
- `src/main/java/com/riansares/r4r/vector/FailoverKnowledgeVectorStore.java`
- `src/main/java/com/riansares/r4r/vector/config/KnowledgeVectorStoreConfiguration.java`
- `src/main/java/com/riansares/r4r/vector/s3vectors/S3VectorMetadataMapper.java`
- `src/main/resources/application.yml`
- `src/test/java/com/riansares/r4r/vector/s3vectors/S3VectorMetadataMapperTest.java`
- `src/test/resources/application.yml`
- `docs/operations/titan-validation-2026-11-10.md`
- `lambda-titan/aws_cli.py`
- `lambda-titan/iam/backend-invoke-titan.json`
- `lambda-titan/src/main/java/com/riansares/r4r/titan/CorpusImporter.java`
- `lambda-titan/src/main/java/com/riansares/r4r/titan/RuntimeIdentity.java`
- `lambda-titan/src/main/java/com/riansares/r4r/titan/TitanEmbeddingClient.java`
- `lambda-titan/src/test/java/com/riansares/r4r/titan/CorpusImporterTest.java`
- `py-scripts/r4r_evaluation_cases.py`
- `src/main/java/com/riansares/r4r/vector/config/TitanRetrievalConfiguration.java`
- `src/main/java/com/riansares/r4r/vector/config/TitanRetrievalProperties.java`
- `src/main/java/com/riansares/r4r/vector/titan/TitanLambdaKnowledgeStore.java`
- `src/test/java/com/riansares/r4r/vector/TitanDegradedHttpIT.java`
- `src/test/java/com/riansares/r4r/vector/TitanFailoverTest.java`
- `src/test/java/com/riansares/r4r/vector/TitanLambdaReadOnlyIT.java`
- `src/test/java/com/riansares/r4r/vector/config/TitanRetrievalConfigurationTest.java`
- `src/test/java/com/riansares/r4r/vector/titan/TitanLambdaKnowledgeStoreTest.java`
- `src/test/java/com/riansares/r4r/vector/titan/TitanResponses.java`
