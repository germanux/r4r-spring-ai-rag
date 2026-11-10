# Nomic: diagnóstico y corrección de recuperación del Caso G

## Evidencia real, 10 de noviembre de 2026

Partida: rama `feature/aws-bedrock-embed-titan`, HEAD `6f98e66`, árbol limpio.
El gate inicial `mvn clean verify` pasó antes de editar.

Se leyeron PostgreSQL y S3/Nomic y se utilizaron embeddings Nomic reales mediante
`EmbeddingProviderRouter` / `ObservedEmbeddingModel` de Spring AI. La sesión PG se
abrió con `default_transaction_read_only=on` y se comprobó ese estado. No se inició
la aplicación Boot, Flyway ni procesos de ingesta. No se cambió ningún índice,
registro, política IAM, recurso AWS ni configuración/despliegue Synology.

### Presencia e integridad

- PostgreSQL contiene 282 filas en `vector_store`.
- Se recuperaron por GetVectors los 282 IDs del export en el índice S3 original
  `amzn-r4r-vectors/amzn-r4r-vector-index`, con perfil `r4r-codex`, región `eu-north-1`.
- Los 282 IDs y vectores PG/S3 coinciden: máxima diferencia de componentes **0.0**.
- Están presentes los **nueve fragmentos del Caso G**, ordinales 10–18 de
  `R4R_20_CASOS_OFICINAS_CUEVAS_BANOS_Y_FILTROS.md`.
- Los fragmentos 10, 11 y 12 tienen respectivamente IDs
  `f4190d1f-a66b-5cd0-96af-02a11cbee1ca`,
  `2b57fda6-8cec-525f-999e-8dd58afa624e`,
  `8c41501d-152e-5cf1-a1d1-6270002e7542`.
- Re-embebidos tres contenidos PG originales con el modelo/digest configurado,
  su similitud con el vector almacenado fue 0.99999653, 0.99999694 y 0.99999595.
  No hay evidencia de un índice generado con otro modelo. Esa pequeña diferencia
  numérica no explica los puestos 19/6 observados.

El embedding del título del Caso G corresponde a su título original de 81 caracteres.
Su texto recuperable se amplía a 3027 caracteres mediante la expansión de sección
PG existente; S3 ya conserva ese mismo contexto en `source_text` con el marcador
`pgvector-heading-v1`. El diagnóstico distingue lo embebido de lo entregado al LLM.
No se vuelven a fragmentar documentos ni se alteran IDs.

### Ranking y filtro anteriores

Configuración efectiva: `topK=5`, `minScore=0.63`, comparación estricta de coseno
`score > 0.63`. Los siguientes valores PG se calcularon sobre los vectores reales;
se contrastaron además con QueryVectors real de S3 (su búsqueda aproximada introduce
pequeñas diferencias numéricas, sin resolver la exclusión por topK).

| Consulta | Primer Caso G: puesto PG / score | Puesto S3 / score | Fragmentos que pasan 0.63, antes de limitar a cinco |
|---|---|---|---:|
| ¿Con qué reformaríais una cueva? | 19 / 0.639080 | 19 / 0.638825 | 26 |
| ¿Con qué reformaríais una bodega? | 6 / 0.667478 | 6 / 0.667754 | 25 |
| ¿Qué mortero utilizaríais para rehabilitar una cueva antigua? | 3 / 0.718790 | 3 / 0.718712 | 27 |

Para «cueva», el primer resultado era `R4R_00...`, ordinal 6 («Reformas»), score
0.720862. Para «bodega», el mismo fragmento daba 0.732096. El primer resultado del
caso sí superaba el umbral: **se perdía por truncar candidatos a cinco antes de
considerar coincidencias textuales y de sección**. La pregunta explícita sobre
mortero ya incluía el caso entre sus cinco resultados, aunque otros documentos
quedaban por delante.

El problema no era ausencia del documento, desincronización PG/S3 ni un error del
filtro de similitud. Aumentar/bajar el umbral global no corrige esa pérdida de ranking.

### Comparaciones descartadas y elección

El texto enviado al embedding se mantiene exactamente como la consulta recibida
tras la normalización/traducción existente de R4R. No se añaden sinónimos, nombres
de documentos, materiales ni instrucciones específicas para cuevas.

