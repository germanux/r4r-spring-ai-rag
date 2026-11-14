# Ingesta bilingüe en PostgreSQL/pgvector del NAS

El destino definitivo es PostgreSQL del Synology `192.168.1.203`, base `r4r_rag`.
Los comandos se ejecutan desde la raíz del repositorio en el PC de desarrollo,
con Java 21 y Maven. No necesitan PostgreSQL local ni arrancan Docker.
Spring importa `docker-spring/.env.base` y después `docker-spring/.env`;
no hay que copiar contraseñas a comandos ni imprimirlas.

> PostgreSQL del NAS ya está migrado a Qwen3/1024 (1.375 vectores).
> Estado de AWS y despliegue: [migración](../operations/qwen3-embedding-migration.md).

## Qué hace cada clase

```text
KnowledgeIngestionCli.execute()
  → KnowledgeIngestionService.ingestIndexed(checkOnly, replaceAll)
    → MarkdownDocumentLoader.loadBilingual()
    → HeadingMarkdownChunker.chunk()
    → comprobación JDBC del NAS, permisos, esquema y embedding de prueba
    → insertOrUpdateSource() + replaceChunks()
    → PgVectorKnowledgeStore.index()
      → identificadores deterministas y metadatos
      → VectorStore.add() de Spring AI
        → ObservedEmbeddingModel → EmbeddingProviderRouter → Qwen3
    → comprobación SQL de recuentos, contenido y metadatos
  → commit de la transacción y R4R_INGESTION_RESULT
```

`loadBilingual()` busca recursivamente documentos `R4R_<número>_*.md` en
`knowledge/es/` y `knowledge/en/`. Comprueba identificadores únicos, parejas y
el idioma del frontmatter. Excluye README, JSON, documentación ajena al corpus y
los documentos con `ingest_into_editorial_rag: false`. Los 82 Markdown del corpus
incluyen dos manifiestos R4R_70 excluidos expresamente: son 80 fuentes indexables.

El checksum documental incluye el archivo original completo. El chunker recibe
el cuerpo sin YAML, lo divide por encabezados y limita el tamaño configurado.
Conserva la ruta relativa con prefijo `es/` o `en/`, la jerarquía de encabezados y
el ordinal. Cada fragmento genera un vector: no se fusionan traducciones ni se
crean tablas separadas por idioma. El metadato `source` permite deducir el idioma.

Se utiliza el modelo `qwen3-embedding:0.6b`, su digest configurado, los 1024
componentes y el failover existente. El router comprueba identidad y digest antes
de cada petición, y valida dimensión, orden, valores finitos y vectores no nulos.
Esto usa Ollama para embeddings; las traducciones inglesas se realizaron
directamente con Codex, sin el script de traducción.

No se ha usado ningún script Python para generar ni guardar embeddings. Se han
usado comandos Python puntuales para comprobar el corpus y ejecutar `psql` con
las credenciales cargadas en variables de entorno, sin mostrarlas. La ingesta
real utiliza exclusivamente las clases Java y Spring AI indicadas arriba.

## 1. Compilar y validar

```bash
mvn clean verify
```

No activar `-Ppostgres-it`: esos tests históricos están diseñados para bases de
prueba y no deben apuntar al corpus del NAS. Los perfiles de integración con
PostgreSQL, AWS y LLM están desactivados por defecto. La suite offline conserva
sus vectores históricos en `docs/knowledge/data/struct-data/`; no son el índice
actual y no se importan durante esta ingesta.

## 2. Comprobación previa sin escrituras

```bash
java -Dloader.main=com.riansares.r4r.ingestion.KnowledgeIngestionCli \
  -cp target/r4r-spring-ai-rag-0.3.4.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher \
  --r4r.ingestion.index=true \
  --r4r.ingestion.check-only=true \
  --r4r.knowledge.root=knowledge \
  --spring.flyway.enabled=false \
  --r4r.catalog-on-startup=false \
  --r4r.rag.warmup.on-startup=false \
  --r4r.rag.vector.primary=pgvector \
  --r4r.rag.vector.failover-enabled=false \
  --r4r.rag.vector.s3vectors.enabled=false
```

