# Pruebas Maven de R4R

## Build obligatorio sin infraestructura

Java 21 y Maven. PostgreSQL, PostgreSQL-test, AWS y Ollama no son requisitos del
build ordinario. No se arrancan contenedores, no se importa el `.env` privado y no se
utilizan H2 ni SQLite. Las dependencias y plugins Maven deben estar disponibles en
la caché local o poder descargarse del repositorio de artefactos; eso es distinto
de necesitar los servicios de la aplicación durante las pruebas.

| Comando, desde la raíz | Qué ejecuta |
|---|---|
| `mvn compile` | Compila producción. |
| `mvn test` | Suite obligatoria de Surefire. |
| `mvn clean package` | Suite obligatoria y JAR ejecutable. |
| `mvn verify` | Suite obligatoria e integración offline; informa las reales como SKIPPED. |
| `mvn clean verify` | Lo anterior, recompilando desde cero. |
| `mvn verify -Ppostgres-it` | Añade PostgreSQL/pgvector real. |
| `mvn verify -Paws-it` | Añade el smoke de lectura AWS real. |
| `mvn verify -Ptitan-it -Dr4r.test.titan.minimum-similarity=VALOR` | Añade las 30 preguntas de recuperación Titan real, tras ingesta y calibración. |
| `mvn verify -Pllm-it` | Añade Ollama, OpenAI y API RAG real. |

Los perfiles se pueden combinar: añaden integraciones a la batería ordinaria.
No hay un perfil `activeByDefault` que desaparezca al seleccionar otro perfil.
Se mantienen las convenciones Surefire (`*Test`) y Failsafe (`*IT`) y las versiones
existentes de los plugins. No se utiliza `skipTests`, `maven.test.skip` ni un patrón
de tests vacío para obtener un build verde.

`scripts/verify.sh integration` utiliza ahora `mvn verify` y acepta argumentos Maven,
por ejemplo `scripts/verify.sh integration -Ppostgres-it`. Los modos `unit` y `all`
conservan sus comprobaciones del harness de agentes. El exportador Docker usa el
build offline y su mensaje ya no lo presenta como ejecución de toda la integración real.

## Categorías

### A. Obligatorias — Surefire

Lógica de negocio, documentos/chunks, validación, citas, idiomas, seguridad, routers
de generación/embeddings, configuración, failover, recuperación, cancelación y
contratos de ciclo de vida Spring/CLI con dobles. Algunas pruebas usan servidores
HTTP efímeros locales para comprobar el transporte: son dobles alojados por la
propia prueba, sin endpoints externos ni modelos reales.

Se conservan las pruebas de contrato Spring en sus nombres `*Test`: siguen siendo
obligatorias para `test` y `package`. El fichero de test `application.yml` sombrea el
de producción en el classpath de pruebas. Configura proveedores loopback, desactiva
la inicialización externa por defecto y usa un driver JDBC que rechaza conexiones.
El JAR final conserva el YAML y las clases de producción, sin esos recursos de test.

Las pruebas que inspeccionan específicamente el YAML de producción lo abren como
recurso de fichero explícito; no pasan a comprobar accidentalmente la copia de test.
Se mantiene `TestDatabaseGuard`: exige el perfil Spring `test` y rechaza URLs y
usuarios de producción. El driver real requiere además selección PostgreSQL explícita
y una contraseña de test suministrada por entorno.

Inventario auditado de las 41 clases obligatorias, bajo
`src/test/java/com/riansares/r4r/`:

| Paquete | Clases |
|---|---|
| `chunking` | `HeadingMarkdownChunkerTest` |
| `config` | `DatabaseEnvironmentTest`, `OllamaServerSeparationTest`, `PublicRagEnvironmentTest`, `RagApiCorsTest`, `RagApiSecurityFilterTest` |
| `document` | `MarkdownDocumentLoaderTest` |
| `ingestion` | `KnowledgeIngestionCliContractTest`, `KnowledgeIngestionCliTest`, `KnowledgeIngestionFailureClassificationTest`, `KnowledgeIngestionServiceTest`, `KnowledgeIngestionSpringLifecycleTest` |
| `rag` | `CitedRagMultilingualRetrievalTest`, `CitedRagServiceCitationsTest`, `CitedRagServiceInvalidAnswersTest`, `CitedRagServiceTest`, `EmbeddingProviderFailoverTest`, `GenerationRouterTest`, `LocalRetrievalQueryTranslatorTest`, `OllamaModelPreloaderTest`, `OllamaThinkingInterceptorTest`, `OpenAiFallbackTest`, `RagStreamingTest` |
| `rag/api` | `InferenceHostTest`, `RagQueryControllerTest`, `RagRequestValidationTest`, `RagRuntimeControllerTest`, `RagStreamControllerTest` |
| `service` | `KnowledgeCatalogServiceTest` |
| `vector` | `FailoverKnowledgeVectorStoreTest`, `PostgresAccessGateTest`, `TitanFailoverTest`, `NomicCandidateRerankerTest`, `NomicRetrievalRegressionTest` |
| `vector/config` | `KnowledgeVectorStoreConfigurationTest`, `PostgresFailoverStartupTest`, `S3VectorsConfigurationTest`, `S3VectorsEnvironmentCredentialsTest`, `VectorReadOnlyStartupTest`, `TitanRetrievalConfigurationTest` |
| `vector/titan` | `TitanLambdaKnowledgeStoreTest` |
| `vector/pgvector` | `PgVectorKnowledgeStoreTest`, `PgVectorSectionContextTest` |
| `vector/s3vectors` | `S3VectorMetadataMapperTest`, `S3VectorScoreMapperTest`, `S3VectorsKnowledgeStoreTest` |

