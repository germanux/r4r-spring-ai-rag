# Validación Nomic y revisión Titan — 10-11-2026

## Resultado

Se reproduce y corrige la recuperación del Caso G con **PG y S3 reales**. No se
modificó el índice original Nomic ni el corpus, no se desplegó en Synology, no se
modificó IAM y **no se hizo ninguna llamada a Bedrock**.

Causa: limitación prematura a cinco vecinos semánticos. El Caso G existe y supera
0.63, pero sus primeros fragmentos quedan en 19/6 para las consultas breves. La
corrección compartida amplía a veinte candidatos válidos y usa un apoyo léxico
BM25 acotado antes de devolver cinco. Mantiene modelo, digest, embeddings, umbral,
IDs, textos y contratos del router. No hay sinónimos/reglas por documento o consulta.

## Resultado real verificable

| Consulta | Primer Caso G antes | Después en PG y S3 | Respuesta local después |
|---|---:|---:|---|
| ¿Con qué reformaríais una cueva? | 19, coseno PG 0.639080 | 5 | Mortero de cal/NHL 2/3.5 y preparación; cita del Caso G `[S5]`. |
| ¿Con qué reformaríais una bodega? | 6, coseno PG 0.667478 | 1, también título en 3 | Cal y malla galvanizada, citando el caso específico. |
| ¿Qué mortero utilizaríais para rehabilitar una cueva antigua? | 3, coseno PG 0.718790 | 2, también materiales en 5 | NHL 2/3.5, compatibilidad y citas del caso. |

- PostgreSQL: 282 filas; se verificó sesión `transaction_read_only=on`.
- S3/Nomic: recuperados los 282 IDs del export, incluidos los nueve del Caso G.
  Los vectores coinciden con PG (diferencia máxima de componentes 0.0).
- Tres re-embeddings del contenido PG original arrojan coseno >0.999995 con el
  vector guardado, con el digest Nomic configurado y comprobado por el router.
- La prueba `search_query:` no arregla el caso: puesto 22 para «cueva»; descartada.
- Batería real adicional: **30 preguntas canónicas ES/EN, 60 respuestas locales**.
  Antes: 23 respuestas / 7 abstenciones; después: **24 respuestas correctas según
  las expectativas de evidencia y 6 abstenciones fuera de dominio**. Se comprobaron
  referencias y procedencia de citas en las respuestas. Es una ejecución A/B, no
  una certificación de factualidad de cada frase ni de cualquier paráfrasis futura.
- Una pregunta inglesa de solera/solado pasa de abstenerse a responder. Ninguna
  respuesta anterior pasa a abstención ni desaparece una abstención fuera del dominio.
- Los cinco fragmentos finales y su orden coinciden **exactamente entre PG y S3
  para los 33 casos reales**. La generación se ejecutó sobre los resultados PG;
  no se duplicaron llamadas LLM sobre los mismos textos S3.
- Se utilizaron Spring AI, `GenerationRouter`, `RagConfiguration` y `CitedRagService`
  existentes. No se llamó a la API NAS, no se cambió su cuota/configuración, no se
  inició otro backend y OpenAI se desactivó expresamente para el diagnóstico.

El detalle de scores, filtrado, contexto del embedding, alternativas comparadas,
parámetros configurables y reproducción está en
[el diagnóstico técnico](nomic-cave-retrieval.md).

## Pruebas de esta entrega

| Comando | Resultado |
|---|---|
| `mvn clean verify` inicial | Correcto antes de editar: 370 obligatorias + 46 offline. |
| `mvn clean verify` final | **BUILD SUCCESS: 413 obligatorias, 46 offline, cero fallos/errores**. 25 entradas de servicios reales NOT RUN/SKIPPED. Incluye generación del JAR. |
| `mvn -f lambda-titan/pom.xml clean verify` | **21 aprobados**, cero fallos/errores. |
| `python3 -m unittest discover -s lambda-titan -p 'test_*.py'` | **8 aprobados**, SDK/transporte simulados. |
| `python3 py-scripts/test_compose_configuration.py` | **1 aprobado**, configuración aislada. |
| `python3 py-scripts/test_secret_isolation.py` | **1 aprobado**, verify con secretos sintéticos y servicios reales optativos. |
| Diagnóstico main con tres consultas y `--generate-local` | 3 comparaciones reales PG/S3 y 6 respuestas Ollama locales, completadas. |
| Diagnóstico main con las 30 consultas originales | 30 comparaciones reales PG/S3 y 60 respuestas Ollama locales, completadas. |
| Verificación de procedencia de citas de ambos informes | Correcta para todas las respuestas no abstencionistas. |
| `git diff --check` y `git diff --cached --check` | Sin errores. |