El modo completo exige un JDBC dirigido a `192.168.1.203`; PostgreSQL puede
identificarse con una IP interna del contenedor del NAS, por ejemplo `172.20.0.5`.
Comprueba base/usuario, sesión escribible, permisos SELECT/INSERT/UPDATE/DELETE,
secuencias y columna `vector(1024)`. Hace una petición real de embedding de prueba,
pero no escribe filas. Flyway y la inicialización del esquema quedan desactivados;
se preservan todas las migraciones. No usar `--debug` ni volcar el entorno privado. La CLI limita por defecto la conexión
a 5 segundos y la espera de lectura JDBC a 30 segundos; una pérdida de red no
debe dejar el proceso bloqueado indefinidamente.

## 3. Ingesta completa

Ejecutar el mismo comando anterior sustituyendo únicamente:

```text
--r4r.ingestion.check-only=false
```

No basta con ejecutar la CLI antigua sin `--r4r.ingestion.index=true`: se conserva
por compatibilidad y solamente realiza persistencia documental.

El modo completo reemplaza los fragmentos y vectores de cada fuente presente,
incluso si el checksum no cambia. Esto repara una ingesta antigua que dejó fuentes
y fragmentos sin vectores. Reejecutar no duplica los documentos ni los vectores;
recalcula los embeddings y puede cambiar los identificadores SQL documentales.

La salida `R4R_INDEXED_SOURCE=...` indica progreso dentro de la transacción, no un
commit parcial. El JSON final `R4R_INGESTION_RESULT=...` se imprime después del
commit y contiene fuentes, fragmentos y vectores. El proceso devuelve 0 al terminar
correctamente y un código distinto de 0 si falla. El modo de comprobación devuelve
`checkOnly: true`, que no debe interpretarse como una ingesta realizada.

## Reindexar, retirar documentos y recuperarse de un fallo

No hay ningún `TRUNCATE` automático. Si hay fuentes antiguas o vectores de rutas
retiradas, el modo normal falla en lugar de dejar evidencia obsoleta en silencio.
Para sustituir deliberadamente el conjunto completo, después de revisar el destino
y tener copia de seguridad, añadir al comando de ingesta:

```text
--r4r.ingestion.replace-all=true
```

Esa bandera autoriza `DELETE FROM vector_store`, `DELETE FROM knowledge_chunks`
y `DELETE FROM knowledge_sources`, seguido de la reconstrucción en **la misma
transacción**. No cambia tablas, índices, migraciones, secuencias ni recursos AWS.
No se ha necesitado esa bandera en la ejecución inicial: el usuario había vaciado
las tres tablas tras hacer la copia de seguridad.

El proceso bloquea escrituras concurrentes en las tres tablas durante la ingesta.
Las lecturas ordinarias siguen viendo el estado previamente confirmado. Conviene
programarlo en una ventana de mantenimiento por el tiempo y los recursos del NAS.
Una excepción de embedding, persistencia o validación provoca rollback completo.
Para reintentar, corregir la causa y repetir el comando: no hay cachés parciales ni
pasos manuales que marcar como completados. Si se corta la conexión al confirmar,
consultar recuentos antes de repetir; el reintento es seguro, pero recalcula vectores.

## 4. Verificar en el NAS

En una sesión `psql` conectada al NAS con las credenciales habituales:

