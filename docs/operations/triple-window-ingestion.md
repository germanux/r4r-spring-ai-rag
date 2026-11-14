# Ingesta original y Triple Window

Configuración final cerrada: `topK=5`, `minScore=0.49`, Qwen3 1024 dimensiones.
No se han repetido benchmarks ni las 70 consultas durante este cierre.

El flujo productivo de `KnowledgeIngestionCli --r4r.ingestion.index=true` ejecuta:

1. `KnowledgeIngestionService.ingestIndexed` confirma su transacción de originales.
2. En el mismo contexto Spring, una segunda transacción invoca el método reutilizado
   `ExperimentalTripleWindowIndexer.synchronize`.
3. Solo al terminar ambas se imprime el resultado satisfactorio. `vectors` cuenta
   originales; `tripleWindowVectors` y `tripleWindow` describen las ventanas finales.

No se ejecuta en el arranque normal. El antiguo flujo documental sin `index=true`
continúa siendo documental, sin generar embeddings. `check-only=true` conserva
sus comprobaciones y no escribe ni ejecuta la sincronización de ventanas.

Se conserva el algoritmo y la identidad `triple-even-v1`: centro par, vecinos del
mismo documento, ordinal desplazado y UUID determinista de fuente/centro/contenido.
Los reemplazos de originales conservan las ventanas hasta su sincronización.
Solo se embeben IDs ausentes; los cambios de encabezado actualizan metadatos sin
recalcular embeddings. Las ventanas obsoletas se eliminan selectivamente. Las
fuentes desaparecidas o excluidas eliminan sus filas en las tres tablas, sin exigir
un reemplazo completo del corpus.

La integridad de originales excluye únicamente `r4r_experiment=triple-even-v1`:
continúa comprobando cantidades, contenido y encabezados de originales. Las
ventanas se verifican aparte por IDs exactos, contenido, metadatos, dimensiones y
norma finita positiva. Las modificaciones de ventanas son atómicas. Si fallan, la
transacción original ya está confirmada: la CLI devuelve error y la siguiente
ejecución incremental puede repararlas sin reindexar fuentes originales intactas.

## Operación futura

Después del build, desde la raíz del repositorio y usando la configuración habitual:

```bash
java -Dloader.main=com.riansares.r4r.ingestion.KnowledgeIngestionCli \
  -cp target/r4r-spring-ai-rag-0.3.4.jar org.springframework.boot.loader.launch.PropertiesLauncher \
  --r4r.knowledge.root=knowledge --r4r.catalog-on-startup=false \
  --r4r.rag.warmup.on-startup=false --r4r.ingestion.index=true
```

Añadir `--r4r.ingestion.check-only=true` para preflight sin escrituras.
No hace falta ejecutar este comando para desplegar el corpus actual, ya validado.
No es necesario `replace-all=true` para las futuras ingestas incrementales.

## S3 Vectors

`PgVectorKnowledgeStore.exportS3Vectors()` sigue siendo el único exportador. Lee
los embeddings almacenados bajo una transacción de solo lectura/repeatable read,
exporta originales y ventanas e incluye `r4r_experiment` y `coveredOrdinals` en estas.
Conserva la expansión existente de fragmentos que solo contienen un encabezado.
No se regeneran embeddings para S3.

Se reutilizó sin cambios el bloque de importación de `lambda-s3-vectors/deploy.sh`,
en modo idempotente sin `--replace-index`. El índice existente terminó con 2.070
claves únicas y coincidencia exacta de cada float32 y metadato con el export. No se
recrearon bucket/índice ni se modificó IAM. Dos invocaciones reales de Lambda
recuperaron ventanas ES/EN por sus propios embeddings.

Para futuras sincronizaciones se mantienen los mecanismos documentados de export
`--r4r.ingestion.export-s3=<archivo-nuevo>` e importación
`bash lambda-s3-vectors/deploy.sh --vectors <archivo> --profile r4r-codex`.
Este último también construye/actualiza Lambda; no usa `--replace-index` por defecto.
La importación existente rechaza claves remotas obsoletas: tras eliminar documentos
hará falta su limpieza selectiva explícita antes de reutilizarla; no se automatizó
ninguna eliminación remota ni se amplió el alcance de este cierre.

## Evidencia de cierre

Ver [verificación compacta](../validation/mvp-closure-2026-11-14/verification.json).
Dos sincronizaciones reales: 695 omitidas, 0 añadidas/eliminadas, 0 embeddings nuevos;
los 2.070 vectores conservan la misma huella completa antes/después.
El build final ejecuta unitarias e integración offline; las integraciones reales
requieren sus perfiles explícitos. La prueba real AWS descrita arriba es adicional.
No se ha probado detener PostgreSQL en el NAS ni el failover de producción durante
esta reanudación. El despliegue final corresponde al usuario.