### B. Integración offline — Failsafe, tag `offline`

| Clase | Casos | Evidencia |
|---|---:|---|
| `RagStreamHttpIT` | 5 | Tomcat/HTTP real, SSE, eventos antes de terminar la generación, desconexión y Origin. |
| `VectorDegradedHttpIT` | 3 | Arranque Spring con PG inaccesible desde el inicio; lectura S3 simulada y citas por REST/SSE; ambos almacenes fallan y runtime sigue disponible. |
| `TitanDegradedHttpIT` | 35 | Reutiliza el fixture de arranque degradado y las 30 preguntas ES/EN originales; Lambda simulada, REST/SSE, citas, abstención y timeout. |
| `KnowledgeIngestionCliProcessIT` | 3 | JVM hija real, salidas 0/4, resultado JSON, cierre del proceso y aislamiento de credenciales. |

Estas pruebas ejecutan Spring y el transporte real de la API, usando DataSource,
SDK AWS, embeddings y generación simulados. No validan conectividad ni permisos AWS.
`PostgresFailoverStartupTest` y `PostgresAccessGateTest`, dentro de la suite obligatoria,
conservan además migración/validación, cooldown, exclusión concurrente, vuelta a PG,
errores no recuperables y orden de transacciones. Los asserts de failover se mantienen.

### C. Integración real — explícita

Las clases reales se descubren también en el `verify` ordinario. Una condición JUnit
las deja en **SKIPPED**, con un mensaje **NOT RUN** que identifica el perfil necesario.
Al habilitar el perfil se ejecutan y los errores de configuración, infraestructura
o aserciones producen **BUILD FAILURE**. No se convierten esos errores en éxitos.

| Perfil/tag | Clases | Requisitos |
|---|---|---|
| `postgres-it` / `postgres` | `PostgresBaselineIT`, `PgVectorKnowledgeStoreIT`, `PgVectorKnowledgeStoreTransactionalRollbackIT`, `KnowledgeIngestionServiceIT` | PostgreSQL de test y pgvector, Flyway, contraseña de test. |
| `aws-it` / `aws` | `S3VectorsReadOnlyIT` | Índice de test explícito y credenciales SDK. Solo QueryVectors; sin PG ni LLM. |
| `llm-it` / `llm` | `LunaLiveIT`, `OllamaEmbeddingWarmupLiveIT`, `RagLiveQuestionsIT`, `RagLiveEnglishQuestionsIT` | OpenAI, Ollama con el modelo instalado y API de test explícita, según la clase. |

Los perfiles Maven no cambian el perfil de ejecución de la aplicación de producción.
Las propiedades `r4r.test.postgres.enabled`, `r4r.test.aws.enabled` y
`r4r.test.llm.enabled` se transmiten a Failsafe. Surefire conserva su modo offline.
Los antiguos flags `RAG_LIVE_TEST`, `RAG_LIVE_EN_TEST`, `RAG_LUNA_LIVE_TEST` y
`RAG_WARMUP_LIVE_TEST` ya no activan llamadas reales en el build ordinario.

## Ejecutar integraciones reales

### PostgreSQL

El guard existente exige `jdbc:postgresql://192.168.1.203:5433/r4r_rag_test`, usuario
`r4r_test`. Arrancar y preparar ese servidor es responsabilidad del operador; Maven
no levanta contenedores. Definir `R4R_TEST_POSTGRES_PASSWORD` en el entorno mediante
el mecanismo habitual de secretos y ejecutar:

```bash
mvn verify -Ppostgres-it
```