Se comparó `search_query:` en las tres consultas. Para «cueva», el primer Caso G
bajó de 19 a **22**; para «bodega» quedó en 6 y para «mortero» en 3. No se adoptó.
El [model card de Nomic](https://huggingface.co/nomic-ai/nomic-embed-text-v1.5)
explica los prefijos de consulta/documento coordinados; cambiar solo las consultas
no constituye una migración validada del índice existente.

Se compararon pools de 10/20/30/50 y ranking léxico BM25, fusión por posiciones y
un apoyo BM25 normalizado acotado sobre el coseno original. Diez candidatos no
alcanzan el título del Caso G para «cueva»; veinte sí. Un reranking léxico completo
cambia innecesariamente muchos resultados. La opción elegida conserva un peso
semántico dominante:

`orden = coseno_original + lexicalWeight * BM25 / maxBM25`

BM25 usa k1=1.2 y b=0.75, tokens Unicode normalizados y el contenido/headingPath
existentes; no tiene diccionarios de sinónimos, reglas por consulta, boosts por
fuente ni nombres de casos/documentos en producción. El score combinado sirve
solo para ordenar, **nunca para superar el umbral de admisión**.

| Peso léxico comparado, 20 candidatos | Caso G en los cinco finales: cueva / bodega / mortero | Se conserva el mejor resultado semántico anterior en los 33 casos |
|---|---|---|
| 0.030 | No / Sí / Sí | Sí |
| 0.050 | No / Sí / Sí | Sí |
| **0.075** | **Sí / Sí / Sí** | **Sí** |
| 0.100 | Sí / Sí / Sí | No, cambia la cobertura en dos casos de plazos |
| 0.150 | Sí / Sí / Sí | No, cambia la cobertura en cuatro casos |

Es evidencia comparativa sobre esta batería, no una garantía para cualquier corpus
futuro. Los parámetros son configurables y no se ha cambiado `RAG_MIN_SCORE`.

## Cambio implementado

`NomicCandidateReranker` es una única implementación compartida por
`PgVectorKnowledgeStore` y `S3VectorsKnowledgeStore`. Ambos solicitan hasta cuatro
candidatos por resultado deseado, conservan el filtro de coseno y finalmente
entregan como máximo el `topK` original. Se reutiliza la expansión PG de títulos.
S3 sigue siendo de solo lectura y mantiene embedding único/paginación/validación.

Con los defaults, se examinan veinte candidatos y se entregan cinco. Si hay menos
de cinco válidos, no se amplía el conjunto ni se reordena; si no hay coincidencia
léxica, se conserva el orden semántico. Los empates son estables. Las respuestas
vacías y los errores conservan su significado para el router de failover.

```properties
RAG_TOP_K=5
RAG_MIN_SCORE=0.63
RAG_RETRIEVAL_CANDIDATE_MULTIPLIER=4
RAG_RETRIEVAL_LEXICAL_WEIGHT=0.075
```

Los defaults están en `application.yml`, `.env.base` y Compose autocontenido.
Para recuperar explícitamente el ranking anterior, sin reindexación ni despliegue
en esta ejecución:

```properties
RAG_RETRIEVAL_CANDIDATE_MULTIPLIER=1
RAG_RETRIEVAL_LEXICAL_WEIGHT=0
```

No se cambió la configuración activa del NAS. El cambio necesita el despliegue
posterior autorizado del backend para afectar a la API que ya está desplegada.

## Contraste real de los adaptadores y generación

Con las clases corregidas, contra PG/S3 reales en lectura, los puestos finales del
Caso G fueron:

| Consulta | PG y S3 después | Generación Ollama local después |
|---|---|---|
| Cueva | título del caso en 5 | Cal hidráulica natural NHL 2/3.5 y preparación, con cita del Caso G `[S5]`. |
| Bodega | contexto en 1 y título en 3 | Mortero de cal y malla galvanizada, conservando el objetivo y límites del caso. |
| Mortero/cueva antigua | título en 2 y materiales en 5 | NHL 2/3.5, compatible con el soporte antiguo, con citas. |

Antes, «cueva» y «bodega» obtenían recomendaciones genéricas de reformas. Después
se cita el documento específico. Se ejecutaron seis respuestas locales antes/después
para estas tres preguntas. Son respuestas reales del `GenerationRouter` y
`CitedRagService` existentes, con OpenAI explícitamente desactivado, sin Bedrock.
El embedding real de cada consulta se reutiliza en los cuatro recorridos PG/S3 y
antes/después; no se sustituye por un vector simulado.

La batería ES/EN original se ejecutó asimismo con la herramienta descrita abajo:
**30 consultas y 60 respuestas locales** antes/después. Antes: 23 respuestas y 7
abstenciones. Después: **24 respuestas del dominio con citas y 6 abstenciones fuera
del dominio**, sin incumplir ninguna expectativa de la batería en esta ejecución.
Una pregunta inglesa sobre solera/solado que antes se abstenía ahora responde.
Se verificó que todas las citas correspondían a fuente, headingPath y ordinal de
los fragmentos seleccionados. PG y S3 devolvieron exactamente los mismos fragmentos
y orden en los **33 casos reales después del cambio**. La generación se ejecutó
sobre los resultados PG; la igualdad de los resultados S3 se comprobó por datos,
no repitiendo innecesariamente las mismas llamadas LLM.

Son comparaciones reales de una ejecución; no certifican cualquier paráfrasis ni
el comportamiento de versiones futuras del modelo. Las pruebas de fallo y SSE
siguen cubiertas de forma aislada por la batería offline.

## Regresión offline y reproducción

`NomicRetrievalRegressionTest` reproduce las tres consultas y las **30 preguntas
originales ES/EN**, sin red. Los vectores de consulta fueron capturados realmente
mediante Spring AI/Nomic el 10-11-2026 y están en
`src/test/resources/retrieval/nomic-query-vectors.json`, con modelo, digest y SHA-256
del corpus. Se leen los vectores/documentos del export existente; no se duplica el
corpus ni se inventan embeddings para hacer pasar las pruebas.

Las regresiones verifican los puestos anteriores 19/6/3, la recuperación nueva del
Caso G en ambos adaptadores, límite final, petición de veinte candidatos, umbral
original, embedding único y conservación de fuentes/contenido/ordinales. Los 30
casos conservan su mejor evidencia anterior y no admiten candidatos bajo el umbral.
Las abstenciones también se verifican con el contrato existente y generación mock:
ese test aislado no demuestra por sí mismo calidad de un LLM real.

`NomicRetrievalDiagnostic` es un main de herramientas en fuentes de test, excluido
de Surefire/Failsafe por su nombre. Reutiliza las clases reales y exige `--run`;
sin esa opción no carga credenciales ni consulta servicios. No arranca el backend,
Flyway ni ingestas. La conexión PG es forzosamente de solo lectura; el cliente S3
usa perfil explícito `r4r-codex` y región `eu-north-1`. Solo permite el índice Nomic
esperado. Carga configuración con el mecanismo nativo Spring y no interpreta ni
imprime manualmente archivos de credenciales.

```bash
mvn clean verify
mvn test-compile dependency:build-classpath \
  -Dmdep.outputFile=target/nomic-diagnostic-classpath.txt
java -cp "target/classes:target/test-classes:$(cat target/nomic-diagnostic-classpath.txt)" \
  com.riansares.r4r.vector.NomicRetrievalDiagnostic
# Lecturas y embeddings reales de las tres regresiones, sin generación:
java -cp "target/classes:target/test-classes:$(cat target/nomic-diagnostic-classpath.txt)" \
  com.riansares.r4r.vector.NomicRetrievalDiagnostic --run \
  --output target/nomic-three-queries.json
```

Para la batería original y respuestas locales, optativas:

```bash
python3 - <<'PY'
import json, sys
from pathlib import Path
sys.path.insert(0, 'py-scripts')
from r4r_evaluation_cases import load_cases
Path('target/nomic-questions.json').write_text(json.dumps(load_cases(), ensure_ascii=False))
PY
java -cp "target/classes:target/test-classes:$(cat target/nomic-diagnostic-classpath.txt)" \
  com.riansares.r4r.vector.NomicRetrievalDiagnostic --run --generate-local \
  --questions target/nomic-questions.json --output target/nomic-es-en-real.json
```

El informe conserva consultas, input del embedding, dimensión, resultados antes y
después y, si se solicita, respuestas/citas. Los resultados completos quedan en
almacenamiento de runtime ignorado; no imprimirlos en sistemas públicos.

## Revisión Titan en esta tarea

No se repite implementación ni ingesta. Se comprobaron importador, embedding,
adaptador, router, configuración y pruebas:

- Titan sigue desactivado por defecto; no se ha sondeado Bedrock.
- Las escrituras siguen exclusivamente en PG, también con Titan habilitado.
- La nueva reordenación solo pertenece a los adaptadores Nomic de 768 dimensiones.
  Lambda/Titan conserva su modelo/índice de 1024 y umbral independiente.
- Se corrigió una validación permisiva: `asInt()` aceptaba dimensiones expresadas
  como `"1024"` o `1024.5`. Ahora se exige un entero JSON exacto igual a 1024.
- También se exige que el campo de fuente devuelto sea textual y coincida con los
  metadatos. Se mantienen validación de citas, cancelación, timeouts y errores.
- Las clases compartidas de metadatos, corpus, embedding Titan e importador no se
  duplican ni se modifican. Lambda no requiere actualización de código por esta tarea.

La validación real de Titan sigue pendiente de AWS Support; no se ha presentado
como aprobada ni se ha intentado evitar el bloqueo mediante otros endpoints.