Las regresiones Nomic offline reproducen **vectores de consulta reales capturados**,
no embeddings artificiales. El transporte se simula para que el build no dependa de
servicios. Se reutilizan el export de corpus y las preguntas originales; la fixture
nueva solo contiene vectores y su procedencia/checksum. Las pruebas de contrato SSE,
fallos, timeouts y cancelación siguen en el gate ordinario.

Las 25 entradas JUnit reales siguen optativas y omitidas en el verify estándar;
las lecturas/generaciones manuales anteriores no se presentan como ejecución de
los perfiles completos `postgres-it`, `aws-it`, `llm-it` o `titan-it`. No se ejecutó
la integración mutante PG ni la batería contra una API desplegada.

## Revisión Titan

Se revisaron `CorpusImporter`, `TitanEmbeddingClient`, `TitanLambdaKnowledgeStore`,
router, configuración e integraciones. Sin cambios innecesarios en el importador,
modelo, fuente compartida ni Lambda. Las escrituras del backend siguen en PG y se
ha añadido un test explícito con Titan habilitado. La reordenación nueva solo se
aplica en los adaptadores Nomic de 768 dimensiones.

Se corrigió en el backend la coerción permisiva de dimensiones de la respuesta
Lambda: ahora se exige entero JSON 1024 y se rechazan `"1024"`, `1024.5` y 768.
También se exige el tipo textual de `source`, además de coincidir con metadatos.
Se mantienen umbrales separados, fuentes/citas, cancelaciones, timeouts y errores.
Titan sigue desactivado por defecto; no se ha intentado otra comprobación Bedrock,
ingesta, despliegue Lambda o modificación AWS en esta entrega.

## Pendientes reales

- La corrección aún no está desplegada en Synology, por restricción expresa. La
  API actualmente desplegada conserva el comportamiento anterior hasta su despliegue.
- Titan real sigue pendiente de AWS Support: habilitación Bedrock, ingesta 282,
  calibración ES/EN y validación end-to-end. No se declara aprobado.
- El A/B demuestra esta batería y ejecución; ampliar seguimiento con otras
  paráfrasis/corpus futuros sin bajar umbrales por intuición. Los parámetros son
  reversibles y el ranking anterior se reproduce con multiplicador 1/peso 0.

## Evidencia y Git

Informes completos/logs conservados en `target/nomic-validation/` (ignorado):
`baseline-ranking.json`, `adapter-live.json`, `canonical-live.json`,
`live-summary.json`, `comparison.txt` y logs de gates. No contienen credenciales;
las respuestas completas pertenecen a datos de runtime y no se versionan.

Rama `feature/aws-bedrock-embed-titan`; HEAD conservado en `6f98e66`.
Sin add, commit, merge, reset, checkout ni push. Archivos afectados:

- `docker-spring/.env.base`
- `docker-spring/docker-compose.yml`
- `docs/testing.md`
- `src/main/java/com/riansares/r4r/vector/config/KnowledgeVectorStoreConfiguration.java`
- `src/main/java/com/riansares/r4r/vector/config/S3VectorsConfiguration.java`
- `src/main/java/com/riansares/r4r/vector/pgvector/PgVectorKnowledgeStore.java`
- `src/main/java/com/riansares/r4r/vector/s3vectors/S3VectorsKnowledgeStore.java`
- `src/main/java/com/riansares/r4r/vector/titan/TitanLambdaKnowledgeStore.java`
- `src/main/resources/application.yml`
- `src/test/java/com/riansares/r4r/vector/TitanFailoverTest.java`
- `src/test/java/com/riansares/r4r/vector/config/KnowledgeVectorStoreConfigurationTest.java`
- `src/test/java/com/riansares/r4r/vector/pgvector/PgVectorSectionContextTest.java`
- `src/test/java/com/riansares/r4r/vector/s3vectors/S3VectorsKnowledgeStoreTest.java`
- `src/test/java/com/riansares/r4r/vector/titan/TitanLambdaKnowledgeStoreTest.java`
- `docs/operations/nomic-cave-retrieval.md`
- `docs/operations/nomic-validation-2026-11-10.md`
- `src/main/java/com/riansares/r4r/config/NomicRerankProperties.java`
- `src/main/java/com/riansares/r4r/vector/NomicCandidateReranker.java`
- `src/test/java/com/riansares/r4r/vector/NomicCandidateRerankerTest.java`
- `src/test/java/com/riansares/r4r/vector/NomicRegressionCorpus.java`
- `src/test/java/com/riansares/r4r/vector/NomicRetrievalDiagnostic.java`
- `src/test/java/com/riansares/r4r/vector/NomicRetrievalRegressionTest.java`
- `src/test/resources/retrieval/nomic-query-vectors.json`