Estas pruebas migran y limpian tablas de la BBDD de test, y comprueban índices,
persistencia, recuperación de metadatos, búsquedas e integridad/rollback. No se deben
dirigir a producción. La selección habilita Flyway y el driver PostgreSQL reales
solo para esas clases. Los embeddings de pgvector son deterministas locales.

### AWS S3 Vectors

El operador debe disponer de un bucket/índice **de test**, ya creados, con coseno y
768 dimensiones, y credenciales mediante la cadena estándar del SDK, por ejemplo
`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` y, si procede, `AWS_SESSION_TOKEN`.
No se usa el bucket productivo como valor implícito ni se crea/modifica ningún recurso.

```bash
R4R_TEST_S3_BUCKET=nombre-del-bucket-de-test \
R4R_TEST_S3_INDEX=nombre-del-indice-de-test \
R4R_TEST_S3_REGION=eu-north-1 \
mvn verify -Paws-it
```

El nuevo smoke usa un vector fijo no nulo de 768 dimensiones, `topK=1` y
`returnMetadata=false`. Comprueba HTTP satisfactorio, coseno y el límite de resultados;
admite un índice vacío. No llama a Ollama ni valida respuestas RAG/citas reales.

### LLM y end-to-end

Definir `OPENAI_API_KEY` para Luna. Seleccionar una API y un Ollama de **test** mediante
`RAG_TEST_BASE_URL` y `RAG_EMBEDDING_LIVE_URL`; ya no se asume el endpoint productivo
del NAS. Los modelos deben estar instalados; la prueba no los descarga. Estas pruebas
pueden consumir llamadas facturables y varios minutos de ejecución.

```bash
RAG_TEST_BASE_URL=http://127.0.0.1:18080 \
RAG_EMBEDDING_LIVE_URL=http://127.0.0.1:11434 \
mvn verify -Pllm-it
```

Para una clase concreta, manteniendo los tests obligatorios:

```bash
mvn verify -Pllm-it -Dit.test=LunaLiveIT
RAG_TEST_BASE_URL=http://127.0.0.1:18080 \
mvn verify -Pllm-it -Dit.test=RagLiveEnglishQuestionsIT
```

Las dos fábricas dinámicas contienen 15 preguntas cada una. Las dos regresiones
adicionales de plazos mantienen su opt-in independiente `RAG_TIMELINE_REGRESSION=true`.

## Batería completa antes de desplegar

Con los requisitos anteriores y las variables de recursos/secretos ya definidos:

```bash
RAG_TIMELINE_REGRESSION=true mvn clean verify -Ppostgres-it,aws-it,llm-it
python3 py-scripts/test_compose_configuration.py
node --test front-athagon-r4r-static/js/app.test.cjs
```

El build offline no sustituye esa validación real. Un despliegue puede fallar por DNS,
credenciales, permisos, disponibilidad o calidad del modelo aunque las simulaciones
sean correctas. No se ejecutó esa batería externa durante esta reorganización.

## Auditoría y resultados de esta modificación

La traza aportada mostraba **20 errores** al ejecutar por defecto las cuatro clases
PostgreSQL reales con el puerto 5433 detenido. No eran fallos de los asserts de
failover. El helper anterior seleccionaba solo las tres IT offline; ese resultado
no demostraba que un `mvn verify` sin selección fuese independiente de infraestructura.

Se extrajeron de `PgVectorKnowledgeStoreIT` tres contratos puramente locales hacia
`PgVectorKnowledgeStoreTest`: determinismo/dimensiones del embedding, validación de
entradas y metadatos inválidos. Sus cuerpos y asserts se conservaron literalmente;
la nueva fixture comprueba además que no hubo interacción con JDBC ni VectorStore.
Los casos de identidad persistida, búsquedas y transacciones permanecen en integración.

Resultados medidos con Java 21, sin arrancar servicios:

| Ejecución | Resultado |
|---|---|
| `mvn compile` | PASS. |
| `mvn test` | PASS, 336 obligatorias, 0 fallos/errores/skips. |
| `mvn clean package` | PASS, 336 obligatorias, 0 fallos/errores/skips; JAR 0.3.3. |
| `mvn clean verify` | PASS, 336 obligatorias y 11 IT offline; 24 entradas reales SKIPPED. |
| `mvn test -Ppostgres-it,aws-it,llm-it` | PASS, las mismas 336 obligatorias; Failsafe no se ejecuta en la fase `test`. |

Las 24 entradas omitidas son 17 PostgreSQL, 1 AWS y 6 LLM, incluidas dos fábricas
dinámicas: no se cuentan sus preguntas como ejecutadas. Cada omisión aparece en XML
con el motivo y el perfil correspondiente. Surefire y Failsafe escriben en
`target/surefire-reports/` y `target/failsafe-reports/`.