```sql
SELECT current_database(), current_user, host(inet_server_addr());

SELECT 'knowledge_sources' AS tabla, count(*) FROM knowledge_sources
UNION ALL SELECT 'knowledge_chunks', count(*) FROM knowledge_chunks
UNION ALL SELECT 'vector_store', count(*) FROM vector_store;

SELECT split_part(source_path, '/', 1) AS idioma, count(*)
FROM knowledge_sources GROUP BY 1 ORDER BY 1;

SELECT split_part(metadata->>'source', '/', 1) AS idioma,
       count(*), min(vector_dims(embedding)), max(vector_dims(embedding))
FROM vector_store GROUP BY 1 ORDER BY 1;

SELECT count(*) AS inconsistencias
FROM knowledge_chunks c JOIN knowledge_sources s ON s.id = c.source_id
FULL JOIN vector_store v ON v.metadata->>'source' = s.source_path
    AND (v.metadata->>'ordinal')::integer = c.ordinal
WHERE c.id IS NULL OR v.id IS NULL OR c.content IS DISTINCT FROM v.content
   OR to_jsonb(c.heading_path) IS DISTINCT FROM v.metadata->'headingPath';
```

El resultado esperado es 40 fuentes por idioma, vectores de 768 componentes y
cero inconsistencias. Los fragmentos ES y EN no tienen por qué sumar lo mismo:
la traducción puede superar un límite de tamaño distinto. En esta implementación
el total de fragmentos persistidos sí debe coincidir con el de vectores.

## S3 Vectors

No se ha sincronizado, exportado ni modificado AWS. El índice S3 anterior no debe
considerarse actualizado automáticamente. Su sincronización es una operación
posterior separada; esta ejecución fuerza pgvector y desactiva el respaldo S3.

## Validación de esta intervención — 13 de noviembre de 2026

- Corpus comprobado: 41 ES + 41 EN, mismos identificadores, niveles de encabezado,
  valores numéricos e idioma del YAML. Las traducciones proceden de los commits
  `ad8ab4f` (33 nuevas) y `61cfccd` (correcciones de las 8 previas).
- Comprobación previa real: base `r4r_rag`, usuario `r4r`, pgvector `0.8.6`, las
  tres tablas vacías, permisos y embedding Nomic compatibles. Se calcularon 80
  documentos indexables y 1.401 fragmentos.
- La primera ingesta perdió acceso al NAS después de 61 fuentes procesadas.
  Una nueva conexión devolvió «No route to host». Se detuvo la JVM y, al recuperarse
  el NAS, se verificó que las tres tablas seguían a cero: no se publicó el trabajo
  parcial. Se añadieron límites de conexión/lectura JDBC y se inició el reintento.
- `mvn clean verify`: BUILD SUCCESS; 433 pruebas unitarias sin fallos; 103 casos
  de Failsafe, 27 omitidos por perfiles desactivados y ninguno fallido. No se
  arrancaron contenedores PostgreSQL locales. La primera verificación detectó
  una referencia antigua al export histórico; se corrigió la ruta de la fixture
  sin cambiar sus datos ni los resultados esperados.
- Se conserva una inconsistencia del original español R4R_10: la corrección de
  350 € acompaña totales cuya diferencia es 360 €. No se inventó un importe nuevo.

### Resultado confirmado del reintento

La CLI terminó con código 0 y `success: true`. Una conexión `psql` independiente,
con transacción de solo lectura, confirmó después del commit:

| Dato | Resultado |
|---|---:|
| Fuentes totales | 80 |
| Fuentes ES / EN | 40 / 40 |
| Fragmentos persistidos | 1.401 |
| Vectores persistidos | 1.401 |
| Vectores ES / EN | 700 / 701 |
| Dimensión mínima / máxima | 768 / 768 |
| Inconsistencias contenido / encabezados / ordinales | 0 |
| Checksums documentales que coinciden con el corpus local | 80 de 80 |

El proceso completo del reintento duró aproximadamente 131.5 segundos.
Hubo 81 respuestas correctas de embeddings, incluida la prueba de
compatibilidad previa, con una media de 470 ms en el proveedor
que respondió. El primer proveedor configurado no estuvo disponible y se usó el
respaldo existente; no se cambió la configuración privada ni el modelo. Estas
latencias son de ingesta, no un benchmark de respuestas RAG.

No se usó `replace-all`, no se ejecutó `TRUNCATE` ni se modificó el esquema. No se
realizó sincronización con S3 ni validación de generación de respuestas en vivo.