La ejecución `clean verify` se auditó con `strace` para conexiones y aperturas de
ficheros: ningún destino de servicio externo y ninguna apertura del `.env` privado.
Solo se observaron conexiones locales, incluidas conexiones de Tomcat a interfaces
del propio PC. PostgreSQL estaba detenido según el estado comunicado por el operador;
el driver offline impide depender de su disponibilidad.

Se comprobó también la selección de los tres perfiles con ejecuciones directas de
Failsafe, sin credenciales ni endpoints reales. Cada perfil seleccionó su clase y
falló por el requisito ausente antes de contactar al servicio. Esas comprobaciones
de precondiciones **no son PASS de integración real**: PostgreSQL, AWS y LLM siguen
**NOT RUN**, pendientes de ejecutarse con infraestructura de test disponible.

El helper `python3 py-scripts/test_secret_isolation.py` ahora ejecuta `mvn verify`
sin filtros ni propiedades especiales de JDBC/Flyway, con credenciales ficticias y
flags heredados deliberadamente conflictivos. Comprueba ausencia de fugas y que los
tests reales continúan generando reportes NOT RUN. No reemplaza los comandos Maven.
Esta comprobación también pasó con todas las credenciales ficticias y los cuatro
flags legacy de pruebas reales activados en el proceso padre.

Los helpers `LiveFailoverExecution` y `LiveEvidenceSnapshot` no son tests JUnit ni
entran en la selección Surefire/Failsafe. Son herramientas manuales históricas y no
se ejecutaron. No se modificó código de producción, migraciones ni recursos reales.


## Lambda Titan e importador

El módulo independiente reutiliza las fuentes ligeras de metadatos/chunks del backend.
El `verify` raíz no ejecuta sus tests automáticamente; deben ejecutarse ambos gates
cuando cambie código compartido o antes de actualizar Lambda:

```bash
mvn clean verify
mvn -f lambda-titan/pom.xml clean verify
python3 -m unittest discover -s lambda-titan -p 'test_*.py'
python3 lambda-titan/corpus.py
python3 lambda-titan/evaluate.py
python3 py-scripts/test_secret_isolation.py
python3 py-scripts/test_compose_configuration.py
```

`CorpusImporterTest` verifica el export original de 282 filas, conservación de IDs/texto,
manifiesto, idempotencia/reanudación, metadatos, vectores, errores y destino exclusivo
Titan con SDK simulado. `RetrievalHandlerTest` cubre el contrato Lambda, Bedrock denegado,
timeouts y datos/citas. Python comprueba lanzadores, perfil/región, stop tras error y
reutilización de preguntas; ya no implementa validación/ingesta paralela.

`TitanLambdaReadOnlyIT` aparece como **NOT RUN/SKIPPED** salvo `-Ptitan-it` y requiere
un umbral explícito en `-Dr4r.test.titan.minimum-similarity`. Usa `r4r-codex` y
`eu-north-1`, solo consulta la función Titan existente. No escribir vectores ni invocar
la batería real mientras Bedrock siga bloqueado. La condición JUnit impide que una
variable de entorno heredada active estas llamadas en el build ordinario.

Las 30 preguntas en `TitanDegradedHttpIT` prueban contrato y flujo con respuestas
simuladas; **no certifican relevancia semántica, permisos ni latencias reales**.
La evaluación real y los comandos de activación, IAM y comprobación del corpus están
en [la guía Titan](operations/titan-lambda-retrieval.md).


## Regresión semántica Nomic y Caso G

`NomicRetrievalRegressionTest` añade tres consultas de cueva/bodega/mortero y reutiliza
las 30 preguntas ES/EN originales. Reproduce offline vectores Nomic reales capturados,
leyendo el corpus exportado existente y comprobando su checksum. Los mocks sustituyen
el transporte de PG/S3, no los vectores ni el cálculo de ranking. Se mantienen el
umbral de coseno y las comprobaciones de abstención/citas.

`NomicRetrievalDiagnostic` es una herramienta main de test, no un test automático:
requiere `--run`, fuerza PG en solo lectura, usa el perfil `r4r-codex` para S3 y no
arranca Boot/Flyway. `--generate-local` habilita explícitamente generación Ollama,
con OpenAI desactivado. No consulta Bedrock. Sus comandos, comparativas reales y
parámetros de ranking están en [el diagnóstico del Caso G](operations/nomic-cave-retrieval.md).

La comparación real ejecutada en esta revisión comprende las tres regresiones y
las 30 preguntas ES/EN, antes/después. Los resultados completos permanecen en runtime
ignorado; el informe documenta las cantidades y distingue estas mediciones de mocks.
